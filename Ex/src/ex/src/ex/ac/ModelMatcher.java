// $Id: ModelMatcher.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * @author Martin Labsky labsky@vse.cz
 */

import java.io.*;
import java.util.*;
import uep.util.*;

import ex.model.*;
import ex.reader.*;
import ex.train.KB;
import ex.train.NgramInfo;
import ex.train.PhraseBook;
import ex.features.*;
import ex.util.search.Path;
import ex.parser.*;

public class ModelMatcher {
    private Logger log;
    private static FM fm; // feature manager

    public static final int USE_ATTR_PATTERNS=1;
    public static final int USE_INST_PATTERNS=2;
    public static final int USE_ALL_PATTERNS = USE_ATTR_PATTERNS | USE_INST_PATTERNS;
    
    public static void main(String[] argv) throws Exception {
        String cfg="config.cfg";
        Options o=Options.getOptionsInstance();
        try {
            o.load(new FileInputStream(cfg));
        }catch(Exception ex) { 
            Logger.LOG(Logger.WRN,"Cannot find "+cfg+": "+ex.getMessage());
        }
        Logger.init("mm.log", -1, -1, null);
        Logger lg=Logger.getLogger("Parser");

        // cfg
        int nbest=o.getIntDefault("parser_nbest",10);
        int nbestShow=o.getIntDefault("parser_nbest_show",5);

        // init empty KB
        KB modelKb=new KB("ModelKB",1000,5000);
        fm=FM.getFMInstance();
        fm.registerKB(modelKb);

        Model m=null;
        Document d=null;

        // open model
        ModelReader mr=new ModelReader();
        String modelFile=argv[0];
        try {
            m=mr.read(modelFile, modelKb);
        }catch(org.xml.sax.SAXException sex) {
            System.err.println("Error XML parsing model "+modelFile+": "+sex.getMessage());
        }catch(ModelException mex) {
            System.err.println("Error reading model "+modelFile+": "+mex.getMessage());
            mex.printStackTrace();
        }catch(java.io.IOException iex) {
            System.err.println("Cannot open model "+modelFile+": "+iex.getMessage());
        }
        if(m==null) {
            fm.deinit();
            return;
        }

        // open doc
        DocumentReader dr=new DocumentReader();
        String docFile=argv[1];
        CacheItem citem=dr.cacheItemFromFile(docFile);
        if(citem==null) {
            lg.LG(Logger.ERR,"Cannot open "+docFile);
            fm.deinit();
            return;
        }
        d=dr.parseHtml(citem);
        d.setTokenFeatures(modelKb.vocab);

        // test KB saving
        // modelKb.save("model_kb.ser");

        // match all attribute value and context patterns, mark PatMatches in document.tokens
        ModelMatcher mm=new ModelMatcher();
        int patMatchCnt=mm.matchModelPatterns(m, d, USE_ATTR_PATTERNS, TokenPattern.PATTERN_WITHOUT_ACS, null);
        if(lg.IFLG(Logger.INF)) lg.LG(Logger.INF,"Model '"+m.name+"' attr pattern matches: "+patMatchCnt);

        // estimate ACs with their cond. probabilities based on PatMatches found
        ACFinder af=new ACFinder();
        int acCnt=af.findScoreACs(d, mm, m, TokenPattern.PATTERN_WITHOUT_ACS);
        if(lg.IFLG(Logger.INF)) lg.LG(Logger.INF,"Model '"+m.name+"' ACs: "+acCnt);
        
        int patMatchCntAC=mm.matchModelPatterns(m, d, USE_ATTR_PATTERNS, TokenPattern.PATTERN_WITH_ACS, null);
        if(lg.IFLG(Logger.INF)) lg.LG(Logger.INF,"Model '"+m.name+"' attr pattern with AC matches: "+patMatchCntAC);

        int acCntAC=af.findScoreACs(d, mm, m, TokenPattern.PATTERN_WITH_ACS);
        if(lg.IFLG(Logger.INF)) lg.LG(Logger.INF,"Model '"+m.name+"' ACs based on other ACs: "+acCntAC);
        
        int patMatchCnt2=mm.matchModelPatterns(m, d, USE_INST_PATTERNS, TokenPattern.PATTERN_ALL, null);
        if(lg.IFLG(Logger.INF)) lg.LG(Logger.INF,"Model '"+m.name+"' inst pattern matches: "+patMatchCnt2);
        
        // parse instance candidates from
        Parser prs=Parser.getParser(Parser.PS_LR, m);
        ArrayList<Path> paths=new ArrayList<Path>(nbest);
        prs.setMaxICs(5000); // max 5000 IC candidates to be generated
        prs.setMaxParseTime(2*60*1000); // max. 2 minutes
        int icCnt=prs.parse(d, nbest, paths);
        if(lg.IFLG(Logger.INF)) lg.LG(Logger.INF,"Instances for '"+m.name+"' on best path: "+icCnt);

        nbestShow=Math.min(nbestShow, paths.size());
        String[] instanceTables=new String[nbestShow];
        instanceTables[0]="<div>No instances found.</div>";
        for(int i=0;i<instanceTables.length;i++)
            instanceTables[i]=Parser.pathToTable(paths.get(i));

        // annotate found patterns in document's source
        boolean live=true;
        // don't show patterns, show atts, use live labels
        String labeled=d.getAnnotatedDocument(false,true,"utf-8",live,null);
        if(lg.IFLG(Logger.INF)) lg.LG(Logger.INF,"Annotated "+d.labelCnt+" patterns/attributes.");
        if(lg.IFLG(Logger.TRC)) lg.LG(Logger.TRC,"Document:\n"+labeled);
        // output in utf-8
        PrintStream ps = new PrintStream(System.out, true, "utf-8");
        String acTable=d.getACTable();
        ps.print(acTable);
        ps.print("\n");
        for(int i=0;i<instanceTables.length;i++) {
            ps.print(instanceTables[i]);
            ps.print("\n");
        }
        ps.print(labeled);
        ps.close();

        // disconnect from lemmatizer etc.
        fm.deinit();
    }

    public ModelMatcher() {
        log=Logger.getLogger("ac");
        fm=FM.getFMInstance();
    }

    public int matchModelPatterns(Model m, Document d, int mode, int patFilter, List<PatMatch> matches) {
        // find model-defined patterns in doc
        int mc=0;
        for(int ci=0;ci<m.classArray.length;ci++) {
            ClassDef cd=m.classArray[ci];
            int cc=0;
            if((mode & USE_INST_PATTERNS)!=0) {
                cc+=matchClassPatterns(cd, d, patFilter, matches);
            }
            if((mode & USE_ATTR_PATTERNS)!=0) {
                for(int ai=0;ai<cd.attArray.length;ai++) {
                    AttributeDef ad=cd.attArray[ai];
                    int ac=matchAttributePatterns(ad, d, patFilter, matches);
                    cc+=ac;
                }
            }
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Class '"+cd.name+"' matches: "+cc);
            mc+=cc;
        }
        return mc;
    }
    
    private int matchClassPatterns(ClassDef cd, Document d, int patFilter, List<PatMatch> matches) {
        // obtain pattern matcher
        PatMatcher matcher=PatMatcher.getMatcher();
        int pi=0, ic=0;
        for(pi=0;pi<cd.ctxPatterns.size();pi++) {
            TokenPattern tp=cd.ctxPatterns.get(pi);
            if(!TokenPattern.usePattern(tp, patFilter))
                continue;
            for(int pos=0;pos<d.tokens.length;pos++) {
                int pc=matcher.match(d.tokens, pos, tp); // find matches
                if(pc<=0)
                    continue;
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Class pattern '"+tp+"' matched "+pc+"x at "+pos+" ("+d.tokens[pos]+")");
                // create PatMatches, add them to TokenAnnot and to matches
                matcher.applyMatches(d.tokens, pos, tp, matches);
                ic+=pc;
            }
        }
        // recycle matcher
        PatMatcher.disposeMatcher(matcher);
        return ic;
    }

    private int matchAttributePatterns(AttributeDef ad, Document d, int patFilter, List<PatMatch> matches) {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"\nAttribute "+ad+"\n");
        // obtain pattern matcher
        PatMatcher matcher=PatMatcher.getMatcher();
        int pi=0, ac=0;
        // value patterns
        for(pi=0;pi<ad.valPatterns.length;pi++) {
            TokenPattern tp=ad.valPatterns[pi];
            if(!TokenPattern.usePattern(tp, patFilter))
                continue;
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"\nValue pattern "+tp.toString()+"\n");
            for(int pos=0;pos<d.tokens.length;pos++) {
                int pc=matcher.match(d.tokens, pos, tp); // find matches
                if(pc<=0)
                    continue;
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Value pattern '"+tp.toString()+"' matched "+pc+"x at "+pos+" ("+d.tokens[pos]+")");
                // create PatMatches, add them to TokenAnnot and to matches
                matcher.applyMatches(d.tokens, pos, tp, matches);
                ac+=pc;
            }
        }
        // context patterns
        for(pi=0;pi<ad.ctxPatterns.length;pi++) {
            TokenPattern tp=ad.ctxPatterns[pi];
            if(!TokenPattern.usePattern(tp, patFilter))
                continue;
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"\nContext pattern "+tp.toString()+"\n");
            for(int pos=0;pos<d.tokens.length;pos++) {
                int pc=matcher.match(d.tokens, pos, tp);
                if(pc<=0)
                    continue;
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Context pattern '"+tp.toString()+"' matched "+pc+"x at "+pos+" ("+d.tokens[pos]+")");
                matcher.applyMatches(d.tokens, pos, tp, matches);
                ac+=pc;
            }
        }
        if(log.IFLG(Logger.INF)) log.LG(((ac>0)?Logger.INF:Logger.TRC),"Attribute '"+ad.name+"' matches: "+ac);
        // recycle matcher
        PatMatcher.disposeMatcher(matcher);
        return ac;
    }
    
    /** Adds counts of all relevant ngrams of size 1..maxLen for the given document to book. 
     * @param roteLearning */
    public int collectNgrams(Document doc, PhraseBook book, ClassifierDef csd) {
        int cnt=0;
        NgramInfo inf=new NgramInfo();
        int maxLen=csd.getMaxNgramSize();
        boolean roteLearning=csd.getRoteLearning();
        for(int pos=0;pos<doc.tokens.length;pos++) {
            int ml=Math.min(maxLen, doc.tokens.length-pos);
            for(int len=1;len<=ml;len++) {
                collectNgram(doc, inf, pos, len, csd);
                cnt++;
                // record in trie
                book.put(doc.tokens, pos, len, inf, false);
            }
            if(roteLearning) {
                TokenAnnot stok=doc.tokens[pos];
                if(stok.semAnnots!=null) {
                    for(SemAnnot lab: stok.semAnnots) {
                        ModelElement elem=lab.getModelElement();
                        if(elem!=null && csd.supportsFeaturesOf(elem)) {
                            if(lab.getLength()>maxLen) {
                                // rote-learn EQUALS ngram even though it is too long
                                inf.clear();
                                inf.addOccCnt(1);
                                inf.setCount(elem, TokenNgramF.NGR_EQUALS, 1);
                                book.put(doc.tokens, pos, lab.getLength(), inf, false);
                            }
                        }
                    }
                }
            }
        }
        return cnt;
    }
    
    /** Collects counts for a specific n-gram appearing in particular positions wrt particular 
     * attributes. */
    protected void collectNgram(Document doc, NgramInfo inf, int pos, int len, ClassifierDef csd) {
        inf.clear();
        inf.addOccCnt(1);
        int ngrEnd=pos+len-1;
        
        // Positions:
        // "NGR_BEFORE","NGR_AFTER","NGR_EQUALS","NGR_PREFIX","NGR_SUFFIX","NGR_CONTAINED"

        // BEFORE (ngram precedes attribute)
        if(ngrEnd+1<doc.tokens.length) {
            TokenAnnot aft=doc.tokens[ngrEnd+1];
            if(aft.semAnnots!=null) {
                for(SemAnnot lab: aft.semAnnots) {
                    ModelElement elem=lab.getModelElement();
                    if(elem!=null && csd.supportsFeaturesOf(elem)) {
                        inf.setCount(elem, TokenNgramF.NGR_BEFORE, 1);
                    }else {
                        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"ngrams: ignored SemAnnot data="+lab.data);
                    }
                }
            }
        }

        // AFTER (ngram follows attribute)      
        if(pos>0) {
            TokenAnnot bef=doc.tokens[pos-1];
            if(bef.semAnnotPtrs!=null) {
                for(SemAnnot lab: bef.semAnnotPtrs) {
                    ModelElement elem=lab.getModelElement();
                    if(lab.endIdx==pos-1) {
                        if(elem!=null && csd.supportsFeaturesOf(elem)) {
                            inf.setCount(elem, TokenNgramF.NGR_AFTER, 1);
                        }else {
                            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"ngrams: ignored SemAnnot data="+lab.data);
                        }
                    }
                }
            }
        }
        
        // EQUALS, 
        // CONTAINED, PREFIX, SUFFIX,
        // CONTAINS, BEGINS_WITH, ENDS_WITH, 
        // OVERLAPS_LEFT, OVERLAPS_RIGHT
        for(int i=0;i<len;i++) {
            TokenAnnot ta=doc.tokens[pos+i];
            if(ta.semAnnotPtrs==null)
                continue;
            for(SemAnnot lab: ta.semAnnotPtrs) {
                ModelElement elem=lab.getModelElement();
                if(elem==null || !csd.supportsFeaturesOf(elem)) {
                    if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"ngrams: ignored SemAnnot data="+lab.data);
                    continue;
                }
                if(lab.startIdx==pos && lab.endIdx==ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_EQUALS, 1);
                }else if(lab.startIdx==pos && lab.endIdx>ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_PREFIX, 1);
                }else if(lab.startIdx<pos && lab.endIdx==ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_SUFFIX, 1);
                }else if(lab.startIdx<pos && lab.endIdx>ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_CONTAINED, 1);
                }else if(lab.startIdx==pos && lab.endIdx<ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_BEGINS_WITH, 1);
                }else if(lab.startIdx>pos && lab.endIdx==ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_ENDS_WITH, 1);
                }else if(lab.startIdx>pos && lab.endIdx<ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_CONTAINS, 1);
                }else if(lab.startIdx<pos && lab.endIdx<ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_OVERLAPS_LEFT, 1);
                }else if(lab.startIdx>pos && lab.endIdx>ngrEnd) {
                    inf.setCount(elem, TokenNgramF.NGR_OVERLAPS_RIGHT, 1);
                }else {
                    log.LG(Logger.ERR,"ngrams: invalid position: ngram["+pos+","+ngrEnd+"] vs. att["+lab.startIdx+","+lab.endIdx+"]");
                }
            }
        }
        
        if(inf.isEmpty()) {
            inf.setCount(null, TokenNgramF.NGR_EQUALS, 1);
        }else {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC, "set ngr: "+Document.toString(doc.tokens, pos, len, " ")+" = "+inf);
        }
    }
}

// $Id: EvaluatorImpl.java 1934 2009-04-12 09:16:14Z labsky $
package medieq.iet.components;

import java.util.*;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import uep.util.Logger;
import uep.util.Options;
import uep.util.Util;
import medieq.iet.model.*;
import medieq.iet.generic.*;

public class EvaluatorImpl implements Evaluator, Configurable {
    public static final char EVAL_PREC=1;
    public static final char EVAL_RECALL=2;
    protected static String[] evalTypes={"precision","recall"}; 

    public static final int INCLUDE=1;
    public static final int EXCLUDE=2;
    
    // 2 parameters to set: occurrence/presence and precise/loose:
    public static final String parEvalMode="eval_mode";
    public static final int MODE_OCCURRENCE=1;
    public static final int MODE_PRESENCE=2;
    public static final int MODE_EXACT=4;
    public static final int MODE_LOOSE=8;
    public static final String[] MODES_STR={"occurrence","presence","exact","loose"};
    
    public static final String parHomoSpec="homo_spec";
    
    List<Annotation> goldAnnots;
    List<Annotation> autoAnnots;
    
    List<MatchedAnnotation> exactAnnots;
    List<MatchedAnnotation> innerAnnots;
    List<MatchedAnnotation> crossAnnots;
    
    // configuration
    Properties params;
    int homoSpec;
    AnnotationFilter filter;
    
    // helpers
    DocumentReader dr; // reader used to fetch unloaded docs
    DataModel model; // model used to read the doc's annotations
    DocumentSet goldDocSet;
    DocumentSet autoDocSet;
    String docId; // used to track errors
    
    EvalResult workResult;
    Logger log;
    
    static AnnotComparator acmpr=new AnnotComparator();
    static MatchedAnnotComparator macmpr=new MatchedAnnotComparator();
    static AttributeDef bg=new AttributeDefImpl("bg","bg");
    
    public EvaluatorImpl() {
        goldAnnots=new ArrayList<Annotation>(32);
        autoAnnots=new ArrayList<Annotation>(32);
        exactAnnots=new ArrayList<MatchedAnnotation>(8);
        innerAnnots=new ArrayList<MatchedAnnotation>(8);
        crossAnnots=new ArrayList<MatchedAnnotation>(8);
        dr=null;
        model=null;
        goldDocSet=autoDocSet=null;
        workResult=new EvalResult("work");
        params=new Properties();
        homoSpec=0;
        filter=null;
        Options o=Options.getOptionsInstance();
        setEvalMode(o.getProperty(parEvalMode, MODES_STR[0]+" "+MODES_STR[2]));
        // whether to dump diffs of correct vs. auto instances
        EvalInstRecord.SHOW_PROBLEMS = Options.getOptionsInstance().getIntDefault("eval_instances_verbose", 0);
        EvalResult.SHOW_MICRO = Options.getOptionsInstance().getIntDefault("eval_show_micro", 0);
        params.put(parHomoSpec, 0);
        log=Logger.getLogger("Eval");
    }
    
    /** Invokes eval() method on each pair of documents from goldDocs and autoDocs. 
     *  Accumulates results in res by macro-averaging. */
    public void eval(DocumentSet goldDocs, DocumentSet autoDocs, EvalResult res) throws IOException {
        if(goldDocs.size()!=autoDocs.size())
            throw new IllegalArgumentException("eval: gold standard docs size="+goldDocs.size()+", auto="+autoDocs.size());
        goldDocSet=goldDocs;
        autoDocSet=autoDocs;
        Iterator<Document> git=goldDocs.getDocuments().iterator();
        Iterator<Document> ait=autoDocs.getDocuments().iterator();
        while(git.hasNext()) {
            Document gold=git.next();
            Document auto=ait.next();
            // use member empty result to log results just for the document
            workResult.clear();
            eval(gold, auto, workResult);
            // contribute result to the global result
            res.add(workResult);
        }
    }

    /** Compares annotations (that comprise AttributeValues) in goldDoc with those found in autoDoc.
     *  The documents must have the same source. If goldDoc==autoDoc, then only annotations whose author=='Gold'
     *  are used as gold standard, and all other annotations are evaluated against them. */
    public void eval(Document goldDoc, Document autoDoc, EvalResult res) throws IOException {
        res.docCnt++;
        docId=goldDoc.getFile();
        homoSpec=(Integer) params.get(parHomoSpec);
        int mode=(Integer) params.get(parEvalMode);
        if((mode & MODE_PRESENCE)!=0) {
            // load docs if not loaded
            boolean err=false;
            if(goldDoc.getSource()==null || goldDoc.getSource().length()==0) {
                if(dr!=null && model!=null)
                    dr.readDocument(goldDoc, model, (goldDocSet!=null)? goldDocSet.getBaseDir(): null, true);
                else
                    err=true;
            }
            if(autoDoc.getSource()==null || autoDoc.getSource().length()==0) {
                if(dr!=null && model!=null)
                    dr.readDocument(autoDoc, model, (autoDocSet!=null)? autoDocSet.getBaseDir(): null, true);
                else
                    err=true;
            }
            if(err) {
                throw new IOException("File(s) not loaded: "+goldDoc.getFile()+" dr="+dr+" model="+model);
            }
        }
        goldAnnots.clear();
        int gac=getAnnotations(goldDoc, goldAnnots, (goldDoc==autoDoc)? INCLUDE: 0, DocumentReader.goldStandardAuthor);
        autoAnnots.clear();
        int aac=getAnnotations(autoDoc, autoAnnots, (goldDoc==autoDoc)? EXCLUDE: 0, DocumentReader.goldStandardAuthor);
        log.LG(Logger.USR,"Comparing "+gac+" gold annotations vs "+aac+" automatic");
        if((mode & MODE_PRESENCE)!=0) {
            evalMatchesPresence(goldAnnots, autoAnnots, res, EVAL_RECALL);
            evalMatchesPresence(autoAnnots, goldAnnots, res, EVAL_PREC);
        }else { // MODE_OCCURRENCE is default:
            evalMatches(goldAnnots, autoAnnots, res, EVAL_RECALL);
            evalMatches(autoAnnots, goldAnnots, res, EVAL_PREC);
        }

        // dump current results (cumulative or for this doc only depending on what was supplied)
        log.LG(Logger.INF, "Eval for doc id="+autoDoc.getId()+", file="+autoDoc.getFile()+"\n"+res.toString());
        
        // measure instance grouping performance
        if(Options.getOptionsInstance().getIntDefault("eval_instances", 1)>0) {
            EvalInstRecord eir=new EvalInstRecord(goldDoc.getFile());
            Villain vil=new Villain(this);
            vil.eval(goldDoc, autoDoc, mode, eir);
            log.LG(Logger.INF, "Instance Villain scores for doc id="+autoDoc.getId()+", file="+autoDoc.getFile()+"\n"+eir.toString());
            res.getAvgInstRecord().add(eir);
        }
        return;
    }
    
    /** Finds the closest matching Annotation to an in haystack. */
    protected MatchedAnnotation findClosestAnnotation(Annotation an, List<Annotation> haystack, int mode) {
        int si=an.getStartOffset();
        int ei=si+an.getLength()-1;
        AttributeDef ad=an2av(an).getAttributeDef();
        return findClosestAnnotation(si, ei, ad, an.getText(), an, haystack, mode);
    }
    
    /** Finds in haystack the closest matching Annotation according to the given criteria. */
    protected MatchedAnnotation findClosestAnnotation(int si, int ei, AttributeDef ad, String text, Annotation an, 
            List<Annotation> haystack, int mode) {
        MatchedAnnotation ma=null;
        exactAnnots.clear(); innerAnnots.clear(); crossAnnots.clear();
        int mc=getAnnotations(haystack, si, ei, mode, ad, exactAnnots, innerAnnots, crossAnnots, text);
        if(mc>0) {
            ma=getBestAnnot(an, exactAnnots, innerAnnots, crossAnnots);
            if(ma==null) {
                throw new IllegalArgumentException("Internal error");
            }
        }
        return ma;
    }
    
    /** Compares a source set of annotations against a target set of annotations. Results are stored in res 
     *  based on how many annotation occurrences matched. */
    private void evalMatches(List<Annotation> srcAnnots, List<Annotation> trgAnnots, EvalResult res, char evalType) {
        int mode=((Integer)params.get(parEvalMode));
        // eval recall
        Iterator<Annotation> anit=srcAnnots.iterator();
        while(anit.hasNext()) {
            Annotation an=anit.next();
            AttributeDef ad=an2av(an).getAttributeDef();
            if(an==null || an.getText()==null) {
                log.LG(Logger.ERR, "Source annotation has no text: attribute="+ad+" annot="+an);
                continue;
            }
            EvalAttRecord rec=res.getRecord(ad, true);
            if(evalType==EVAL_RECALL)
                rec.goldCnt++;
            else // EVAL_PREC
                rec.autoCnt++;
            
            // 1. search for the best matching Annotation of the same attribute
            MatchedAnnotation ma=findClosestAnnotation(an, trgAnnots, mode);
            if(ma!=null) {
                if(ma.type==MatchedAnnotation.EXACT) {
                    if(evalType==EVAL_RECALL)
                        rec.goldExactMatchCnt++;
                    else
                        rec.autoExactMatchCnt++;
                }else {
                    if(evalType==EVAL_RECALL) {
                        rec.goldPartialMatchCnt+=ma.ratio;
                        rec.getConfusion(ad, true).addMatch(an.getText(), ma.annot.getText(), ma.ratio, docId, an.getDebugInfo());
                    }else {
                        rec.autoPartialMatchCnt+=ma.ratio;
                        // this would produce 'auto' -> 'correct'
                        // AttributeDef ad2=(an2av(ma.annot)).getAttributeDef();
                        // rec.getConfusion(ad2, true).addMatch(an.getText(), ma.annot.getText(), ma.ratio, docId, an.getDebugInfo());
                    }
                }
            }
            // 2. search for the best matching Annotation mis-classified as other attributes
            else if(evalType==EVAL_RECALL){
                int si=an.getStartOffset();
                int ei=si+an.getLength()-1;
                ma=findClosestAnnotation(si, ei, ad, an.getText(), null, trgAnnots, mode);
                if(ma!=null) {
                    AttributeDef ad2=(an2av(ma.annot)).getAttributeDef();
                    if(ad2!=null) {
                        Collection<Object> dbgInfo=Util.concatView(an.getDebugInfo(), ma.annot.getDebugInfo());
                        if(ma.type==MatchedAnnotation.EXACT) {
                            rec.getConfusion(ad2, true).addMatch(an.getText(), ma.annot.getText(), 1, docId, dbgInfo);
                        }else {
                            rec.getConfusion(ad2, true).addMatch(an.getText(), ma.annot.getText(), ma.ratio, docId, dbgInfo);
                            //rec.getConfusion(bg, true).addMatch(an.getText(), ma.annot.getText(), 1-ma.ratio);
                        }
                    }else {
                        rec.getConfusion(bg, true).addMatch(an.getText(), "", 1-ma.ratio, docId, an.getDebugInfo());
                    }
                }else {
                    rec.getConfusion(bg, true).addMatch(an.getText(), "", 1, docId, an.getDebugInfo());
                }
            }
            // 3. record that no matching Annotation was found
            else {
                res.getRecord(bg,true).getConfusion(ad, true).addMatch("", an.getText(), 1, docId, an.getDebugInfo());
            }
        }
    }

    /** Selects the best matching annotation for an original annotation.
     * @param baseAnnot original annotation
     * @param exactAnnots annotations that match baseAnnot's position and length
     * @param innerAnnots annotations that are contained within baseAnnot's span
     * @param crossAnnots annotations that cross at least one border of baseAnnot
     * @return the best matching annotation for baseAnnot */
    protected MatchedAnnotation getBestAnnot(Annotation baseAnnot, List<MatchedAnnotation> exactAnnots, List<MatchedAnnotation> innerAnnots, List<MatchedAnnotation> crossAnnots) {
        MatchedAnnotation best=null;
        if(exactAnnots.size()>0) {
            best=exactAnnots.get(0);
        }else {
            if(innerAnnots.size()>0) {
                best=innerAnnots.get(0);
            }
            if(crossAnnots.size()>0) {
                if(best==null || crossAnnots.get(0).ratio > best.ratio)
                    best=crossAnnots.get(0);
            }
        }
        return best;
    }
        
    protected AttributeValue an2av(Annotation an) {
        return (AttributeValue) an.getUserData();
    }
    
    /** Adds to lst all Instances from doc that match flag=INCLUDE||EXCLUDE and author.
     * @return number of added instances. */
    protected int getInstances(Document doc, List<Instance> lst, int flag, String author) {
        int cnt=0;
        // instance members
        for(Instance inst: doc.getInstances()) {
            if(filter!=null && !filter.matches(inst)) {
                log.LG(Logger.INF,"Filtered OUT: "+inst);
                continue;
            }
            if(accept(inst, flag, author)) {
                cnt++;
                lst.add(inst);
            }
        }
        return cnt;
    }
    
    /** @return true if the object matches flag=INCLUDE||EXCLUDE and author. */
    protected boolean accept(AnnotableObject obj, int flag, String author) {
        if(flag==INCLUDE && obj.getAuthor()!=author && (author==null || !author.equals(obj.getAuthor()))) {
            //log.LGERR("---------->"+av+a.getAuthor());
            return false;
        }
        if(flag==EXCLUDE && (obj.getAuthor()==author || (author!=null &&  author.equals(obj.getAuthor())))) {
            //log.LGERR("-----X---->"+av+a.getAuthor());
            return false;
        }
        return true;
    }
    
    /** Retrieves all Annotation objects that constitute AttributeValues in doc.
     *  Note: this method stores AttributeValue instance as user data of its Annotation (overwriting any previous user data). 
     * @param doc Document to process 
     * @param lst List to add Annotations to 
     * @param flag Possible values: INCLUDE, EXCLUDE or 0. Used only if author!=null.
     *        If INCLUDE, only annotations by author are included. If EXCLUDE, annotations by author are excluded.
     * @param author Author name to filter annotations or null
     * @return number of added annotations */
    protected int getAnnotations(Document doc, List<Annotation> lst, int flag, String author) {
        int cnt=0;
        Map<Annotation,Object> seen=new HashMap<Annotation,Object>(doc.getAttributeValues().size());
        // instance members
        for(Instance inst: doc.getInstances()) {
            if(filter!=null && !filter.matches(inst)) {
                log.LG(Logger.INF,"Filtered OUT: "+inst);
                continue;
            }
            for(AttributeValue av: inst.getAttributes()) {
                for(Annotation a: av.getAnnotations()) {
                    if(seen.containsKey(a)) {
                        log.LG(Logger.INF,"Duplicate member annotation "+a);
                        continue;
                    }
                    seen.put(a, null);
                    if(accept(a, flag, author)) {
                        cnt+=storeAnnotation(lst, a, av);
                    }
                }
            }
        }
        // standalone
        for(AttributeValue av: doc.getAttributeValues()) {
            if(filter!=null && !filter.matches(av)) {
                log.LG(Logger.INF,"Filtered OUT: "+av);
                continue;
            }
            for(Annotation a: av.getAnnotations()) {
                if(seen.containsKey(a)) {
                    log.LG(Logger.INF,"Duplicate member/standalone annotation "+a);
                    continue;
                }
                seen.put(a, null);
                if(accept(a, flag, author)) {
                    cnt+=storeAnnotation(lst, a, av);
                }
            }
        }
        seen.clear();
        Collections.sort(lst, acmpr);
        return cnt;
    }
    
    /** Stores av as a's user data, and adds a to lst. */
    protected int storeAnnotation(List<Annotation> lst, Annotation a, AttributeValue av) {
        int cnt=0;
        if(a.getUserData()!=null && a.getUserData()!=av) {
            //log.LG(Logger.INF,"Overwriting user data "+a.getUserData()+" with "+av);
        }
        a.setUserData(av);
        lst.add(a);
        cnt++;
        return cnt;
    }
    
    /** Fills a list of all annotations in range and belonging to an attribute */
    public int getAnnotations(List<Annotation> srcList, int startCharIdx, int endCharIdx, int mode, AttributeDef ad, 
            List<MatchedAnnotation> exactMatches, List<MatchedAnnotation> innerMatches, List<MatchedAnnotation> crossMatches,
            String rangeText) {
        int cnt=0;
        int srcLen=endCharIdx-startCharIdx+1;
        if(srcLen!=rangeText.length())
            throw new IllegalArgumentException("Range text '"+rangeText+"' of wrong size="+srcLen+" (offset="+startCharIdx+" len="+srcLen+") realsize="+rangeText.length());
        Iterator<Annotation> anit=srcList.iterator();
        while(anit.hasNext()) {
            Annotation an=anit.next();
            if(an==null || an.getText()==null) {
                log.LG(Logger.ERR, "getAnnotations: Source annotation has no text: annot="+an);
                continue;
            }
            int si=an.getStartOffset();
            int ei=si+an.getLength()-1;
            if(ad!=null) {
                AttributeDef ad2=an2av(an).getAttributeDef();
                if(homoSpec!=0) {
                    // treat specializations of single attribute like "name", "name_responsible", "name_sponsor" alike
                    int i=ad.getName().indexOf("_");
                    String adBaseName=(i!=-1)? ad.getName().substring(0, i): ad.getName();
                    i=ad2.getName().indexOf("_");
                    String ad2BaseName=(i!=-1)? ad2.getName().substring(0, i): ad2.getName();
                    if(!adBaseName.equals(ad2BaseName))
                        continue;
                }else {
                    if(ad!=ad2)
                        continue;
                }
            }
            if(si==startCharIdx && ei==endCharIdx) { // exact
                exactMatches.add(new MatchedAnnotation(an, 1.0, MatchedAnnotation.EXACT));
                cnt++;
            }else if(si>=startCharIdx && ei<=endCharIdx) { // inner
                int matchSize=ei-si+1;
                List<MatchedAnnotation> lst=innerMatches;
                char type=MatchedAnnotation.INNER;
                if((mode & MODE_LOOSE)!=0) {
                    // treat all ignorable non-matched parts as matched 
                    if(si>startCharIdx && isIgnorable(rangeText.substring(0,si-startCharIdx)))
                        matchSize+=si-startCharIdx;
                    if(ei<endCharIdx && isIgnorable(rangeText.substring(ei-startCharIdx+1)))
                        matchSize+=endCharIdx-ei;
                    if(matchSize==srcLen) {
                        type=MatchedAnnotation.EXACT;
                        lst=exactMatches;
                    }else {
                        // recompute the loose ratio so as to ignore e.g. long sequences of whitespace between tokens 
                        srcLen=nonIgnorableLength(rangeText);
                        matchSize=nonIgnorableLength(an.getText());
                    }
                }
                if(lst!=null) {
                    lst.add(new MatchedAnnotation(an, (double)matchSize/(double)srcLen, type));
                    cnt++;
                }
            }else if(si<=startCharIdx && ei>=endCharIdx) { // outer
                // whole source area is matched; even more...
                List<MatchedAnnotation> lst=crossMatches;
                char type=MatchedAnnotation.OUTER;
                try {
                    if(((mode & MODE_LOOSE)!=0) && (isIgnorable(an.getText().substring(0,startCharIdx-si))) 
                            && (isIgnorable(an.getText().substring(endCharIdx-si+1)))) {
                        lst=exactMatches;
                        type=MatchedAnnotation.EXACT;
                    }
                }catch(Exception e) {
                    log.LG("Doprdele");
                }
                if(lst!=null) {
                    lst.add(new MatchedAnnotation(an, 1.0, type)); 
                    cnt++;
                }
            }else if(si> startCharIdx && si<endCharIdx && ei>endCharIdx) { // crossed + overlaps after
                int matchSize=endCharIdx-si+1;
                List<MatchedAnnotation> lst=crossMatches;
                char type=MatchedAnnotation.CROSSED_AFTER;
                if((mode & MODE_LOOSE)!=0) {
                    if(isIgnorable(rangeText.substring(0,si-startCharIdx))) {
                        matchSize+=si-startCharIdx; // ==srcLen
                        if(isIgnorable(an.getText().substring(endCharIdx-si+1))) {
                            lst=exactMatches;
                            type=MatchedAnnotation.EXACT;
                        }
                    }
                    if(type!=MatchedAnnotation.EXACT) {
                        // recompute the loose ratio so as to ignore e.g. long sequences of whitespace between tokens 
                        srcLen=nonIgnorableLength(rangeText);
                        matchSize=nonIgnorableLength(an.getText().substring(0,endCharIdx-si+1));
                    }
                }
                if(lst!=null) {
                    crossMatches.add(new MatchedAnnotation(an, (double)matchSize/srcLen, type));
                    cnt++;
                }
            }else if(ei< endCharIdx && ei>startCharIdx && si<startCharIdx) { // crossed + overlaps before
                int matchSize=ei-startCharIdx+1;
                List<MatchedAnnotation> lst=crossMatches;
                char type=MatchedAnnotation.CROSSED_BEFORE;
                if((mode & MODE_LOOSE)!=0) {
                    if(isIgnorable(rangeText.substring(ei-startCharIdx+1))) {
                        matchSize+=endCharIdx-ei; // ==srcLen
                        if(isIgnorable(an.getText().substring(0,startCharIdx-si))) {
                            lst=exactMatches;
                            type=MatchedAnnotation.EXACT;
                        }
                    }
                    if(type!=MatchedAnnotation.EXACT) {
                        // recompute the loose ratio so as to ignore e.g. long sequences of whitespace between tokens 
                        srcLen=nonIgnorableLength(rangeText);
                        matchSize=nonIgnorableLength(an.getText().substring(startCharIdx-si));
                    }
                }
                if(lst!=null) {
                    lst.add(new MatchedAnnotation(an, (double)matchSize/srcLen, type));
                    cnt++;
                }
            }else if(si>endCharIdx) { // anots are sorted by start idx
                break;
            }
        }
        if(innerMatches.size()>0)
            Collections.sort(innerMatches, macmpr);
        if(crossMatches.size()>0)
            Collections.sort(crossMatches, macmpr);
        return cnt;
    }
    
    /** returns portion of an1 covered by an2 */
    public double overlap(Annotation an1, Annotation an2) {
        int si1=an1.getStartOffset();
        int si2=an2.getStartOffset();
        int ovlStart=Math.max(si1, si2);
        int ei1=an1.getStartOffset()+an1.getLength();
        int ei2=an2.getStartOffset()+an2.getLength();
        int ovlEnd=Math.min(ei1, ei2);
        if(ovlEnd<ovlStart) {
            return 0.0;
        }
        return (ovlEnd-ovlStart+1) / an1.getLength();
    }

    public void setDataModel(DataModel model) {
        this.model=model;
    }

    public DataModel getDataModel() {
        return model;
    }
    
    public void setDocumentReader(DocumentReader reader) {
        this.dr=reader;
    }
    
    public DocumentReader getDocumentReader() {
        return dr;
    }
    
    private static String ignorableChars="[\\s()\\.,\\-+;!:\\\"']+";
    private static Pattern patN1=Pattern.compile("^"+ignorableChars);
    private static Pattern patN2=Pattern.compile(ignorableChars+"$");
    private static Pattern patN3=Pattern.compile(ignorableChars);
    private static Pattern patN4=Pattern.compile("(^|"+ignorableChars+")(the|a|el)("+ignorableChars+"|$)");
    private static Pattern patTg=Pattern.compile("\\s*<[^>]+>\\s*");
    protected String normalizeValue(String val) {
        val=val.toLowerCase();
        val=patN4.matcher(val).replaceAll("");
        val=patN1.matcher(val).replaceAll("");
        val=patN2.matcher(val).replaceAll("");
        val=patN3.matcher(val).replaceAll("");
        val=patTg.matcher(val).replaceAll("");
        //log.LGERR("=>"+val);
        return val;
    }
    
    protected boolean isIgnorable(String s) {
        boolean rc = s.length()==0 || patN3.matcher(s).matches() || patN4.matcher(s).matches() || 
                     patTg.matcher(s).replaceAll("").length()==0;
        //if(s.contains("<")) {
        //    log.LGERR(s+"rc="+rc);
        //}
        return rc;
    }
    
    protected int nonIgnorableLength(String s) {
        return normalizeValue(s).length();
    }
    
    protected Map<String,Map<AttributeDef,List<Annotation>>> getNormalizedValues(List<Annotation> annots) {
        // normalized value of 1 att -> map of (att name -> list of source annots of the att)
        Map<String,Map<AttributeDef,List<Annotation>>> srcVals=
            new HashMap<String,Map<AttributeDef,List<Annotation>>>(annots.size());
        for(Annotation an: annots) {
            AttributeDef ad=an2av(an).getAttributeDef();
            String key=normalizeValue(an.getText());
            Map<AttributeDef,List<Annotation>> annotsPerAtt=srcVals.get(key);
            if(annotsPerAtt==null) {
                annotsPerAtt=new HashMap<AttributeDef,List<Annotation>>(2);
                srcVals.put(key, annotsPerAtt);
            }
            
            List<Annotation> lst=annotsPerAtt.get(ad);
            if(lst==null) {
                lst=new LinkedList<Annotation>();
                annotsPerAtt.put(ad, lst);
            }
            lst.add(an);
        }
        return srcVals;
    }
    
    private void evalMatchesPresence(List<Annotation> srcAnnots, List<Annotation> trgAnnots, EvalResult res, char evalType) {
        Map<String,Map<AttributeDef,List<Annotation>>> srcVals=getNormalizedValues(srcAnnots);
        Map<String,Map<AttributeDef,List<Annotation>>> trgVals=getNormalizedValues(trgAnnots);
        for(Map.Entry<String,Map<AttributeDef,List<Annotation>>> entry: srcVals.entrySet()) {
            String val=entry.getKey();
            for(AttributeDef ad1: entry.getValue().keySet()) {
                // update src count
                EvalAttRecord rec=res.getRecord(ad1, true);
                if(evalType==EVAL_RECALL)
                    rec.goldCnt++;
                else // EVAL_PREC
                    rec.autoCnt++;
                // find match
                Map<AttributeDef,List<Annotation>> trgAttVals=trgVals.get(val);
                if(trgAttVals!=null && trgAttVals.containsKey(ad1)) {
                    if(evalType==EVAL_RECALL)
                        rec.goldExactMatchCnt++;
                    else
                        rec.autoExactMatchCnt++;
                // update confusion matrix
                }else if(evalType==EVAL_RECALL && trgAttVals!=null) {
                    // TODO:
                    log.LGERR("CFM not implemented!");
                    for(Map.Entry<AttributeDef,List<Annotation>> en: trgAttVals.entrySet()) {
                        AttributeDef ad2=en.getKey();
                        rec.getConfusion(ad2, true).addMatch(val, "?", 1, docId, null);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if(args.length<2) {
            System.err.println("Usage: java EvaluatorImpl doc_goldstandard.html doc_annotated.html");
            System.exit(-1);
        }
        DocumentReader dr=new DatDocumentReader();
        Evaluator ev=new EvaluatorImpl();
        DataModel em=new DataModelImpl("", "anonymous");
        Document gold=null, auto=null;
        EvalResult res=new EvalResult(em.getName());
        try {
            gold=dr.readDocument(args[0], null, em, null, true);
            auto=dr.readDocument(args[1], null, em, null, true);
            ev.eval(gold, auto, res);
        }catch (IOException ex) {
            System.err.println("Error reading docs: "+ex);
            System.exit(-1);
        }
        System.out.println(res);
    }

    public void configure(Properties newParams) {
        params.putAll(newParams);
    }

    public void configure(InputStream cfgFile) throws IOException {
        params.load(cfgFile);
    }

    public Object getParam(String name) {
        return params.get(name);
    }

    public void setParam(String name, Object value) {
        if(name.equals(parEvalMode)) {
            setEvalMode((String)value);
        }else if(name.equals(parHomoSpec)) {
            params.put(name, Integer.parseInt((String)value));
        }else if(name.equals("filter")) {
            params.put(name, (String)value);
            filter=new AuthorAnnotationFilter((String)value);
        }else {
            params.put(name, value);
        }
    }

    public boolean initialize(String cfgFile) throws IOException {
        if(cfgFile!=null) {
            configure(new FileInputStream(new File(cfgFile)));
        }
        return true;
    }

    public void uninitialize() {
        ;
    }

    public boolean cancel(int cancelType) {
        return false; // not supported
    }

    public String getName() {
        return "evaluator";
    }
    
    public void setEvalMode(String val) {
        String[] vals=val.trim().toLowerCase().split("\\s+");
        Integer mode=0;
        for(String pv: vals) {
            int i=0;
            int pvInt=1;
            for(;i<MODES_STR.length;i++) {
                if(pv.equals(MODES_STR[i])) {
                    mode |= pvInt;
                    break;
                }
                pvInt*=2;
            }
            if(i==MODES_STR.length) {
                int pvNo=Integer.parseInt(val);
                if(mode<1||mode>MODES_STR.length)
                    throw new IllegalArgumentException("eval_mode = (occurrence|presence) (exact|loose)");
                mode |= (int) Math.pow(2, pvNo);
            }
        }
        params.put(parEvalMode, mode);
    }
    
    public String getEvalMode() {
        int mode=((Integer)params.get(parEvalMode));
        StringBuffer ret=new StringBuffer(32);
        int pvInt=1;
        for(int i=0;i<MODES_STR.length;i++) {
            if((mode & pvInt)!=0) {
                if(ret.length()>0)
                    ret.append(" ");
                ret.append(MODES_STR[i]);
            }
            pvInt*=2;
        }
        return ret.toString();
    }
    
    public String toString() {
        return this.getClass().getName()+"/"+getEvalMode();
    }

    public static String evalType2string(char evalType) {
        if(evalType>=1 && evalType<=evalTypes.length) {
            return evalTypes[evalType-1];
        }
        return "illegal evalType";
    }
    
    /** Computes micro-averaged results from macro-averaged EvalResult that each 
     *  Document in docs has. */
    public void getMicroResults(List<Document> docs, MicroResult micro) {
        for(Document doc: docs) {
            EvalResult der = doc.getEvalResult();
            micro.add(der);
        }
        micro.commit();
    }
}

/* Helper classes */

class AnnotComparator implements Comparator<Annotation> {
    public int compare(Annotation a1, Annotation a2) {
        int d=a1.getStartOffset()-a2.getStartOffset();
        if(d==0) {
            d=a1.getLength()-a2.getLength();
            if(d==0)
                d=a1.hashCode()-a2.hashCode();
        }
        return d;
    }
}

class MatchedAnnotComparator implements Comparator<MatchedAnnotation> {
    public static AnnotComparator acmpr=new AnnotComparator();
    public int compare(MatchedAnnotation a1, MatchedAnnotation a2) {
        double d=a2.ratio-a1.ratio;
        if(d==0.0)
            return acmpr.compare(a1.annot, a2.annot);
        return (d>0.0)? 1: -1;
    }
}

class MatchedAnnotation {
    public static final char EXACT=1;
    public static final char INNER=2;
    public static final char OUTER=3;
    public static final char CROSSED_BEFORE=4;
    public static final char CROSSED_AFTER=5;
    public static final char ARTIFICIAL=6;
    public MatchedAnnotation(Annotation annot, double ratio, char type) {
        this.annot=annot;
        this.ratio=ratio;
        this.type=type;
    }
    public Annotation annot;
    public double ratio;
    public char type;
}

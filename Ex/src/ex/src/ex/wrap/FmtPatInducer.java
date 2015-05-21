// $Id: FmtPatInducer.java 2038 2009-05-21 00:23:51Z labsky $
package ex.wrap;

import java.util.*;

import uep.util.Logger;
import uep.util.Options;
import ex.util.pr.PR_Evidence;
import ex.util.search.*;
import ex.ac.*;
import ex.reader.*;
import ex.parser.*;
import ex.model.AttributeDef;

public class FmtPatInducer {
    protected Logger log;
    protected static final int IC_KEY_ACS=1;
    protected static final int IC_KEY_PAT=2;
    public int icKey=IC_KEY_PAT;
    protected double FMTPAT_P_FACTOR = 0.75;
    protected double FMTPAT_R_FACTOR = 0.75;
    
    public FmtPatInducer() {
        log=Logger.getLogger("FPI");
        FMTPAT_P_FACTOR = Options.getOptionsInstance().getDoubleDefault("fmtpat_pfactor", FMTPAT_P_FACTOR);
        FMTPAT_R_FACTOR = Options.getOptionsInstance().getDoubleDefault("fmtpat_rfactor", FMTPAT_R_FACTOR);
    }

    class FmtLayoutRecord {
        public FmtLayout layout;
        public ArrayList<ICBase> ics;
        public FmtLayoutRecord(FmtLayout layout) {
            this.layout=layout;
            this.ics=new ArrayList<ICBase>(4);
        }
    }

    public int induce(Set<ICBase> ics, Document doc, List<FmtPattern> pats) {
        /** make separate sets of ICs based on content */
        int patCnt=0;
        HashMap<String,List<ICBase>> icsByContent=new HashMap<String,List<ICBase>>();
        Iterator<ICBase> icit=ics.iterator();
        StringBuffer buff=new StringBuffer(64);
        while(icit.hasNext()) {
            ICBase ic=icit.next();
            String contentSignature=null;
            buff.setLength(0);
            if(icKey==IC_KEY_ACS) {
                ic.getAttrNames(buff);
                contentSignature=buff.toString();
            }else { // IC_KEY_PAT
                FmtLayout fl=ic.getFmtLayout();
                if(fl!=null)
                    contentSignature=fl.toString();
            }
            if(contentSignature==null) {
                log.LG(Logger.WRN,"Cannot get signature for IC "+ic);
                continue;
            }
            List<ICBase> icList=null;
            if(!icsByContent.containsKey(contentSignature)) {
                icList=new LinkedList<ICBase>();
                icsByContent.put(contentSignature,icList);
            }else {
                icList=icsByContent.get(contentSignature);
            }
            icList.add(ic);
        }
        /** compute the best path from each IC set, find formatting similarities within each IC path */
        Set<Map.Entry<String, List<ICBase>>> icListSet=icsByContent.entrySet();
        Iterator<Map.Entry<String, List<ICBase>>> lrit=icListSet.iterator();
        ArrayList<ICBase> noOverlapList=new ArrayList<ICBase>(32);
        while(lrit.hasNext()) {
            Map.Entry<String, List<ICBase>> entry=lrit.next();
            List<ICBase> lst=entry.getValue();
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Finding path through ics like "+entry.getKey()+" cnt="+lst.size());
            GState[] icStates=ICLattice.toGraph(lst, doc, false, null);
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC lattice for fmtpat induction:\n"+ICLattice.toString(icStates));
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC fmtpat lattice graph:\n"+Lattice.toGraph(icStates[0]));
            // search for the best path
            Lattice lat=new Lattice();
            ArrayList<Path> paths=new ArrayList<Path>(1);
            int pc=lat.search(icStates,1,paths);
            if(pc<=0) {
                log.LG(Logger.ERR,"Error finding best path through lattice of ICs like "+entry.getKey());
                continue;
            }
            Path path=paths.get(0);
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Inducing patterns from "+(path.states.size()-2)+" non-overlapping ICs like "+entry.getKey()+": "+path.toString());
            noOverlapList.clear();
            noOverlapList.ensureCapacity(path.states.size()-2);
            Iterator<State> it=path.states.iterator();
            int i=0;
            while(it.hasNext()) {
                i++;
                GState st=(GState) it.next();
                if(i==1 || !it.hasNext()) // leave out null init and final states
                    continue;
                noOverlapList.add((ICBase) st.data);
            }
            patCnt+=inducePatterns(noOverlapList, doc, pats);
        }

        /* sort & dump learnt patterns */
        Collections.sort(pats);
        int lev = Logger.USR;
        if(log.IFLG(lev)) {
            StringBuffer b=new StringBuffer(128);
            b.append("Learnt fmt patterns:\n");
            Iterator<FmtPattern> pi=pats.iterator();
            int i=0;
            while(pi.hasNext()) {
                b.append((++i)+". "+pi.next()+"\n");
            }
            log.LG(lev,b.toString());
        }

        return patCnt;
    }

    public int inducePatterns(Collection<ICBase> ics, Document doc, List<FmtPattern> pats) {
        int cnt=0;
        HashMap<String,FmtLayoutRecord> fmtLayoutMap=new HashMap<String,FmtLayoutRecord>();
        Iterator<ICBase> icit=ics.iterator();
        // assemble per-instance layouts and their counts
        while(icit.hasNext()) {
            ICBase ic=icit.next();
            FmtLayout fl=ic.getFmtLayout();
            if(fl==null) {
                log.LG(Logger.WRN,"No fmt layout determined for IC "+ic+": removing from induction IC set");
                icit.remove();
                continue;
            }
            String s=fl.toString();
            FmtLayoutRecord rec=null;
            if(!fmtLayoutMap.containsKey(s)) {
                rec=new FmtLayoutRecord(fl);
                fmtLayoutMap.put(s, rec);
            }else {
                rec=fmtLayoutMap.get(s);
            }
            rec.ics.add(ic);
        }
        // for each encountered layout, compute precision = count(layout, IC) / total count(layout) in doc
        Set<Map.Entry<String, FmtLayoutRecord>> layoutSet=fmtLayoutMap.entrySet();
        Iterator<Map.Entry<String, FmtLayoutRecord>> lrit=layoutSet.iterator();
        while(lrit.hasNext()) {
            Map.Entry<String, FmtLayoutRecord> entry=lrit.next();
            FmtLayoutRecord lrec=entry.getValue();
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Counting total occurences of "+entry.getKey()+" C(FMTPAT,IC)="+lrec.ics.size());
            int matchCnt=findMatches(doc, lrec.layout);
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"FMTPAT "+entry.getKey()+" C(FMTPAT,IC)="+lrec.ics.size()+" C(FMTPAT)="+matchCnt+" C(IC)="+ics.size());
            double prec=(double) lrec.ics.size() / matchCnt;
            double rec=(double) lrec.ics.size() / ics.size();
            if(prec<0.33 || rec<0.5 || lrec.ics.size()<=1)
                continue;
            cnt++;
            prec *=FMTPAT_P_FACTOR;
            rec *=FMTPAT_R_FACTOR;
            FmtPattern fp=new FmtPattern(lrec.layout, lrec.ics.size(), matchCnt, ics.size(), prec, rec);
            PR_Evidence ev=new PR_Evidence("PAT_ICFMT_"+fp.hashCode(), fp.precision, fp.recall, (byte)0, -1);
            fp.evidence = ev;
            pats.add(fp);
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Derived new FMTPAT "+fp);
        }
        return cnt;
    }

    public int applyPatterns(Document doc, List<FmtPattern> pats, List<AC> affectedACs, List<AC> newACs) {
        int cnt=0;
        Iterator<FmtPattern> it=pats.iterator();
        while(it.hasNext()) {
            FmtPattern pat=it.next();
            findMatches(doc, pat.layout, pat, affectedACs, newACs);
        }
        return cnt;
    }

    public int findMatches(Document doc, FmtLayout layout) {
        return findMatches(doc, layout, null, null, null);
    }
    
    /**
     * Finds matches of layout formatting pattern given by layout in document. 
     * @param doc Document to analyze
     * @param layout Format layout to be found in doc
     * @param acCreator If null, the method only counts matches. Otherwise, new ACs
     * are created and old ones are updated.
     * @param affectedACs array to be filled with all ACs whose score changed
     * @param newACs array to be filled with all newly induced ACs 
     * @return number of matches in doc.
     */
    public int findMatches(Document doc, FmtLayout layout, FmtPattern acCreator, List<AC> affectedACs, List<AC> newACs) {
        int cnt=0;
        TagAnnot last=null;
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot tok=doc.tokens[i];
            TagAnnot docTag=(TagAnnot) tok.parent;
            // our level of granularity is whole tags for now
            if(docTag==last || docTag.hasAncestor(last))
                continue;
            last=docTag;

            // identify first lowest tag in layout, then path to layout root
            TagAnnot layTag=layout.firstTag;
            TagAnnot docRoot=null;
            while(layTag.type==docTag.type) {
                if(docTag.parentIdx<layTag.startIdx || docTag.parentIdx>layTag.startIdxInner) {
                    break;
                }
                if(layTag==layout.root) {
                    docRoot=docTag;
                    break;
                }
                layTag=(TagAnnot) layTag.parent;
                docTag=(TagAnnot) docTag.parent;
                if(layTag==null || docTag==null) {
                    log.LG(Logger.ERR,"Error matching layout pattern "+layout+": cannot find path to layout root; layTag="+layTag+", docTag="+docTag+", url="+doc.id);
                    docRoot=null;
                    break;
                }
            }
            if(docRoot==null)
                continue;

            // path to layout root found; now search for the rest of the layout
            boolean rc=matchLayout(docRoot, layTag, acCreator, doc, affectedACs, newACs);
            if(rc) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"FmtPat matched at "+tok);
                cnt++;
            }
        }
        return cnt;
    }

    public boolean matchLayout(TagAnnot docTag, TagAnnot layTag, FmtPattern acCreator, Document doc, List<AC> affectedACs, List<AC> newACs) {
        if(docTag.type!=layTag.type)
            return false;
        int minIdx=layTag.startIdx;
        int maxIdx=layTag.startIdxInner;
        if(docTag.parentIdx<minIdx || docTag.parentIdx>maxIdx)
            return false;
        for(int i=0;i<layTag.childNodes.length;i++) {
            Annot a=layTag.childNodes[i];
            if(a instanceof SemAnnot) {
                // check semAnnot can match something
                int tc=docTag.tokenCnt(false);
                if(tc==0)
                    return false;
                // log matched text
                TokenAnnot sta=docTag.firstToken;
                if(log.IFLG(Logger.USR)) log.LG(Logger.USR,"FP-matched "+Document.toString(doc.tokens, sta.idx, tc, " "));
                // create new / boost existing ACs
                if(acCreator!=null) {
                    createUpdateAC(sta, tc, (SemAnnot)a, acCreator, doc, affectedACs, newACs);
                    // acCreator.precision;
                }
                continue;
            }else if(a instanceof TagAnnot) {
                TagAnnot layChild=(TagAnnot) a;
                minIdx=layChild.startIdx;
                maxIdx=layChild.startIdxInner;
                if(maxIdx>minIdx)
                    Logger.LOG(Logger.ERR, "Ranges not yet implemented: "+maxIdx);
                if(minIdx>=docTag.childNodes.length)
                    return false;
                Annot da=docTag.childNodes[minIdx];
                if(!(da instanceof TagAnnot))
                    return false;
                TagAnnot docChild=(TagAnnot) da;
                if(!matchLayout(docChild, layChild, acCreator, doc, affectedACs, newACs)) {
                    return false;
                }
                continue;
            }else {
                Logger.LOG(Logger.ERR, "Unknown annot type: "+a);
            }
        }
        return true;
    }

    int createUpdateAC(TokenAnnot ta, int len, SemAnnot attrAnnot, FmtPattern fmtPat, Document doc, List<AC> affectedACs, List<AC> newACs) {
        int modCnt=0;
        AC exactAc=null;
        AttributeDef predictedAttr = (AttributeDef) attrAnnot.data;
        for(int i=0; ta.acs!=null && i<ta.acs.length; i++) {
            // only update attributes that match each other, but allow for specializations
            AttributeDef ad1 = ta.acs[i].getAttribute();
            if(ad1==predictedAttr || ad1.isDescendantOf(predictedAttr) || predictedAttr.isDescendantOf(ad1)) {
                AC ac=null;
                // there is an AC which spans exactly according to our fmt pattern
                if(ta.acs[i].len==len) {
                    // boost it
                    ac=ta.acs[i];
                    ac.addCustomEvidence(fmtPat.evidence, (byte)1);
                    //ac.updateEvidenceList();
                    //ac.setEvidenceValue(fmtPat.evidence, (byte)1);
                    exactAc=ac;
                // an AC which spans inside the right slot of our fmt pattern
                }else if(ta.acs[i].len<len) {
                    // boost it
                    ac=ta.acs[i];
                    ac.addCustomEvidence(fmtPat.evidence, (byte)1);
                    // ac.updateEvidenceList();
                    // ac.setEvidenceValue(fmtPat.evidence, (byte)1);
                }
                if(ac!=null) {
                    ac.condProb();
                    affectedACs.add(ac);
                    modCnt++;
                }
            }
        }
        if(exactAc==null) { // create new
            // ac=new AC(attrAnnot.modelAttribute, ta, len, false, attrAnnot.modelAttribute.getDefaultEvidenceValues(), doc);
            AC ac=new AC((AttributeDef)attrAnnot.data, ta, len, false, null, doc);
            if(ac.value==null) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Fmt-induced AC "+ac+" was not created due to unsatisfied constraints");
            }else {
                // Arrays.fill(ac.eVals, (short) 0);
                // keep all "normal" patterns 0;
//                for(int i=ac.getAttribute().getDefaultEvidenceCount();i<ac.eVals.length;i++) {
//                    if(((AttributeDef)attrAnnot.data).prClass.evs[i].name.startsWith("PAT_ICFMT")) {
//                        ac.eVals[i]=-1;
//                    }else {
//                        // ac.eVals[i]=0;
//                    }
//                }
                ac.applyPatternEvidence(doc);
                ac.addCustomEvidence(fmtPat.evidence, (byte)1);
                // ac.setEvidenceValue(fmtPat.evidence, (byte)1);
                ac.condProb();
                // ac.setValue(); // called in ctor: compute canonicalized value, check constraints
                if(!ac.isHopeless()) {
                    doc.addAC(ac);
                    newACs.add(ac);
                    modCnt++;
                    if(log.IFLG(Logger.USR)) log.LG(Logger.USR,"Discovered new AC="+ac);
                }else {
                    if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Fmt-induced AC "+ac+" was not created since its prob is hopeless="+ac.getProb());
                }
            }
        }
        return modCnt;
    }
}

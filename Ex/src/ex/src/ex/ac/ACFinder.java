// $Id: ACFinder.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.*;

import ex.model.*;
import ex.reader.*;
import ex.util.pd.*;

/** Creates new ACs in a document basewd on content and context pattern matches. 
 *  Can use ModelMatcher to search for AC-dependent patterns after new ACs have been created. */
public class ACFinder {
    private Logger log;
    private Options o;
    private ArrayList<AC> acl;
    
    public ACFinder() {
        log=Logger.getLogger("ACFinder");
        o=Options.getOptionsInstance();
        acl=new ArrayList<AC>(16);
    }
    
    /** Creates ACs from value and context pattern matches (PatMatches) in document.
     *  Uses ModelMatcher and Model to search again for AC-dependent patterns after 
     *  AC-independent pattern matches have been used to create new ACs. 
     **/
    public int findScoreACs(Document doc, ModelMatcher modelMatcher, Model model, int patternFilter) {
        if(model.getGenerateExtraInfo())
            doc.setGenerateExtraInfo(true);
        // update default evidence and length evidence modes 
        AttributeDef.default_evidence_mode=o.getIntDefault("default_evidence_mode", AttributeDef.default_evidence_mode);
        AttributeDef.length_evidence_mode=o.getIntDefault("length_evidence_mode", AttributeDef.length_evidence_mode);
        AttributeDef.numval_evidence_mode=o.getIntDefault("numval_evidence_mode", AttributeDef.numval_evidence_mode);
        AttributeDef.normalize_ac_probs=o.getIntDefault("normalize_ac_probs", AttributeDef.normalize_ac_probs);
        
        // first, turn all value PatMatches into ACs
        int acCnt=updateACsFromValuePatterns(doc, patternFilter);
        // second, derive new (rescore existing) ACs based on context PatMatches
        acCnt+=updateACsFromContextPatterns(doc, patternFilter);
        if(log.IFLG(Logger.USR)) log.LG(Logger.USR,"AC-free patterns created "+acCnt+" ACs");
        
        // find patterns that depend on other ACs
        // FIXME: bug - this re-matches SemAnnots created by classifiers once again!
        int rc=modelMatcher.matchModelPatterns(model, doc, ModelMatcher.USE_ATTR_PATTERNS, TokenPattern.PATTERN_WITH_ACS, null);
        if(log.IFLG(Logger.USR)) log.LG(Logger.USR,"Matched "+rc+" AC-dependent patterns");

        // repeat the above search only for AC-dependent patterns:
        int acCnt2=updateACsFromValuePatterns(doc, TokenPattern.PATTERN_WITH_ACS);
        acCnt2+=updateACsFromContextPatterns(doc, TokenPattern.PATTERN_WITH_ACS);
        if(log.IFLG(Logger.USR)) log.LG(Logger.USR,"AC-dependent patterns created "+acCnt2+" ACs");
        
        // rescore ACs based on value distributions, value length distributions: done automatically in AC constructor
        // derive new ACs based on <contains> information - done later during parsing

        // update scores of created ACs, prune
        int acCnt3=pruneRecomputeACs(doc);

        if(log.IFLG(Logger.USR)) log.LG(Logger.USR,"Created "+acCnt+"+"+acCnt2+"/"+acCnt3+" ACs; patternFilter="+patternFilter);
        return acCnt+acCnt2;
    }
    
    /** Creates or updates ACs based on value pattern matches found in doc. 
     *  Set acOnly to true to exclude context patterns that do not depend on other ACs. */
    protected int updateACsFromValuePatterns(Document doc, int patternFilter) { // boolean acOnly
        /* TODO: we could solve nested ACs right here instead of waiting for IC generation to do that more expensively,
           inner ACs could then be treated only as part of the larger ones */
        int rc=0;
        acl.clear();
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];
            if(ta.matchStarts==null)
                continue;
            boolean skipped=false;
matches:    for(int j=0;j<ta.matchStarts.size();j++) {
                PatMatch pm=ta.matchStarts.get(j);
                if(!TokenPattern.usePattern(pm.pat, patternFilter))
                    continue;
                switch(pm.getType()) {
                case TokenPattern.PAT_VAL:
                    AC ac=null;
                    // check if we already have an AC exactly at this place of the right length 
                    // and of the pattern's attribute, or of its specialization
                    AC general=null;
                    int cnt=acl.size() + ((ta.acs!=null)? ta.acs.length: 0);
                    for(int k=0;k<cnt;k++) {
                        ac=(k<acl.size())? acl.get(k): ta.acs[k-acl.size()];
                        if(ac.len==pm.len) {
                            if((ac.getAttribute()==pm.getModelElement() || ac.getAttribute().isDescendantOf(pm.getModelElement()))) {
                                ac.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                                continue matches;
                            }else if(pm.getModelElement().isDescendantOf(ac.getAttribute())) {
                                general=ac;
                            }
                        }
                        ac=null;
                    }
                    if(((pm.pat.flags & TokenPattern.FLAG_AND_GROUP_MEMBER)==0) && pm.pat.evidence.prec<pm.pat.getAttributeDef().prClass.prior) {
                        // TODO: ACs should take into account PMs in their constructor instead of relying on ACFinder.
                        // With this "optimization", negative patterns need be matched after positive ones.
                        // This is too shaky.
                        skipped=true;
                        continue;
                    }
                    // ac=new AC(pm.pat.getAttributeDef(), ta, pm.len, false, pm.pat.getAttributeDef().getDefaultEvidenceValues(), doc);
                    ac=new AC(pm.pat.getAttributeDef(), ta, pm.len, false, null, doc);
                    // ac.setValue(); // called in ctor
                    if(ac.value==null) {
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"AC "+ac+" was not created due to unsatisfied constraints");
                    }else {
                        if(general!=null) {
                            ac.copyEvidenceValuesFrom(general);
                        }
                        ac.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                        // ac.condProb(); // called below after all evidence is gathered
                        acl.add(ac);
                        rc++;
                        if(skipped) { // restart to include all patterns for this AC 
                            j=-1;
                            continue;
                        }
                    }
                    break;
                }
            }
            doc.addAC(acl);
            acl.clear();
        }
        return rc;
    }

    /** Creates or updates ACs based on context pattern matches found in doc. 
     *  Set acOnly to true to exclude context patterns that do not depend on other ACs. */
    protected int updateACsFromContextPatterns(Document doc, int patternFilter) { // boolean acOnly
        int rc=0;
        // openAtts tracks already labeled attribute values for boosting via right context patterns
        ArrayList<LabelRecord> openAtts=new ArrayList<LabelRecord>(8);
        ArrayList<AC> jel=new ArrayList<AC>(8); // "just ended" attribute list
        ArrayList<AC> derivedList=new ArrayList<AC>(8); // holds derived specialized ACs
        ArrayList<DocSegment> relatedSegments=new ArrayList<DocSegment>(8);
        acl.clear();

        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];

            // decrement remaining token counts of open attributes, create new "just ended" list
            jel.clear();
            for(int j=0;j<openAtts.size();j++) {
                LabelRecord openAtt=openAtts.get(j);
                if(--openAtt.cnt == 0) {
                    openAtts.remove(j);
                    j--;
                    jel.add((AC) openAtt.label);
                    openAtt.disposeInstance();
                }
            }
            // open new attribute candidates
            for(int j=0;ta.acs!=null && j<ta.acs.length;j++) {
                LabelRecord openAtt=LabelRecord.getInstance();
                AC ac=ta.acs[j];
                openAtt.label=(Object) ac;
                openAtt.cnt=ac.len;
                openAtts.add(openAtt);
            }

            // process matched context patterns
            if(ta.matchStarts==null)
                continue;
            for(int j=0;j<ta.matchStarts.size();j++) {
                PatMatch pm=ta.matchStarts.get(j);
                if(!TokenPattern.usePattern(pm.pat, patternFilter))
                    continue;
                switch(pm.pat.type) {
                case TokenPattern.PAT_CTX_L: {
                    //String dbg=pm.toString(doc);
                    //if(dbg.endsWith("(bez DPH :)"))
                    //    dbg="";
                    
                    // boost all relevant ACs or derive specialized ACs in all segments subordinated to the context pattern match area
                    relatedSegments.clear();
                    int segCnt;
                    if((pm.pat.flags & TokenPattern.FLAG_LINEAR)!=0) {
                        relatedSegments.add(new DocSegment(pm.startIdx+pm.len, -1));
                        segCnt=1;
                    }else {
                        segCnt=doc.getRelatedSegments(pm.startIdx, pm.len, relatedSegments);
                    }
                    int updACCnt=0;
                    for(int si=0;si<segCnt;si++) {
                        DocSegment seg=relatedSegments.get(si);
                        TokenAnnot segStartTok=doc.tokens[seg.startIdx];
                        for(int k=0;segStartTok.acs!=null && k<segStartTok.acs.length;k++) {
                            AC ac=segStartTok.acs[k];
                            updACCnt+= ac.applyContextPattern(pm, doc, derivedList);
                        }
                    }
                    int updACCntFol=0;
                    if(updACCnt==0 && (pm.startIdx+pm.len)<doc.tokens.length) {
                        // try the immediate follower segment:
                        TokenAnnot segStartTok=doc.tokens[pm.startIdx+pm.len];
                        for(int k=0;segStartTok.acs!=null && k<segStartTok.acs.length;k++) {
                            AC ac=segStartTok.acs[k];
                            updACCntFol+= ac.applyContextPattern(pm, doc, derivedList);
                        }
                    }
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Boosted "+updACCntFol+"/"+updACCnt+" acs in the following/"+segCnt+" segments related to "+pm.toString(doc));
                    updACCnt+=updACCntFol;
                    
                    // create new ACs where they are missing and where evidence is sufficient
                    // also create one new fitting AC if segment given exactly (e.g. TD in table) and a fitting AC does not yet exist
                    for(int si=0;si<segCnt;si++) {
                        DocSegment seg=relatedSegments.get(si);
                        if(updACCnt==0 || seg.len>0) {
                            int rc1=induceACsFromContext(pm, doc, seg.startIdx, seg.len, acl);
                            if(rc1>0) {
                                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created "+rc1+" followers of "+pm.toString(doc));
                                doc.addAC(acl);
                                acl.clear();
                                rc+=rc1;
                            }
                        }
                    }
                    relatedSegments.clear();
                    break;
                }
                case TokenPattern.PAT_CTX_R: {
                    // boost the correct ACs that have just ended, or derive specialized ACs
                    int n=jel.size();
                    int cnt=0;
                    for(int k=0;k<n;k++) {
                        AC ac=(AC) jel.get(k);
                        cnt+=ac.applyContextPattern(pm, doc, derivedList);
                    }
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Boosted "+cnt+" predecessors of "+pm.toString(doc));
                    // create new ACs if no others were found and evidence is sufficient
                    if(cnt==0 && i>0) {
                        int rc1=induceACsFromContext(pm, doc, i-1, -1, acl);
                        if(rc1>0) {
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created "+rc1+" predecessors of "+pm.toString(doc));                            
                            doc.addAC(acl);
                            acl.clear();
                            rc+=rc1;
                        }
                    }
                    break;
                }
                case TokenPattern.PAT_CTX_LR: {
                    // boost the middle
                    PatSubMatch valMatch=null;
                    if(pm.children!=null) {
                        for(int k=0;k<pm.children.length;k++) {
                            Annotable childMatch=pm.children[k];
                            //if(childMatch.getType()==TokenPattern.PAT_VAL) {
                            if(childMatch instanceof PatSubMatch) {
                                valMatch=(PatSubMatch) childMatch;
                                break;
                            }
                        }
                    }
                    if(valMatch==null) {
                        Logger.LOG(Logger.ERR, "Could not find $ value placeholder in LR context pattern match "+pm.pat);
                        break;
                    }
                    // apply to all ACs which already exist
                    int cnt=0;
                    int si=valMatch.getStartIdx();
                    TokenAnnot ta2=doc.tokens[si];
                    for(int k=0;ta2.acs!=null && k<ta2.acs.length;k++) {
                        if(ta2.acs[k].len==valMatch.len) {
                            cnt+= ta2.acs[k].applyContextPattern(pm, doc, derivedList);
                        }
                    }
                    // if no AC exists that would fit into this LR pattern match, create a new AC if we have enough evidence 
                    if(cnt==0 && pm.pat.evidence.prec > pm.pat.getAttributeDef().pruneProb) {
                        // AC ac=new AC(pm.pat.getAttributeDef(), ta2, valMatch.len, false, pm.pat.getAttributeDef().getDefaultEvidenceValues(), doc);
                        AC ac=new AC(pm.pat.getAttributeDef(), ta2, valMatch.len, false, null, doc);
                        if(ac.value==null) {
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"AC "+ac+" was not created due to unsatisfied constraints");
                        }else {
                            ac.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                            doc.addAC(ac);
                            rc++;
                        }
                    }
                    break;
                }
                }
            }
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC, "Adding total "+derivedList.size()+" derived ACs");
        if(derivedList.size()>0) {
            doc.addAC(derivedList);
            derivedList.clear();
        }
        return rc;
    }

    /** Attempts to create new ACs based on a match of a left or right context pattern.
     *  Uses the corresponding AttributeDef's length distribution to create a set 
     *  of ACs having their conditional probability above the AttributeDef's pruneProb threshold. 
     *  The evidence used for the new ACs is the context pattern and the length distribution. 
     *  New ACs will be created within the area given by segStartIdx and segLen. */
    protected int induceACsFromContext(PatMatch pm, Document doc, int segBorderIdx, int segLen, List<AC> newACs) {
        AttributeDef ad=pm.pat.getAttributeDef();
        if(pm.pat.evidence.prec <= ad.pruneProb || 
         ((ad.maxLength-ad.minLength>100) && (ad.cardDist==null || ad.cardDist.getType()==Distribution.TYPE_MINMAX)) ) {
            return 0;
        }
        int cnt=0;
        int maxLen=(segLen==-1)? ad.maxLength: Math.min(ad.maxLength, segLen);
        for(int len=ad.minLength;len<=maxLen;len++) {
            int i=(pm.pat.type==TokenPattern.PAT_CTX_L)? segBorderIdx: (segBorderIdx-len+1); // segBorderIdx+pm.len-1
            if(i<0 || i>=doc.tokens.length || i+len>doc.tokens.length)
                break;
            TokenAnnot ta=doc.tokens[i];
            // TODO: maybe check changes in tokens' containers and break e.g. on new div
            // this is however also addressed by default patterns which may lower the new AC's condProb
            
            // AC ac=new AC(pm.pat.getAttributeDef(), ta, len, false, pm.pat.getAttributeDef().getDefaultEvidenceValues(), doc);
            // check for duplicates
            AC oldAC=null;
            if(ta.acs!=null) {
                for(int k=0;k<ta.acs.length;k++) {
                    AC ac1=ta.acs[k];
                    if(ac1.getAttribute()==ad && ac1.len==len) {
                        oldAC=ac1;
                        break;
                    }
                }
            }
            AC ac = (oldAC==null)? new AC(ad, ta, len, false, null, doc): oldAC;
            if(ac.value==null) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Context-"+((oldAC==null)?"induced":"boosted")+" AC was not created due to unsatisfied constraints: "+ac);
            }else {
                ac.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                // check if the AC is worth it
                applyDocumentPatterns(ac);
                ac.condProb();
                if(ac.isHopeless()) {
                    if(log.IFLG(Logger.TRC)) Logger.LOG(Logger.TRC,"Pruning context-"+((oldAC==null)?"induced":"boosted")+" AC "+ac+" since its prob is hopeless="+ac.getProb());
                    if(oldAC!=null) {
                        ta.removeAC(ac);
                    }
                }else {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Context-"+((oldAC==null)?"induced":"boosted")+" AC "+ac);
                    if(oldAC==null) {
                        newACs.add(ac);
                        cnt++;
                    }
                }
            }
        }
        return cnt;
    }

    /** Recomputes the conditional probabilities for each AC in doc; 
     *  prunes all ACs below AttributeDef's pruneProb threshold. */
    protected int pruneRecomputeACs(Document doc) {
        // recompute AC probs
        int acCnt=0;
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];
            if(ta.acs==null)
                continue;
            int pruned=0;
            for(int j=0;j<ta.acs.length;j++) {
                AC ac=ta.acs[j];
                // just to be sure everything is set right: 
                applyDocumentPatterns(ac);
                ac.condProb();
                if(ac.isHopeless()) {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Pruning AC "+ac.toStringIncEvidence(1)+" since its prob is hopeless");
                    if(ac.getAttribute().logLevel>1 && log.IFLG(Logger.USR)) log.LG(Logger.USR, "~ "+ac.toStringIncEvidence(1));
                    ta.acs[j]=null;
                    pruned++;
                }else {
                    if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"AC "+ac.toStringIncEvidence(1));
                    if(ac.getAttribute().logLevel>0 && log.IFLG(Logger.USR)) log.LG(Logger.USR, "* "+ac.toStringIncEvidence(1));
                }
                if(log.IFLG(Logger.INF) && ta.semAnnots!=null) {
                    for(SemAnnot sa: ta.semAnnots) {
                        if(sa.type==SemAnnot.TYPE_AV && sa.getLength()==ac.len) {
                            if(sa.origAnnot instanceof medieq.iet.model.Annotation) {
                                ((medieq.iet.model.Annotation) sa.origAnnot).addDebugInfo(ac.toStringIncEvidence(1));
                                //log.LG(Logger.USR,ac.toStringIncEvidence(1));
                            }
                        }
                    }
                }
            }
            if(pruned>0) {
                if(pruned==ta.acs.length) {
                    ta.acs=null;
                }else {
                    AC[] newACs=new AC[ta.acs.length-pruned];
                    int k=0;
                    for(int j=0;j<ta.acs.length;j++) {
                        if(ta.acs[j]!=null) {
                            newACs[k]=ta.acs[j];
                            k++;
                        }
                    }
                    ta.acs=newACs;
                }
            }
            if(ta.acs!=null)
                acCnt+=ta.acs.length;
        }
        return acCnt;
    }
    
    void applyDocumentPatterns(AC ac) {
        Document doc=ac.doc;
        // value patterns
        TokenAnnot sta=ac.getStartToken();
        if(sta.matchStarts!=null) {
            for(int i=0;i<sta.matchStarts.size();i++) {
                PatMatch pm=sta.matchStarts.get(i);
                if(pm.pat.type!=TokenPattern.PAT_VAL || !(pm.getModelElement() instanceof AttributeDef))
                    continue;
                AttributeDef pad=(AttributeDef) pm.getModelElement();
                if(pm.len==ac.len && (pad==ac.getAttribute() || ac.getAttribute().isDescendantOf(pad))) {
                    ac.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                }
            }
        }
        // ctx patterns
        if(sta.idx>0) {
            TokenAnnot ta=doc.tokens[sta.idx-1];
            if(ta.matchEnds!=null) {
                for(int i=0;i<ta.matchEnds.size();i++) {
                    PatMatch pm=ta.matchEnds.get(i);
                    if(pm.pat.type!=TokenPattern.PAT_CTX_L || !(pm.getModelElement() instanceof AttributeDef))
                        continue;
                    AttributeDef pad=(AttributeDef) pm.getModelElement();
                    if((pad==ac.getAttribute() || ac.getAttribute().isDescendantOf(pad))) {
                        ac.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                    }
                }
            }
        }
        TokenAnnot eta=ac.getEndToken();
        if(eta.idx+1<doc.tokens.length) {
            TokenAnnot ta=doc.tokens[eta.idx+1];
            if(ta.matchStarts!=null) {
                for(int i=0;i<ta.matchStarts.size();i++) {
                    PatMatch pm=ta.matchStarts.get(i);
                    if(pm.pat.type!=TokenPattern.PAT_CTX_R || !(pm.getModelElement() instanceof AttributeDef))
                        continue;
                    AttributeDef pad=(AttributeDef) pm.getModelElement();
                    if((pad==ac.getAttribute() || ac.getAttribute().isDescendantOf(pad))) {
                        ac.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                    }
                }
            }
        }
    }
}

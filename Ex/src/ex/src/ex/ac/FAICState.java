// $Id: FAICState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

import java.util.List;

import ex.reader.TokenAnnot;
import ex.model.ClassDef;
import uep.util.Logger;
import ex.parser.TrellisRecord;
import ex.parser.ICBase;

/** Represents a single valid Instance Candidate (IC) of a given ClassDef */
public class FAICState extends FAState {
    ClassDef cd;

    public FAICState(ClassDef cd, Object data) {
        super(ST_IC,data);
        this.cd=cd;
    }

    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        // if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,startIdx+"-"+prev.pathLen);
        if(startIdx+prev.pathLen>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"ICState partial match continues behind doc");
            return 0;
        }
        int matchCnt=0;
        // e.g. tokens[0+0] for the start state before document start
        TokenAnnot ta=tokens[startIdx+prev.pathLen];
        TrellisRecord[] lr=(TrellisRecord[]) ta.userData;
        if(lr==null)
            return 0;
        TrellisRecord rec=lr[1]; // take the up right spoke from token, iterate over its ICs  
        while(rec!=null) {
            for(ICBase ic: rec) {
                if(ic.clsDef==cd && ic.isValidCached()==ICBase.ICVALID_TRUE) {
                    if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"ICState matched class="+cd.name+" inst="+ic.toString());
                    int icLen=ic.getEndIdx()-ta.idx+1;
                    // TNode node=new TNode(this,prev,icLen);
                    TNode node=PatMatcher.newNode(this, prev, icLen);
                    node.annots=new TAnnots(node.annots, ic);
                    newNodes.add(node);
                    matchCnt++;
                }
            }
            rec=rec.getUpRight();
        }
        if(matchCnt==0) {
            if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"ICState mismatch class="+cd.name);
            return 0;
        }
        return matchCnt;
    }
}

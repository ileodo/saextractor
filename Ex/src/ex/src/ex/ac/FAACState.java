// $Id: FAACState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

import java.util.List;
import ex.reader.TokenAnnot;
import ex.model.AttributeDef;
import uep.util.Logger;

/** Represents a single Attribute Candidate (AC) of a given Attribute type */
public class FAACState extends FAState {
    AttributeDef ad;
    
    public FAACState(AttributeDef ad, Object data) {
        super(ST_AC,data);
        this.ad=ad;
    }

    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        // if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,startIdx+"-"+prev.pathLen);
        if(startIdx+prev.pathLen>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"ACState partial match continues behind doc");
            return 0;
        }
        int matchCnt=0;
        // e.g. tokens[0+0] for the start state before document start
        TokenAnnot ta=tokens[startIdx+prev.pathLen];
        if(ta.acs==null)
            return 0;
        for(int i=0;i<ta.acs.length;i++) {
            AC ac=ta.acs[i];
            if(ac.getAttribute()==ad || ac.getAttribute().isDescendantOf(ad)) {
                // only match ACs with reasonable scores
                if(!ac.isHopeless()) {
                    if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"ACState matched att="+ad.name+" text="+ac.getText(new StringBuffer(32)));
                    int acLen=ac.getEndIdx()-ta.idx+1;
                    // TNode node=new TNode(this,prev,acLen);
                    TNode node=PatMatcher.newNode(this, prev, acLen);
                    node.annots=new TAnnots(node.annots, ac);
                    newNodes.add(node);
                    matchCnt++;
                }else {
                    if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"ACState does not match hopeless ac="+ac);
                }
            }
        }
        if(matchCnt==0) {
            if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"ACState mismatch att="+ad.name);
            return 0;
        }
        return matchCnt;
    }
}

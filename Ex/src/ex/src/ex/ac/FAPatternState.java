// $Id: FAPatternState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * State containing a sub-pattern
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

import ex.reader.TokenAnnot;
import uep.util.Logger;

/** Represents an instance of a sub-pattern */
public class FAPatternState extends FAState {
    public TokenPattern sub; // pattern defined independently, could be replaced by only its FA (now kept for debugging)

    public short patType; // what is the subpattern's role in the containing pattern
    public static short PS_GEN=0; // generic, no special meaning
    public static short PS_VAL=1; // pattern for attribute value placeholder; used in PAT_CTX_LR patterns like "size: $ cm"
    // if attribute's dimension >1, then PS_VAL+1 denotes the pattern for the 1st dimension etc., e.g. "size: $1 x $2 cm"

    public FAPatternState(TokenPattern subPattern) {
        this(subPattern,PS_GEN,null);
    }

    public FAPatternState(TokenPattern subPattern, short patType, Object d) {
        super(ST_PATTERN,d);
        sub=subPattern;
        this.patType=patType;
    }

    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        PatMatcher mat=PatMatcher.getMatcher();
        int offset=startIdx+prev.pathLen;
        int matchCnt=mat.match(tokens, offset, sub);
        if(matchCnt==0) {
            if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"PatternState mismatch: token="+((offset<tokens.length)? tokens[offset].ti.token: "_EOF_")+", pat="+sub.id);
        }else {
            if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"PatternState matched: "+matchCnt+"x, token="+tokens[offset].ti.token+", pat="+sub.id);
            // for(int i=0;i<matchCnt;i++) {
            //     TNode subFin=(TNode) mat.finalNodes.get(i);
            for(TNode subFin: mat.finalNodes) {
                if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"span="+subFin.pathLen+", token="+tokens[offset].ti.token+",pat="+sub.id);
                if(!sub.isMatchValid(tokens, startIdx, subFin)) {
                    matchCnt--;
                    continue;
                }
              
//              TNode node=subFin; // reuse each final TNode in the containing tree 
                // TNode node=new TNode(this,prev,subFin.pathLen);
                TNode node=PatMatcher.newNode(this, prev, subFin.pathLen);
                
//              node.constraints=(prev.constraints!=null)? new TConstraints(prev.constraints): null;
                // add or create annotations in this path if needed
                if(patType>=PS_VAL) {
                    short dim=(short) (patType-PS_VAL);
                    node.annots=new TAnnots(node.annots, new PatSubMatch(sub, offset, subFin.pathLen, dim));
                }
//              node.state=this;
//              node.pathLen+=prev.pathLen;
//              node.prevNode=null; // only used if this and prev are null; we inherit constraints and annots all the way to the final node
                newNodes.add(node);
            }
        }
        PatMatcher.disposeMatcher(mat);
        return matchCnt;
    }
}

// $Id: FASimpleTokenState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

import java.util.Comparator;
import java.util.List;

import uep.util.Logger;
import ex.features.FM;
import ex.reader.TokenAnnot;
import ex.train.TokenInfo;
import ex.train.Vocab;

public class FASimpleTokenState extends FAState {
    public static final Comparator<FASimpleTokenState> comparator=new FASTStateComparator();
    
    int tokenId;
    
    public FASimpleTokenState(int tokenId) {
        super(ST_TOKEN, tokenId);
        this.tokenId=tokenId;
        if(Logger.IFLOG(Logger.TRC)) {
            Vocab voc=FM.getFMInstance().getKB().vocab;
            if(voc!=null) {
                TokenInfo ti=voc.get(tokenId);
                if(ti!=null) {
                    data=tokenId+"/"+ti.token;
                }else {
                    Logger.LOG(Logger.ERR,"Token not found in vocab for "+tokenId);
                }
            }
        }
    }
    
    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        // PatMatcher.log.LG(Logger.TRC,startIdx+"-"+prev.pathLen);
        if(startIdx+prev.pathLen>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"SimpleTokenState partial match continues beyond doc");
            return 0;
        }
        TokenAnnot ta=tokens[startIdx+prev.pathLen];
        int tid=ta.ti.getTokenIdToMatch(matchFlags);
        boolean pass=(tid==tokenId);
        if(neg)
            pass=!pass;
        if(pass) {
            newNodes.add(PatMatcher.newNode(this, prev, 1));
            // if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"SimpleTokenState "+data+" matched at "+(startIdx+prev.pathLen)+": "+ta.token);
        }else {
            if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"SimpleTokenState "+data+" did not match at "+(startIdx+prev.pathLen)+": "+ta.token);
        }
        return pass? 1:0;
    }
    
    public boolean equals(FASimpleTokenState o) {
        return o.tokenId==tokenId;
    }
}

class FASTStateComparator implements Comparator<FASimpleTokenState> {
    public int compare(FASimpleTokenState o1, FASimpleTokenState o2) {
        return o1.tokenId-o2.tokenId;
    }
}

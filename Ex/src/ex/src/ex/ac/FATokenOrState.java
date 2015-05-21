// $Id: FATokenOrState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * State accepting 1 token (OR on _values_ of each included feature, but configurable AND/OR on whole included features)
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ex.reader.TokenAnnot;
import ex.train.TokenInfo;
import ex.util.CaseUtil;
import ex.features.FM;
import uep.util.Logger;

public class FATokenOrState extends FAState {
    int[] fids; // [F1, F2]
    int[] vals; // [F1.V1, F1.V2, F2.V1]
    int[] lastIdxs; // [1,2]
    boolean feor; // OR on whole features

    public FATokenOrState() {
        this(null,null,null,true,null);
    }

    public FATokenOrState(int[] f, int[] v, int[] o, boolean featureOr) {
        this(f,v,o,featureOr,null);
    }

    public FATokenOrState(int[] f, int[] v, int[] o, boolean featureOr, Object d) {
        super(ST_TOKEN,d);
        fids=f;
        vals=v;
        lastIdxs=o;
        feor=featureOr;
    }

    //public static int cntr=0;
    
    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        if(startIdx+prev.pathLen>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"TokenOrState partial match continues behind doc");
            return 0;
        }
        //if((cntr)%1000==0) {
        //    System.err.print("\rO: "+(++cntr));
        //}
        TokenAnnot ta=tokens[startIdx+prev.pathLen];
        TokenInfo ti=ta.ti;
        int passed=0;
        boolean ok=true;
outer:  for(int i=0;i<fids.length;i++) {
            int tokFVal=ti.intVals.get(fids[i]);
            // make lemma ID the same as my ID if missing, same for LC form
            if(tokFVal==-1) {
                switch(fids[i]) {
                case FM.TOKEN_LC:
                case FM.TOKEN_LEMMA:
                    tokFVal=ti.intVals.get(FM.TOKEN_ID);
                    break;
                }
            }
            int prevIdx;
            int lastIdx;
            if(lastIdxs!=null) {
                prevIdx=(i>0)? (lastIdxs[i-1]+1): 0;
                lastIdx=lastIdxs[i];
            }else {
                prevIdx=i;
                lastIdx=i;
            }
            int j=prevIdx;
            for( ;j<=lastIdx;j++) {
                if(tokFVal==vals[j]) {
                    passed++;
                    if(feor)
                        break outer; // OR: one matched feature is enough
                    else
                        break;
                }
            }
            if(!feor && j>lastIdx) { // AND: all features must be matched
                //if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"TokenOrState(FAND) misses");
                ok=false;
                break;
            }
        }
        if(ok && passed==0 && fids.length>0) {
            //if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"TokenOrState(FOR) misses");
            ok=false;
        }
        if(ok && data!=null && data instanceof Pattern) {
            Pattern re=(Pattern) data;
            String stok=ta.token;
            // if((matchFlags & TokenPattern.MATCH_IGNORE_ACCENT) != 0) {
            if((re.flags() & TokenPattern.MATCH_IGNORE_ACCENT) != 0) {
                stok=CaseUtil.removeAccents(stok);
            }
            Matcher m=((Pattern)data).matcher(stok);
            if(!m.find()) {
                //if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"TokenOrState(RE) misses");
                ok=false;
            }
        }
        if(neg)
            ok=!ok;
        // newNodes.add(new TNode(this,prev,1));
        if(ok) {
            newNodes.add(PatMatcher.newNode(this, prev, 1));
            //if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"TokenOrState matched");
            return 1;
        }
        return 0;
    }
}

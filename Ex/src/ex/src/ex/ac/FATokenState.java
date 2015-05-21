// $Id: FATokenState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * State accepting 1 token (AND on features)
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ex.features.FM;
import ex.features.IntFeature;
import ex.features.TokenF;
import ex.reader.TokenAnnot;
import ex.train.TokenInfo;
import ex.util.CaseUtil;
import uep.util.Logger;

/** Represents a single specific token, with a logical AND over feature constraints */
public class FATokenState extends FAState {
    int[] fids; // feature ids, e.g.  [TokenTypeF,TokenLCF]
    int[] vals; // feature vals, e.g. [ALPHANUM,UC]

    public FATokenState() {
        this(null,null,null);
    }

    public FATokenState(int[] f, int[] v) {
        this(f,v,null);
    }

    public FATokenState(int[] f, int[] v, Object d) {
        super(ST_TOKEN,d);
        fids=f;
        vals=v;
    }

    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        // PatMatcher.log.LG(Logger.TRC,startIdx+"-"+prev.pathLen);
        if(startIdx+prev.pathLen>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"TokenState partial match continues beyond doc");
            return 0;
        }
        TokenAnnot ta=tokens[startIdx+prev.pathLen];
        TokenInfo ti=ta.ti; // e.g. tokens[0+0] for the start state before document start
        boolean pass=true;
        for(int i=0;i<fids.length;i++) {
            int val=ti.intVals.get(fids[i]);
            if(val!=vals[i]) {
                if(PatMatcher.log.IFLG(Logger.MML)) {
                    TokenF f=FM.getFMInstance().getTokenFeature(fids[i]);
                    PatMatcher.log.LG(Logger.MML,"TokenState mismatch f"+fids[i]+"("+f.name+"): val="+((IntFeature)f).toString(val)+",exp="+((IntFeature)f).toString(vals[i]));
                }
                pass=false;
                break;
            }
        }
        if(pass && data!=null && data instanceof Pattern) {
            Pattern re=(Pattern) data;
            String stok=ta.token;
            // if((matchFlags & TokenPattern.MATCH_IGNORE_ACCENT) != 0) {
            if((re.flags() & TokenPattern.MATCH_IGNORE_ACCENT) != 0) {
                stok=CaseUtil.removeAccents(stok);
            }
            Matcher m=((Pattern)data).matcher(stok);
            if(!m.find()) {
                if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"TokenState(RE) misses");
                pass=false;
            }
        }
        if(neg)
            pass=!pass;
        if(pass) {
            // newNodes.add(new TNode(this,prev,1));
            newNodes.add(PatMatcher.newNode(this, prev, 1));
            if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"TokenState "+data+" matched at "+(startIdx+prev.pathLen)+": "+ta.token);
        }
        return pass? 1:0;
    }
}

// $Id: TokenListF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import ex.train.*;

public class TokenListF extends TokenF implements IntFeature {
    public static final int MISS=0;
    public static final int HIT=1;
    public static String[] valueNames={
        "MISS","HIT"
    };
    Vocab vocab;

    public TokenListF(int featureId, String featureName, Vocab voc) {
        super(featureId, featureName, VAL_BOOLEAN);
        vocab=voc;
        valueCnt=valueNames.length;	
    }

    public String toString(int val) {
        return valueNames[val]; // 0 or 1
    }

    public int valueOf(String tok) { 
        TokenInfo ti=vocab.get(tok);
        if(ti==null)
            return MISS; // the token itself is not found - then it cannot be in any list
        return ti.intVals.get(id); // this feature stores 0 or 1 
    }
}

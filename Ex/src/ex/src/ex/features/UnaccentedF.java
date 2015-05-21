// $Id: UnaccentedF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import ex.train.*;
import ex.util.CaseUtil;

public class UnaccentedF extends TokenF implements IntFeature {
    public static final int UNK_UNACCTOKEN=0;
    public static String[] valueNames={
        "UNK_UNACCTOKEN"
    };
    private static UnaccentedF singleton;
    public static UnaccentedF getSingleton() { return singleton; }
    public Vocab vocab;

    protected UnaccentedF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=valueNames.length;
        singleton=this;
    }

    public String toString(int val) {
        String s=String.valueOf(val);
        if(val!=UNK_UNACCTOKEN) {
            TokenInfo tok=vocab.get(val);
            if(tok!=null)
                s+= "."+tok.token;
        }
        return s;
    }

    public int valueOf(String tok) { 
        TokenInfo ti=vocab.get(tok);
        if(ti==null)
            return UNK_UNACCTOKEN;
        return ti.intVals.get(id);
    }

    public static String apply(String tok) {
        return CaseUtil.removeAccents(tok);
    }
}

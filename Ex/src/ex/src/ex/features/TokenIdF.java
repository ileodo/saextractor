// $Id: TokenIdF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import ex.train.*;

public class TokenIdF extends TokenF implements IntFeature {
    public static final int UNK_TOKEN=0;
    public static String[] valueNames={
        "UNK_TOKEN"
    };
    private static TokenIdF singleton;
    public static TokenIdF getSingleton() { return singleton; }
    public Vocab vocab;

    protected TokenIdF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=valueNames.length;
        singleton=this;
    }

    public String toString(int val) {
        // return String.valueOf(val);
        String rc=val+".";
        if(val==UNK_TOKEN || vocab==null) {
            rc+=valueNames[UNK_TOKEN];
        }else {
            TokenInfo tok=vocab.get(val);
            if(tok==null)
                rc+=valueNames[UNK_TOKEN];
            else
                rc+=tok.token;
        }
        return rc;
    }

    public int valueOf(String tok) { 
        TokenInfo ti=vocab.get(tok);
        if(ti==null)
            return UNK_TOKEN;
        return ti.intVals.get(id);
    }
}

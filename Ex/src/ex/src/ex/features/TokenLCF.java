// $Id: TokenLCF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import ex.train.*;

public class TokenLCF extends TokenF implements IntFeature {
    public static final int UNK_LCTOKEN=0;
    public static String[] valueNames={
        "UNK_LCTOKEN"
    };
    private static TokenLCF singleton;
    public static TokenLCF getSingleton() { return singleton; }
    public Vocab vocab;

    protected TokenLCF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=valueNames.length;
        singleton=this;
    }

    public String toString(int val) {
        String s=String.valueOf(val);
        if(val!=UNK_LCTOKEN) {
            TokenInfo tok=vocab.get(val);
            if(tok!=null)
                s+= "."+tok.token;
        }
        return s;
        /*
        if(val==UNK_LCTOKEN)
            return val+"."+valueNames[UNK_LCTOKEN];
        TokenInfo tok=vocab.get(val);
        if(tok==null)
            return val+"."+valueNames[UNK_LCTOKEN];
        return val+"."+tok.token;
         */
    }

    public int valueOf(String tok) { 
        TokenInfo ti=vocab.get(tok);
        if(ti==null)
            return UNK_LCTOKEN;
        return ti.intVals.get(id);
    }

    public static String apply(String tok) {
        return tok.toLowerCase();
    }

    /* returns IDs of a lowercased phrase if it differs */
    public static int[] toLC(AbstractToken[] orig, int start, int len) {
        int lcIds[]=null;
        int end=start+len-1;
        int i;
        for(i=start;i<=end;i++) {
            int id=orig[i].getLCId();
            if(id>0) {
                lcIds=new int[len];
                for(int j=start;j<i;j++)
                    lcIds[j-start]=orig[j].getTokenId();
                lcIds[i-start]=id;
                break;
            }
        }
        if(lcIds!=null) {
            for(i++;i<=end;i++) {
                int id=orig[i].getLCId();
                if(id<=0)
                    id=orig[i].getTokenId();
                lcIds[i-start]=id;
            }
        }
        return lcIds;
    }

}

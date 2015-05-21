package ex.features;

import ex.train.*;

public class PhraseIdF extends PhraseF implements IntFeature {
    public static final int UNK_PHRASE=0;
    public static String[] valueNames={
        "UNK_PHRASE"
    };
    private static PhraseIdF singleton;
    public static PhraseIdF getSingleton() { return singleton; }
    public PhraseBook book;

    protected PhraseIdF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=valueNames.length;
        singleton=this;
    }

    public String toString(int val) {
        return String.valueOf(val);
        /*
        if(val==UNK_PHRASE)
            return val+"."+valueNames[UNK_PHRASE];
        PhraseInfo pi=book.get(val);
        if(pi==null)
            return val+"."+valueNames[UNK_PHRASE];
        return val+"."+pi.toPhrase();
         */
    }

    public int valueOf(TokenInfo[] toks) { 
        NBestResult nbr=new NBestResult(1);
        int rc=book.get(toks, nbr);
        if(nbr.length==0)
            return UNK_PHRASE;
        return ((PhraseInfo) nbr.items[0]).intValues[id];
    }
}

package ex.features;

import ex.train.*;

public class PhraseLemmaF extends PhraseF implements IntFeature {
    public static final int UNK_LPHRASE=0;
    public static String[] valueNames={
        "UNK_LPHRASE"
    };
    private static PhraseLemmaF singleton;
    public static PhraseLemmaF getSingleton() { return singleton; }
    public PhraseBook book;

    protected PhraseLemmaF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=valueNames.length;
        singleton=this;
    }

    public String toString(int val) {
        return toString(val, book);
    }

    public String toString(int val, PhraseBook altBook) {
        String s=String.valueOf(val);
        if(val!=UNK_LPHRASE) {
            PhraseInfo pi=altBook.get(val);
            if(pi!=null)
                s+= "."+pi.toPhrase();
        }
        return s;
        /*
        if(val==UNK_LPHRASE)
           return val+"."+valueNames[UNK_LPHRASE];
        PhraseInfo pi=book.get(val);
        if(pi==null)
           return val+"."+valueNames[UNK_LPHRASE];
        return val+"."+pi.toPhrase();
        */
    }

    public int valueOf(TokenInfo[] toks) { 
        return valueOf(toks, book);
    }

    public int valueOf(TokenInfo[] toks, PhraseBook altBook) { 
        NBestResult nbr=new NBestResult(1);
        int rc=altBook.get(toks, nbr);
        if(nbr.length==0)
            return UNK_LPHRASE;
        return ((PhraseInfo) nbr.items[0]).intValues[id];
    }
}

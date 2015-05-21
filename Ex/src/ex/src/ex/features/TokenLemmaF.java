// $Id: TokenLemmaF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.io.*;
import java.util.regex.*;
import uep.util.*;
import ex.util.MorphoClient;
import ex.train.*;

public class TokenLemmaF extends TokenF implements IntFeature {
    public static final int UNK_LEMMA=0;
    public static String[] valueNames={
        "UNK_LEMMA"
    };
    private static TokenLemmaF singleton;
    public static TokenLemmaF getSingleton() { return singleton; }
    public Vocab vocab;
    protected MorphoClient lemr;

    protected TokenLemmaF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=valueNames.length;
        lemr=new MorphoClient();
        try {
            lemr.connect(); // connect using settings from config
        }catch(IOException ex) {
            Logger.LOG(Logger.WRN,"Cannot connect to morphology server: "+ex.getMessage());
        }catch(ConfigException ex) {
            Logger.LOGERR("Lemmatizer not configured: "+ex.getMessage());
        }
        singleton=this;
    }

    public String toString(int val) {
        String s=String.valueOf(val);
        if(val!=UNK_LEMMA) {
            TokenInfo tok=vocab.get(val);
            if(tok!=null)
                s+= "."+tok.token;
        }
        return s;
        /*
        if(val==UNK_LEMMA)
            return val+"."+valueNames[UNK_LEMMA];
        TokenInfo tok=vocab.get(val);
        if(tok==null)
            return val+"."+valueNames[UNK_LEMMA];
        return val+"."+tok.token;
        */
    }

    public int valueOf(String tok) { 
        TokenInfo ti=vocab.get(tok);
        if(ti==null)
            return UNK_LEMMA; // the token for which we want the lemma is unknown - should not happen
        // we assume lemma id is in Vocab,
        // or should we get lemma from lemmatizer instead?
        return ti.intVals.get(id);
    }

    private static Pattern badLetters=Pattern.compile("[^"+
            "\u0041-\u005a"+
            "\u0061-\u007a"+
            "\u00c0-\u00d6"+
            "\u00d8-\u00f6"+
            "\u00f8-\u00ff"+
            "\u0100-\u1fff"+
            "'"+
    "]");

    public String apply(String tok) {
        if(!lemr.isConnected())
            return null;
        Matcher mat=badLetters.matcher(tok);
        if(mat.find())
            return null;
        String lemma=lemr.getLemma(tok);
        if(lemma==null || lemma.length()==0)
            return null;
        return lemma.toLowerCase();
    }

    public void deinit() {
        if(lemr!=null)
            lemr.disconnect();
    }

    /* returns IDs of a lemmatized phrase if it differs */
    public static int[] toLemma(AbstractToken[] orig, int start, int len) {
        int lemmaIds[]=null;
        int end=start+len-1;
        int i;
        for(i=start;i<=end;i++) {
            int id=orig[i].getLemmaId();
            if(id>0) {
                lemmaIds=new int[len];
                for(int j=start;j<i;j++)
                    lemmaIds[j-start]=orig[j].getTokenId();
                lemmaIds[i-start]=id;
                break;
            }
        }
        if(lemmaIds!=null) {
            for(i++;i<=end;i++) {
                int id=orig[i].getLemmaId();
                if(id<=0)
                    id=orig[i].getTokenId();
                lemmaIds[i-start]=id;
            }
        }
        return lemmaIds;
    }
}

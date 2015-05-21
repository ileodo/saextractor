// $Id: WordOfPhraseF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.ArrayList;
import java.util.List;

/** Feature to convert phrase-level features to word-level features. */
public class WordOfPhraseF extends TokenF {
    public static String NONE="N";
    public static String BEGIN="B";
    public static String INNER="I";
    
    public static int NONEVAL=0;
    public static int BEGINVAL=1;
    public static int INNERVAL=2;
    
    protected static List<String> valueList;
    
    static {
        valueList=new ArrayList<String>(4);
        valueList.add(NONE);
        valueList.add(BEGIN);
        valueList.add(INNER);
    }
    
    PhraseF phraseF;
    
    public WordOfPhraseF(String featureName, PhraseF sourceFeature) {
        super(FM.getFMInstance().getNextFeatureId(), featureName, VAL_ENUM);
        phraseF = sourceFeature;
        valueCnt=3;
    }
    
    public PhraseF getPhraseFeature() {
        return phraseF;
    }
    
    public List<String> getValues() {
        return valueList;
    }
    
    public String toString(int val) {
        return valueList.get(val);
    }

    public int fromString(String name) {
        if(name.equals(NONE))
            return NONEVAL;
        if(name.equals(BEGIN))
            return BEGINVAL;
        if(name.equals(INNER))
            return INNERVAL;
        return -1;
    }
}

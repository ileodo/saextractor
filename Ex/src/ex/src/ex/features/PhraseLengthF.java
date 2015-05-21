// $Id: PhraseLengthF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import ex.train.TokenInfo;

public class PhraseLengthF extends PhraseF implements IntFeature {
    private static PhraseLengthF singleton;
    public static PhraseLengthF getSingleton() { return singleton; }
    protected int maxValue;
    protected int minValue;
    
    protected PhraseLengthF(int featureId, String featureName) {
        super(featureId, featureName, VAL_INT);
        minValue=1;
        maxValue=Integer.MAX_VALUE;
        valueCnt=maxValue-minValue+1;
        singleton=this;
    }
    
    public int getMinValue() { return minValue; }
    public int getMaxValue() { return maxValue; }
    public void setMinValue(int minValue) { this.minValue=minValue; valueCnt=maxValue-minValue+1; }
    public void setMaxValue(int maxValue) { this.maxValue=maxValue; valueCnt=maxValue-minValue+1; }
    
    public String toString(int val) {
        return Integer.toString(val);
    }
    
    public int valueOf(TokenInfo[] toks) { 
        return toks.length;
    }
}

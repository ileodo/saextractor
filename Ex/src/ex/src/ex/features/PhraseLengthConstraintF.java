// $Id: PhraseLengthConstraintF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

public class PhraseLengthConstraintF extends PhraseF implements IntFeature {
    public int minValue;
    public int maxValue;
    
    public PhraseLengthConstraintF(int minValue, int maxValue, String featureName) {
        super(FM.getFMInstance().getNextFeatureId(), featureName, VAL_BOOLEAN);
        this.minValue=minValue;
        this.maxValue=maxValue;
    }

    public String toString(int val) {
        return boolValue2string(val);
    }
}

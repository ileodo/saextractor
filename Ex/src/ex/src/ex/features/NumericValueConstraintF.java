// $Id: NumericValueConstraintF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

public class NumericValueConstraintF extends PhraseF implements IntFeature {
    public double minValue;
    public double maxValue;
    
    public NumericValueConstraintF(double minValue, double maxValue, String featureName) {
        super(FM.getFMInstance().getNextFeatureId(), featureName, VAL_BOOLEAN);
        this.minValue=minValue;
        this.maxValue=maxValue;
    }

    public String toString(int val) {
        return boolValue2string(val);
    }
}

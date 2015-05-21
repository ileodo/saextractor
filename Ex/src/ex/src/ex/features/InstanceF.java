// $Id: InstanceF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.lang.String;

public abstract class InstanceF extends Feature {
    public static InstanceF[] intFeatures;
    public static int intCount;
    public static InstanceF[] floatFeatures;
    public static int floatCount;

    protected InstanceF(int featureId, String featureName, int valType) {
        super(featureId, featureName, OBJ_INSTANCE, valType);
    }
    public int intValueFor(String tagName) { return -1; };
    public float floatValueFor(String tagName) { return -1; };
}

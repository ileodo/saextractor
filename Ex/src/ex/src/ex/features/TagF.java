// $Id: TagF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.lang.String;
import java.util.ArrayList;

public abstract class TagF extends Feature {
    public static ArrayList<TagF> intFeatures=new ArrayList<TagF>(64);
    public static ArrayList<TagF> floatFeatures=new ArrayList<TagF>(64);

    protected TagF(int featureId, String featureName, int valType) {
        super(featureId, featureName, OBJ_TAG, valType);
        // add this feature to the correct list
        switch(valueType) {
        case VAL_BOOLEAN:
        case VAL_INT:
            intFeatures.add(this);
            break;
        case VAL_FLOAT:
            floatFeatures.add(this);
            break;
        }
    }
}

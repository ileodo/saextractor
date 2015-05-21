package ex.features;

import uep.util.Logger;
import ex.model.*;

/* This class defines features:
- for each attribute.
Features store the attribute's cardinality for the described instance 
 */

public class InstanceAttCardF extends InstanceF implements EnumFeature {
    public AttributeDef attDef;

    public InstanceAttCardF(int featureId, AttributeDef att) {
        super(featureId, att.name, VAL_INT);
        attDef=att;
    }

    public String toString(int val) {
        return String.valueOf(val);
    }

    public int fromString(String val) {
        try {
            return Integer.parseInt(val);
        }catch(NumberFormatException ex) {
            Logger.LOGERR("Cannot parse feature value: "+ex.toString());
        }
        return -1;
    }
}

// $Id: PhraseF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.lang.String;
import java.util.ArrayList;

public abstract class PhraseF extends Feature {
    public static ArrayList<PhraseF> intFeatures=new ArrayList<PhraseF>(64);
    public static ArrayList<PhraseF> floatFeatures=new ArrayList<PhraseF>(64);

    protected PhraseF(int featureId, String featureName, int valType) {
        super(featureId, featureName, OBJ_PHRASE, valType);
        // add this feature to the correct list
        switch(valueType) {
        case VAL_BOOLEAN:
        case VAL_ENUM:
        case VAL_INT:
            intFeatures.add(this);
            break;
        case VAL_FLOAT:
            floatFeatures.add(this);
            break;
        }

    }
    //public int intValueFor(String stringValue) { return -1; };
    //public float floatValueFor(String stringValue) { return -1; };
}

// $Id: TokenF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.ArrayList;

public abstract class TokenF extends Feature {
    public static ArrayList<TokenF> intFeatures=new ArrayList<TokenF>(64);
    public static ArrayList<TokenF> floatFeatures=new ArrayList<TokenF>(64);
    
    protected TokenF(int featureId, String featureName, int valType) {
        super(featureId, featureName, OBJ_TOKEN, valType);
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
}

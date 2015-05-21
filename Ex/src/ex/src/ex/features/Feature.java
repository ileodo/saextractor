// $Id: Feature.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.lang.String;
import java.util.ArrayList;
import java.util.List;

public abstract class Feature {
    public static final int OBJ_TOKEN   =0;
    public static final int OBJ_TAG     =1;
    public static final int OBJ_PHRASE  =2;
    public static final int OBJ_INSTANCE=3;
    
    public static final int VAL_BOOLEAN=0;
    public static final int VAL_ENUM   =1;
    public static final int VAL_INT    =2;
    public static final int VAL_FLOAT  =3;
    
    protected static final List<String> boolVals=new ArrayList<String>(2);
    static {
        boolVals.add("0");
        boolVals.add("1");
    }
    
    public String name;  // human readable
    public int id;       // index of this feature in FeatureManager's collection for this OBJ
    public int objectType; // OBJ_*
    public int valueType;  // VAL_*
    public int valueCnt; // count of distinct values this feature can have, use -1 for infinity
    
    protected Feature(int featureId, String featureName, int objType, int valType) {
        id=featureId;
        name=featureName;
        objectType=objType;
        valueType=valType;
        switch(valueType) {
        case VAL_BOOLEAN:
            valueCnt=2;
            break;
        case VAL_INT:
        case VAL_FLOAT:
            valueCnt=Integer.MAX_VALUE;
            break;
        default:
            valueCnt=0;
        }
    }
    
    public String toString() {
        return name;
    }

    // features that need extra deinit code will overrride this method (e.g. to shutdown lemmatizer)
    public void deinit() {
        ;
    }
    
    public List<String> getValues() {
        switch(valueType) {
        case VAL_BOOLEAN:
            return boolVals;
        }
        throw new UnsupportedOperationException("Feature "+name+" valueType="+valueType+" objectType="+objectType);
    }
    
    public static String boolValue2string(int value) {
        if(value<0||value>1)
            throw new IllegalArgumentException("boolValue2string("+value+")");
        return boolVals.get(value);
    }
}

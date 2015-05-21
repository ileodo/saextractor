// $Id: ClassificationF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uep.util.Options;

/** This is a multi-valued feature which can be used instead of multiple binary 
 * AnnotationF features. */
public class ClassificationF extends PhraseF implements IntFeature {
    public byte type; // SemAnnot.type: TYPE_AV, TYPE_INST
    public List<String> values;
    public Map<String,Integer> valueMap;
    
    public static final String BG="bg";
    
    public static final String PLEFT="_pleft"; // phrase prolongs att on the left
    public static final String PRIGHT="_pright"; // phrase prolongs att on the right
    public static final String CLEFT="_cleft"; // phrase crosses left border of att
    public static final String CRIGHT="_cright"; // phrase crosses right border of att
    public static final String CONTAINED="_contained"; // phrase is contained inside att
    public static final String CONTAINS="_contains"; // phrase contains att
    public static final String PREFIX="_prefix"; // phrase is a prefix of att (special case of contained)
    public static final String SUFFIX="_suffix"; // phrase is a suffix of att (special case of contained)
    
    static final String[] clsSpecials={PLEFT, PRIGHT, CLEFT, CRIGHT, CONTAINED, CONTAINS, PREFIX, SUFFIX};
    
    public ClassificationF(byte type, String name, List<String> values) {
        super(FM.getFMInstance().getNextFeatureId(), name, VAL_ENUM);
        this.type=type;
        setValues(values);
    }
    
    public String toString(int val) {
        return values.get(val);
    }
    
    public List<String> getValues() {
        return values;
    }
    
    public void setValues(List<String> values) {
        if(this.values==null) {
            this.values=new ArrayList<String>(16);
            this.valueMap=new HashMap<String,Integer>();
        }else {
            this.values.clear();
            this.valueMap.clear();
        }
        if(values!=null) {
            int classMode=Options.getOptionsInstance().getIntDefault("class_mode", -1);
            int i=0;
            for(String val: values) {
                this.values.add(val);
                this.valueMap.put(val, i++);
            }
            if(classMode>0) {
                for(String val: values) {
                    if(!val.equals(BG)) {
                        for(String spec: clsSpecials) {
                            String valSpec = val+spec;
                            this.values.add(valSpec);
                            this.valueMap.put(valSpec, i++);                        
                        }
                    }
                }
            }
        }
        this.valueCnt=this.values.size();
    }

    public int toValue(String cls) {
        int rc=0;
        Integer i=valueMap.get(cls);
        if(i!=null)
            rc=i;
        return rc;
    }
    
    public String toString() {
        StringBuffer s=new StringBuffer(128);
        s.append("ClassificationF: ");
        int i=0;
        for(String val: values) {
            if(i++>0)
                s.append(",");
            s.append(val);
        }
        return s.toString();
    }
}

// $Id: WordClassificationF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Contains word classifications: for each attribute, there are two word classes: 
 *  beginning word and inner word. */
public class WordClassificationF extends ClassificationF {
    public static final String BEGIN="B-";
    public static final String INNER="I-";
    static final String[] wordClassTypes={BEGIN, INNER};
    
    public WordClassificationF(byte type, String name, List<String> values) {
        super(type, name, values);
    }

    /** Overrides ClassificationF.setValues().
     *  Sets possible class values. Invoked automatically by constructor of superclass. */
    public void setValues(List<String> values) {
        if(this.values==null) {
            this.values=new ArrayList<String>(16);
            this.valueMap=new HashMap<String,Integer>();
        }else {
            this.values.clear();
            this.valueMap.clear();
        }
        if(values!=null) {
            int i=0;
            for(String val: values) {
                if(val.equals(BG)) {
                    this.values.add(val);
                    this.valueMap.put(val, i++);
                }else {
                    for(String spec: wordClassTypes) {
                        String valSpec = spec+val;
                        this.values.add(valSpec);
                        this.valueMap.put(valSpec, i++);                        
                    }
                }
            }
        }
        this.valueCnt=this.values.size();
    }

    public static String getPhraseClass(String cls) {
        if(cls.startsWith(BEGIN) || cls.startsWith(INNER)) {
            return cls.substring(2);
        }
        return null;
    }
    
    public int toValue(String cls) {
        int rc=super.toValue(cls);
        if(rc==0) {
            rc=super.toValue(BEGIN+cls);
        }
        return rc;
    }
}

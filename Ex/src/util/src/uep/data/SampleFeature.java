// $Id: $
package uep.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SampleFeature {
    public final static byte DT_ENUM=1;
    public final static byte DT_INT=2;
    public final static byte DT_FLOAT=3;
    public final static byte DT_STRING=4;
    protected final static String[] typeNames={"unknown","enum","int","float","string"};
    
    private String name;
    private byte type;
    private String[] values;
    private Map<String,Integer> value2idx;
    private Object data;
    
    public SampleFeature(String name, byte type, String[] values) {
        this(name, type, values, null);
    }
    
    public SampleFeature(String name, byte type, String[] values, Object data) {
        this.name=name;
        this.type=type;
        this.data=data;
        setValues(values);
    }
    
    public String type2string() {
        return typeNames[type];
    }
        
    public String toString() {
        return name+" "+type2string();
    }
    
    public int valueToIndex(String val) {
        if(values==null) {
            throw new UnsupportedOperationException("Not supported for feature type="+type+" values="+values);
        }
        Integer idx=value2idx.get(val);
        if(idx==null) {
            throw new IllegalArgumentException("Unknown feature value "+val+", known values="+Arrays.toString(values));
        }
        return idx;
    }
    
    public String indexToValue(int idx) {
        String val;
        switch(type) {
        case DT_INT: 
            val=Integer.toString(idx).intern(); break;
        case DT_ENUM: 
        case DT_STRING:
            val=values[idx]; break;
        case DT_FLOAT: 
            throw new UnsupportedOperationException("Not supported for floats");
        default: 
            throw new IllegalArgumentException("Unknown feature data type "+type);
        }
        return val;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** @return one of DT_ENUM, DT_STRING, DT_INT, DT_FLOAT. */
    public byte getType() {
        return type;
    }

    /** @param type one of DT_ENUM, DT_STRING, DT_INT, DT_FLOAT. 
     * Clears enumeration values if any exist.  */
    public void setType(byte type) {
        this.type = type;
        if(type!=DT_ENUM) {
            value2idx=null;
            values=null;
        }
    }

    /** Only applies to DT_ENUM features. */
    public String[] getValues() {
        return values;
    }

    /** Returns user data associated with this feature. */
    public Object getData() {
        return data;
    }

    /** Sets user data to be associated with this feature. */
    public void setData(Object data) {
        this.data=data;
    }
    
    /** Only applies to DT_ENUM features. */
    public void setValues(String[] values) {
        this.values = values;
        if(values!=null) {
            value2idx=new HashMap<String,Integer>(values.length*2);
            for(int i=0;i<values.length;i++) {
                value2idx.put(values[i], i);
            }
        }else {
            value2idx=null;
        }
    }
}

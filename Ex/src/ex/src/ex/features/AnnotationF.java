// $Id: AnnotationF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uep.util.Logger;

import ex.reader.SemAnnot;

/** Turned on for phrases marked with a corresponding annotation. 
 * The annotation can be e.g. a syntactic chunk, a POS tag, an automatic label
 * produced by preceding processors, or a manual attribute annotation in case of training data */
public class AnnotationF extends PhraseF implements IntFeature {
    public Object data; // either a ModelElement or a String for other annotations
    public int labelId;
    public byte type; // SemAnnot.type: TYPE_AV, TYPE_INST, TYPE_CHUNK
    protected static Map<String,AnnotationF> knownLabelMap=new HashMap<String,AnnotationF>();
    protected static List<AnnotationF> knownLabelList=new ArrayList<AnnotationF>(32);
    
    /** Creates a new annotation feature of the given name; other features with the same name must not exist. */
    public AnnotationF(byte type, Object data) {
        super(FM.getFMInstance().getNextFeatureId(), data.toString(), VAL_BOOLEAN);
        this.data=data;
        this.type=type;
        this.labelId=knownLabelList.size()+1;
        if(knownLabelMap.containsKey(name.toLowerCase()))
            throw new IllegalArgumentException("Annotation feature for "+name+" already exists; old="
                    +knownLabelMap.get(name.toLowerCase()).data+", new="+data);
        knownLabelList.add(this);
        knownLabelMap.put(name.toLowerCase(), this);
// #ifdef REG_SHORTNAMES 
//        int doti=name.indexOf('.'); 
//        while(doti!=-1) {
//            String shortName=name.substring(doti+1);
//            knownLabelMap.put(shortName, this);
//            doti=name.indexOf('.', doti+1);
//        }
// #endif
    }
    
    public String toString(int val) {
        return boolValue2string(val);
    }

    /** Maps string label names to AnnotationF instances. If register==true,
     *  creates a new AnnotationF for a new label name. Otherwise returns null. */
    public static AnnotationF getAnnotation(byte type, Object data, boolean register) {
        String labelName=data.toString();
        AnnotationF f=knownLabelMap.get(labelName.toLowerCase());
        if(f!=null && f.type!=type) {
            Logger.LOG(Logger.WRN,"Annotation feature of name "+labelName+" already exists with type="+f.type+", required type="+type);
        }
        if(f==null && register) {
            f=new AnnotationF(type, data);
        }
        return f;
    }
    
    /** Maps integer labelId to AnnotationF instance. Returns null if labelId is unknown. */
    public static AnnotationF getAnnotation(int labelId) {
        AnnotationF f=null;
        if(labelId>0 && labelId<=knownLabelList.size())
            f=knownLabelList.get(labelId-1);
        return f;
    }    
}

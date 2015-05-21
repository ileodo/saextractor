// $Id: SemAnnot.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import uep.util.Logger;
import ex.ac.Annotable;
import ex.features.AnnotationF;
import ex.model.AttributeDef;
import ex.model.ClassDef;
import ex.model.ModelElement;

/** SemAnnot represents a semantic annotation. Depending on type, 
 * its data can e.g. point to a definition of a model attribute or class,
 * or to a parse chunk produced by 3rd party syntactic parser. */
public class SemAnnot extends Annot implements Annotable {
    public static final byte TYPE_AV=1;
    public static final byte TYPE_INST=2;
    public static final byte TYPE_CHUNK=4;
    public static final byte TYPE_WRAPPER=8;
    // used for filtering:
    public static final byte TYPE_ALL=TYPE_AV|TYPE_INST|TYPE_CHUNK;
    
    public int labelId; // id of the associated AnnotationF
    public double confidence;
    public Object data; // label of type given by <data>: e.g. AttributeDef
    public Object origAnnot; // any object label from host system that represents this SemAnnot in it (Annotation for IET)

    public SemAnnot(int type, int startTok, int endTok, Annot parent, int parIdx, Object data, Object origAnnot) {
        super(type, startTok, endTok, parent, parIdx);
        this.annotType=ANNOT_SEM;
        this.data=data;
        // parent instance assigned by parser, or, for labeled documents, read and assigned separately
        this.parent=null;
        this.origAnnot=origAnnot;
        if(type!=TYPE_WRAPPER) {
            // String labelName = (type==TYPE_AV || type==TYPE_INST)? ((ModelElement)data).name: data.toString();
            String labelName;
            if(data instanceof medieq.iet.model.AttributeValue)
                labelName = ((medieq.iet.model.AttributeValue) data).getAttributeDef().getName();
            else if(data instanceof medieq.iet.model.Instance)
                labelName = ((medieq.iet.model.Instance) data).getClassDef().getName();
            else
                labelName = data.toString();
            
            this.labelId=AnnotationF.getAnnotation(SemAnnot.TYPE_CHUNK, labelName, true).labelId;
        }else {
            this.labelId=0;
        }
    }
    
    public void clear() {
        data=null;
        origAnnot=null;
        super.clear();
    }
        
    public String toString() {
        String s=null;
        switch(type) {
        case TYPE_AV:
            s="AV of "+data;
            break;
        case TYPE_INST:
            s="INST of "+data;
            break;
        case TYPE_CHUNK:
            s="CHUNK of type "+data;
            break;
        default:
            s="SemAnnot of unknown type: "+data;
            break;
        }
        return s;
    }
    
    public boolean equals(Object obj) {
        if(obj==null || !(obj instanceof SemAnnot))
            return false;
        SemAnnot sa=(SemAnnot) obj;
        if(data!=sa.data || type!=sa.type || startIdx!=sa.startIdx || endIdx!=sa.endIdx || labelId!=sa.labelId)
            return false;
        if(data==sa.data)
            return true;
        if(data==null || sa.data==null)
            return false;
        return data.equals(sa.data);
    }
    
    public String getName() {
        String rc;
        switch(type) {
        case TYPE_WRAPPER:
            rc="wrapper";
            break;
        default:
            rc=AnnotationF.getAnnotation(labelId).name;
        }
        return rc;
    }

    /* Annotable impl */
    public double getProb() {
        return confidence;
    }

    public void setProb(double confidence) {
        this.confidence=confidence;
    }
    
    public ModelElement getModelElement() {
        ModelElement melem=null;
        switch(type) {
        case TYPE_AV:
        case TYPE_INST:
            Object obj=AnnotationF.getAnnotation(labelId).data;
            if(obj instanceof ModelElement)
                melem=(ModelElement) obj;
            else if(obj instanceof String)
                Logger.LOG(Logger.WRN,"Unknown attribute "+obj);
            else
                Logger.LOG(Logger.ERR,"Unknown annotation data: "+obj);
        }
        return melem;
    }
    
    public int getType() {
        return Annotable.TYPE_LABEL;
    }
    
    public int getStartIdx() {
        return startIdx;
    }
}

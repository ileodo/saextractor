// $Id: ExtractedObjects.java 1641 2008-09-12 21:53:08Z labsky $
package ex.dom;

import java.util.ArrayList;
import java.util.List;

import medieq.iet.model.AnnotableObject;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.Instance;

public class ExtractedObjects extends ArrayList<AnnotableObject> {
    private static final long serialVersionUID = 1994289291515908987L;
    protected medieq.iet.model.DataModel ietModel;
    
    public ExtractedObjects(medieq.iet.model.DataModel ietModel) {
        this.ietModel=ietModel;
    }
    
    public List<AttributeValue> getAttributesByName(String attName, int filter) {
        if(filter!=0)
            throw new IllegalArgumentException("Filter not implemented");
        attName=attName.trim().toLowerCase();
        List<medieq.iet.model.AttributeValue> lst=new ArrayList<AttributeValue>(8);
        for(AnnotableObject o: this) {
            // 1. standalone
            if(o instanceof AttributeValue) {
                AttributeValue av=(AttributeValue) o;
                if(av.getAttributeName().equalsIgnoreCase(attName)) {
                    lst.add(av);
                }
            }
            // 2. instance members
            else if(o instanceof medieq.iet.model.Instance) {
                Instance inst=(medieq.iet.model.Instance) o;
                lst.addAll(inst.getAttributesByName(attName, ietModel));
            }
        }
        return lst;
    }
    
    public List<AttributeValue> getAttributesByName(String attName) {
        return getAttributesByName(attName, 0);
    }
    
    public List<medieq.iet.model.Instance> getInstancesByClass(String clsName) {
        clsName=clsName.trim().toLowerCase();
        List<medieq.iet.model.Instance> lst=new ArrayList<medieq.iet.model.Instance>(8);
        for(AnnotableObject o: this) {
            if(o instanceof medieq.iet.model.Instance) {
                Instance inst=(medieq.iet.model.Instance) o;
                if(inst.getClassName().equalsIgnoreCase(clsName)) {
                    lst.add(inst);
                }
            }
        }
        return lst;
    }
}

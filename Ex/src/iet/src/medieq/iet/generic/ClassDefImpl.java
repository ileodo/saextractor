// $Id: ClassDefImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.generic;

import java.util.*;
import medieq.iet.model.*;

public class ClassDefImpl implements medieq.iet.model.ClassDef {
	protected String name;
	//protected List<AttributeDef> attDefs;
    protected List<AttributeClassLink> attLinks;
    
	public ClassDefImpl(String name, List<AttributeClassLink> attLinks) {
		this.name=name;
		//this.attDefs=new ArrayList<AttributeDef>(8);
        if(attLinks!=null) {
            this.attLinks=new ArrayList<AttributeClassLink>(attLinks);
        }else {
            this.attLinks=new ArrayList<AttributeClassLink>(16);
        }
	}
	
	/* ClassDef impl */
	//public List<AttributeDef> getAttributes() {
	//	return attDefs;
	//}

    public List<AttributeClassLink> getAttributeLinks() {
        return attLinks;
    }
    
    /* Returns the information linking this class and the given attribute definition. */
    public AttributeClassLink getAttributeLink(AttributeDef attDef) {
        Iterator<AttributeClassLink> lit=attLinks.iterator();
        while(lit.hasNext()) {
            AttributeClassLink link=lit.next();
            if(link.getAttributeDef()==attDef)
                return link;
        }
        return null;
    }
    
	public String getName() {
		return name;
	}
	
	/* write access methods */
	public void setName(String name) {
		this.name=name;
	}
    
    public void setAttributeLinks(List<AttributeClassLink> lst) {
        attLinks.clear();
        attLinks.addAll(lst);
    }
    
    public String toString() {
        return name;
    }
}

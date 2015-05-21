// $Id: ClassDef.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

import java.util.*;

public interface ClassDef {
	public String getName();
    /* @deprecated */
	// public List<AttributeDef> getAttributes();
    public List<AttributeClassLink> getAttributeLinks();
    /* Returns the information linking this class and the given attribute definition. */
    public AttributeClassLink getAttributeLink(AttributeDef attDef);
}

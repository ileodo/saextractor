// $Id: Instance.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

import java.util.*;

public interface Instance extends AnnotableObject {
	public String getId();
	public ClassDef getClassDef();
	public void setClassDef(ClassDef classDef);
    /** Returns all populated attribute values in this instance. */
	public List<AttributeValue> getAttributes();
    /** Returns all attribute values of the given type in this instance. */
    public List<AttributeValue> getAttributes(AttributeDef attDef);
    public double getScore();
    public void setScore(double score);
//    public String getAuthor();
//    public void setAuthor(String author);
	public String toString();
	public String toXML();
	
	/** Added for easy DOM access by finalizer. */
	public String getClassName();
    public void setClassName(String className, DataModel model);
    /** Short-hand for getAttributes(model.getAttribute(attName)). */
    public List<AttributeValue> getAttributesByName(String attName, DataModel model);
}

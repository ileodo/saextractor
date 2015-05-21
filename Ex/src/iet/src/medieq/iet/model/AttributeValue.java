// $Id: AttributeValue.java 1695 2008-10-19 23:03:57Z labsky $
package medieq.iet.model;

import java.util.*;

public interface AttributeValue extends AnnotableObject {
	/** parent instance */
	public Instance getInstance();
	public void setInstance(Instance inst);
	public AttributeDef getAttributeDef();
	public List<Annotation> getAnnotations();
    public double getScore();
	public String toString();
	public String toXML();
	
	public String getText();
    public void setText(String text);
//    public void setAuthor(String author);
//    public String getAuthors();
    
    /** Added for easy DOM access by finalizer. */
    public String getAttributeName();
    public void setAttributeName(String className, DataModel model);
}

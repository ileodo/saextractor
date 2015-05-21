// $Id: AnnotableObject.java 1695 2008-10-19 23:03:57Z labsky $
package medieq.iet.model;

public interface AnnotableObject {
    /** @return class name for instance, attribute name for attribute value. */
    public String getName();
    /** @return xml representation for instance, attribute text for attribute value. */
	public String getText();
	/** @return 0..1 indicating author's confidence about this object. */
	public double getConfidence();
	/** @return An IE engine name or "gold" to indicate human annotation. */
	public String getAuthor();
	/** @param author An IE engine name or "gold" to indicate human annotation. */
	public void setAuthor(String author);
	/** @return type of the annotation object. Known types are "instance", "av" AttributeValue, "an" for Annotation. */
	public String getType();
}

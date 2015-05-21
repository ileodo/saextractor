// $Id: Annotation.java 1695 2008-10-19 23:03:57Z labsky $
package medieq.iet.model;

import java.util.List;

public interface Annotation extends AnnotableObject {
	public int getStartOffset();
	public int getLength();
    /** Returns unnormalized text labeled by this annotation. */
    public String getText();
    /** Storage of user-defined data associated with this annotation. */
    public void setUserData(Object data);
    public Object getUserData();
    /** Sets unnormalized text labeled by this annotation. */
    public void setText(String text);
    public void setStartOffset(int offset);
    public void setLength(int length);
    /** To ease debugging, this enables IE engines to provide intermediate debugging 
     * information about possible matches for this annotation. */
    public void addDebugInfo(Object obj);
    public List<Object> getDebugInfo();
}

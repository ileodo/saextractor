// $Id: AnnotationFilter.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import medieq.iet.model.AnnotableObject;

/** A binary filter useful to filter annotations, e.g. 
 *  to choose annotations that will appear on output. */
public interface AnnotationFilter {
    boolean matches(AnnotableObject obj);
}

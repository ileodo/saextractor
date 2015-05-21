// $Id: Preprocessor.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import medieq.iet.model.Document;
import medieq.iet.model.DocumentSet;

/** Performs common document preprocessing prior to extraction by IE engines.
 */
public interface Preprocessor {
    /** Performs preprocessing of document prior to processing by IE engines. 
     * @return true if any changes were made to doc */
    public boolean preprocess(Document doc);
    /** Performs preprocessing of all documents from document set 
     * prior to processing by IE engines. 
     * @return true if any changes were made to doc */
    public boolean preprocess(DocumentSet docSet);
}

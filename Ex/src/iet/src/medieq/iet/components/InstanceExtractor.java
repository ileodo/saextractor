// $Id: InstanceExtractor.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import medieq.iet.model.*;

public interface InstanceExtractor {
    /** Extracts instances from doc. */
	public int extractInstances(Document doc, DataModel model);
	/** Extracts instances from each document in docSet. */
	public int extractInstances(DocumentSet docSet, DataModel model);
}

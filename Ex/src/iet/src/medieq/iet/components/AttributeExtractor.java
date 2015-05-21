// $Id: AttributeExtractor.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import medieq.iet.model.*;

public interface AttributeExtractor {
    public int extractAttributes(Document doc, DataModel model);
    public int extractAttributes(DocumentSet docSet, DataModel model);
}

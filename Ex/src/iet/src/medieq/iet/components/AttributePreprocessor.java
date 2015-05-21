// $Id: AttributePreprocessor.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import medieq.iet.model.AttributeValue;
import medieq.iet.model.Document;

public interface AttributePreprocessor {
    public static char AP_OK=1;
    public static char AP_CHANGED=2;
    public static char AP_DISCARD=1;
    int preprocess(Document doc, AttributeValue av);
}

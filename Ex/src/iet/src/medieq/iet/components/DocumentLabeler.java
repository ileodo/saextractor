// $Id: DocumentLabeler.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.io.IOException;
import medieq.iet.model.*;

public interface DocumentLabeler {
    public String getAnnotatedDocument();
    public void writeAnnotatedDocument(String fileName, String encoding) throws IOException;
    public int annotateDocument(Document doc);
    public void clear();
}

// $Id: $
package medieq.iet.components;

import java.io.IOException;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;
import medieq.iet.model.DocumentFormat;

public class TextHtmlDocumentReader extends SGMLDocumentReader {
    
    public TextHtmlDocumentReader () {
        super("HTR");
    }
    
    public Document readDocument(Document doc, DataModel model, String baseDir,  boolean force) throws IOException {
        String src=loadDocumentContent(doc, baseDir, "utf-8"); // default enc
        // annot tool requires \r\n but treats them as 1 char:
        if(src!=null) {
            src=src.replace("\r\n", "\n");
            doc.setSource(src);
        }
        return doc;
    }

    public DocumentFormat getFormat() {
        return DocumentFormat.formatForId(DocumentFormat.HTML);
    }
}

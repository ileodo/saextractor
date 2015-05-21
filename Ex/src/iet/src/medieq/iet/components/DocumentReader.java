// $Id: DocumentReader.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.io.IOException;
import medieq.iet.model.Document;
import medieq.iet.model.DataModel;
import medieq.iet.model.DocumentFormat;

public interface DocumentReader {
    public static final String goldStandardAuthor="Gold";
    
    /** Creates a new Document and behaves as its sibling function below. */
    public Document readDocument(String fileName, String encoding, DataModel model, String baseDir, boolean force) throws IOException;
    
    /** Attempts to populate doc with source and annotations from doc.file.
     * If no doc.encoding is specified, a default is used or the implementation may try to guess encoding
     * based on the file content. If the file does not seem to be in the desired format and force==false, 
     * the return value is null and doc is not modified. If force, the system will attempt to read 
     * the document anyway. All encountered and unknown attributes and classes are added to model. */
    public Document readDocument(Document doc, DataModel model, String baseDir, boolean force) throws IOException;
    
    /** If set, the attribute preprocessor is called to pre-process every attribute label read from the document. */
    public void setAttributePreprocessor(AttributePreprocessor ap);

    /** Returns the document format that this reader can read. */
    public DocumentFormat getFormat();
}

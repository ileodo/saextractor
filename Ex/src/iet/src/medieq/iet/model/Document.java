// $Id: Document.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

import java.io.IOException;
import java.util.*;

/**
 * Document representation used within IET.   
 * IET may use the {@link #getClassifications()} method to decide 
 * which extraction models it will apply to the Document.
 * Using applicable models, IET adds to the Document's metadata
 * extracted attribute values and also instances composed of these attribute values.
 * IET can also annotate the document content with colored labels which
 * represent attribute values (or their alternative versions). Tables
 * can be added to the content which represent extracted instances.
 */
public interface Document {
	/** Identifier of this document (e.g. url+timestamp combination or auto-increment id) */
	public String getId();
    /** Sets document identifier */
    public void setId(String id);
	/** Original url the document was downloaded from */
	public String getUrl();
    public void setUrl(String url);
	/** Name of locally cached file */
	public String getFile();
    public void setFile(String file);
	/** Encoding of cached file */
	public String getEncoding();
    public void setEncoding(String enc);
    /** Whether to force encoding specified for this Document or
     * whether to respect the encoding specified within the file being read 
     * (e.g. via http headers or meta tag for html). */
    public boolean getForceEncoding();
    public void setForceEncoding(boolean force);
    /** Content type of the resource */
    public String getContentType();
    public void setContentType(String contentType); 
	/** Original document content */
	public String getSource();
    /** Populates original document content */
    public void setSource(String content);
    /** Size in characters */
	public int getSize();
	/** Obtains graphically annotated document content */
	public String getAnnotatedSource();
    /** Setter for annotated document content */
	public void setAnnotatedSource(String annSource);
	/** Instances found in this document (automatically extracted or manually annotated) */
	public List<Instance> getInstances();
	/** Attribute values found in this document.
	    If instances are populated, some of these AttributeValues may be bound to instances,
        some may be standalone. */
	public List<AttributeValue> getAttributeValues();
	/** N-best classes assigned by document classifier */
	public List<Classification> getClassifications();
	/** List of Extraction model file names against which this doc has already been processed */
	public List<String> getProcessedModels();
	/** Gets evaluation result if available. */
    public EvalResult getEvalResult();
    /** Sets evaluation result. */
    public void setEvalResult(EvalResult res);
    /** Attempts to load document source using one of IET's document loaders 
     *  selected using document's content type and file name. */
    public void populateSource(DataModel model) throws IOException;
}

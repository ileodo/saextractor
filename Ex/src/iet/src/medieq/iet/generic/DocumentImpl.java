// $Id: DocumentImpl.java 1652 2008-09-16 06:45:01Z labsky $
package medieq.iet.generic;

import java.io.IOException;
import java.util.*;

import medieq.iet.components.DocumentReader;
import medieq.iet.model.*;
import uep.util.Fetcher;
import uep.util.Logger;

public class DocumentImpl implements Document {
	protected static int maxId=0;
	protected String id;
	protected String url;
	protected String file;
	protected String source;
	protected String annSource;
	protected int size;
    protected String encoding;
    protected boolean forceEncoding;
    protected String contentType;
	protected Vector<AttributeValue> attrVals;
	protected Vector<Instance> instVals;
	protected Vector<String> processedModels;
    protected Vector<Classification> classes;
    protected EvalResult evalResult;
	
	public DocumentImpl(String url, String file) {
		id=String.valueOf(++maxId);
		this.url=url;
		this.file=file;
		this.size=0;
        this.encoding="utf-8";
        if(url!=null)
            this.contentType=Fetcher.ext2mimeType(url);
        if(contentType==null && file!=null)
            this.contentType=Fetcher.ext2mimeType(file);
		this.attrVals=new Vector<AttributeValue>(2);
		this.instVals=new Vector<Instance>(2);
		this.processedModels=new Vector<String>(2);
        this.classes=new Vector<Classification>(2);
	}
	public String getEncoding() { return encoding; }
	public String getId() { return id; }
    public void setId(String id) { this.id=id; }
	public String getUrl() { return url; }
	public String getFile() { return file; }
	public String getContentType() { return contentType; }
	public void setContentType(String contentType) { this.contentType=contentType; }
	public String getSource() { return source; }
    public void setSource(String source) { this.source=source; }
	public String getAnnotatedSource() { return annSource; }
	public void setAnnotatedSource(String annSource) { this.annSource=annSource; }
	public int getSize() { return size; }
	public List<Instance> getInstances() { return instVals; }
	public List<AttributeValue> getAttributeValues() { return attrVals; }
	public List<Classification> getClassifications() { return classes; }
	public List<String> getProcessedModels() { return processedModels; }
	public boolean equals(Object anObject) { 
		if(anObject==null)
			return false;
		Document other=(Document) anObject;
		if(url!=null && url.equals(other.getUrl()))
			return true;
		return false;
	}
	public String toString() {
		int attrs=(getAttributeValues()!=null)? getAttributeValues().size(): 0;
		int insts=(getAttributeValues()!=null)? getInstances().size(): 0;
		String s=getId()+".";
		if(getFile()!=null && getFile().length()>0)
            s+=" file="+getFile();
		if(getUrl()!=null && getUrl().length()>0)
		    s+=" url="+((getFile()!=null && getUrl().equals(getFile()))? "${file}": getUrl());
		return s+" attrs="+attrs+", insts="+insts;
	}
    public void setEncoding(String enc) {
        this.encoding=enc;
    }
    public void setFile(String file) {
        this.file=file;       
    }
    public void setUrl(String url) {
        this.url=url;
    }
    public boolean getForceEncoding() {
        return forceEncoding;
    }
    public void setForceEncoding(boolean force) {
        this.forceEncoding=force;
    }
    public EvalResult getEvalResult() {
        return evalResult;
    }
    public void setEvalResult(EvalResult evalResult) {
        this.evalResult=evalResult;
    }
    
    public void populateSource(DataModel model) throws IOException {
        DocumentReader docReader=null;
        String cls=null;
        String err=null;
        Document d2=null;
        try {
            cls=DocumentFormat.formatForContentType(getContentType()).documentReaderClassName;
            docReader=(DocumentReader) Class.forName(cls).newInstance();
            d2=docReader.readDocument(this, model, null, false);
        }catch(IOException e) {
            err="IET could not read document "+getFile()+" using "+docReader+": "+e;
        } catch (InstantiationException e) {
            err="IET could not create reader for "+getFile()+", content type="+getContentType()+", class name="+docReader+": "+e;
        } catch (IllegalAccessException e) {
            err="IET has no permission to create reader for "+getFile()+", content type="+getContentType()+", class name="+docReader+": "+e;
        } catch (ClassNotFoundException e) {
            err="Reader not found for "+getFile()+", content type="+getContentType()+", class name="+docReader+": "+e;
        }
        if(d2!=null) {
            if(d2!=this) {
                this.setSource(d2.getSource());
                d2.setSource(null);
            }
        }else {
            if(err==null) {
                // readDocument returned null - this is now done by DatDocumentReader when it finds
                // the document is not in Ellogon format. We assume normal un-annotated HTML and let 
                // the IE engines handle the reading themselves:
                err="Assuming text/html, letting IE engines read document content themselves";
            }
            Logger.LOG(Logger.WRN, err);
            throw new IOException(err);
        }
    }
}

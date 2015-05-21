// $Id: DocumentSetImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.generic;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import medieq.iet.model.*;

public class DocumentSetImpl implements DocumentSet {
	protected String name;
	protected Vector<Document> documents;
    protected String baseDir;
//    protected boolean forceEncoding;
//    protected String encoding;
	
	public DocumentSetImpl(String name) {
		this.name=name;
		documents=new Vector<Document>(16);
        baseDir=null;
	}
	public String getName() {
		return name;
	}
	public List<Document> getDocuments() {
		return documents;
	}
    public String getBaseDir() {
        return baseDir;
    }
    public void setBaseDir(String baseDir) {
        this.baseDir=baseDir;
    }
	public int size() {
		return documents.size();
	}
	public String toString() {
		return getName()+" docs="+size();
	}
	
	private static Pattern urlPat=Pattern.compile("^(http|https|ftp)://");
    public static DocumentSet read(String fileName) throws IOException {
        BufferedReader br=new BufferedReader(new FileReader(new File(fileName)));
        DocumentSet ds=new DocumentSetImpl(fileName);
        String s=null;
        while((s=br.readLine())!=null) {
            s=s.trim();
            if(s.length()==0 || s.startsWith("#"))
                continue;
            boolean isRemote=urlPat.matcher(s).find();
            Document doc=isRemote? new DocumentImpl(s, null): new DocumentImpl(null, s);
            ds.getDocuments().add(doc);
        }
        return ds;
    }

// Specified for each doc separately:
//    public String getEncoding() {
//        return encoding;
//    }
//    public void setEncoding(String enc) {
//        this.encoding=enc;        
//    }
//    public boolean getForceEncoding() {
//        return forceEncoding;
//    }
//    public void setForceEncoding(boolean force) {
//        this.forceEncoding=force;
//    }
}

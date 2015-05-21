// $Id: SGMLDocumentReader.java 1990 2009-04-22 22:07:59Z labsky $
package medieq.iet.components;

import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;

import uep.util.CacheItem;
import uep.util.Logger;

import medieq.iet.generic.DocumentImpl;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;

public abstract class SGMLDocumentReader implements DocumentReader {
    protected StringBuffer buff;
    protected CharBuffer cb;
    protected AttributePreprocessor atrPreprocessor;
    protected static Logger log;
    
    public SGMLDocumentReader(String name) {
        buff=new StringBuffer(1024);
        cb=CharBuffer.allocate(512);
        if(log==null)
            log=Logger.getLogger(name);
    }
    
    public Document readDocument(String fileName, String encoding,
            DataModel model, String baseDir, boolean force) throws IOException {
        Document doc=new DocumentImpl(null, fileName);
        doc.setEncoding(encoding);
        return readDocument(doc, model, baseDir, force);
    }

    protected String loadDocumentContent(Document doc, String baseDir, String defaultEnc) throws IOException {
        buff.setLength(0);
        cb.clear();
        File f=new File(baseDir, doc.getFile());
        String enc=doc.getEncoding();
        boolean forceEnc=doc.getForceEncoding();
        if(enc==null || enc.trim().length()==0) {
            enc=defaultEnc;
            forceEnc=false;
        }
        CacheItem ci = CacheItem.fromFile(f.getAbsolutePath(), doc.getContentType(), enc, forceEnc);
        if(ci==null) {
            throw new IOException("Error opening document "+f.getAbsolutePath());
        }
        if(ci.encoding!=null && ci.encoding.length()>0) {
            // important so that document containing meta charset=1252 gets also saved as charset=1252
            doc.setEncoding(ci.encoding);
        }
        return ci.data;
//        String enc=doc.getEncoding();
//        if(enc==null || enc.trim().length()==0) {
//            enc=defaultEnc;
//        }
//        buff.setLength(0);
//        File f=new File(baseDir, doc.getFile());
//        // doc.setFile(f.getAbsolutePath());
//        InputStreamReader is=new InputStreamReader(new FileInputStream(f), enc);
//        BufferedReader br=new BufferedReader(is, 1024);
//        int r=-1;
//        int rsz=0;
//        while((r=br.read(cb))!=-1) {
//            cb.flip();
//            buff.append(cb);
//            cb.clear();
//            rsz+=r;
//            // log.LG(Logger.TRC,"Read "+rsz+"/"+charCnt+"; buff.length="+buff.length());
//        }
//        // doc content
//        String src=buff.toString();
//        buff.setLength(0);
//        return src;
    }
    
    public void setAttributePreprocessor(AttributePreprocessor ap) {
        atrPreprocessor=ap;
    }    
}

class TagItem {
    String tag;
    AttributeValue av;
    public TagItem(String tag, AttributeValue av) {
        this.tag = tag;
        this.av = av;
    }
    public String toString() {
        return tag+"."+av;
    }
}

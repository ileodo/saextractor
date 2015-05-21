// $Id: CookedDocumentReader.java 1680 2008-10-11 09:59:37Z labsky $
package medieq.iet.components;

import java.io.File;
import java.io.IOException;

import uep.util.CacheItem;
import uep.util.Logger;

import medieq.iet.generic.AttributeDefImpl;
import medieq.iet.generic.AttributeValueImpl;
import medieq.iet.generic.DocumentImpl;
import medieq.iet.model.Annotation;
import medieq.iet.model.AttributeDef;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;
import medieq.iet.model.DocumentFormat;

public class CookedDocumentReader implements DocumentReader {
    protected AttributePreprocessor atrPreprocessor;
    protected static Logger log;
    public static final String AUTO_SUFFIX = ".class";
    public static final String GOLD_SUFFIX = ".test";
    public static final String CLS_BG = "bg";
    public static final String AUTO_AUTHOR = "Cook";
    
    public CookedDocumentReader() {
        if(log==null)
            log=Logger.getLogger("Cooked");
    }

    protected static String readClass(String rawCls) {
        String cls=rawCls.replaceAll("^<|/>$", "").toLowerCase();
        if(cls.endsWith("_p") || cls.endsWith("_s")) {
            cls=CLS_BG;
        }
        return cls;
    }
    
    public Document readDocument(String fileName, String encoding,
            DataModel model, String baseDir, boolean force) throws IOException {
        Document doc=new DocumentImpl(null, fileName);
        doc.setEncoding(encoding);
        return readDocument(doc, model, baseDir, force);
    }

    public Document readDocument(Document doc, DataModel model, String baseDir, boolean force) throws IOException {
        String defaultEnc="iso-8859-1";
        String file=doc.getFile().trim();
        String[] parts=file.split("\\s*\\|\\s*");
        if(parts.length>2) {
            throw new IOException("Unhandled file name syntax: "+file);
        }
        CacheItem[] cis=new CacheItem[parts.length];
        for(int fl=0;fl<parts.length;fl++) {
            String author=(fl==0)? goldStandardAuthor: AUTO_AUTHOR;
            File f=new File(baseDir, parts[fl]);
            String enc=doc.getEncoding();
            boolean forceEnc=doc.getForceEncoding();
            if(enc==null || enc.trim().length()==0) {
                enc=defaultEnc;
                forceEnc=false;
            }
            CacheItem ci = CacheItem.fromFile(f.getAbsolutePath(), doc.getContentType(), enc, forceEnc);
            if(ci==null) {
                throw new IOException("Error reading "+f.getAbsolutePath());
            }
            if(ci.encoding!=null && ci.encoding.length()>0) {
                // important so that document containing meta charset=1252 gets also saved as charset=1252
                doc.setEncoding(ci.encoding);
            }
            // parse cooked format, separate words and annotations
            StringBuffer text=new StringBuffer((int)(ci.data.length()/1.5));
            String[] sents=ci.data.trim().split("\\s*[\r\n]\\s*");
            for(int s=0;s<sents.length;s++) {
                String[] toks=sents[s].trim().split("\\s+");
                if((toks.length % 2)!=0) {
                    throw new IOException("Odd token count "+toks.length+" in cooked file "+f.getAbsolutePath());
                }
                int wc=toks.length/2;
                AttributeValue curAv=null;
                for(int i=0;i<wc;i++) {
                    // class
                    String cls=readClass(toks[i*2+1]);
                    if(curAv==null) {
                        // A. bg->bg
                        if(cls.equals(CLS_BG)) {
                            ;
                        }
                        // B. bg->av
                        else {
                            int startPos=text.length();
                            if(i>0) { startPos++; } // space
                            curAv=createAV(cls, startPos, author, model);
                            doc.getAttributeValues().add(curAv);
                        }
                    }else {
                        // C. av->bg|other AV
                        if(cls.equals(CLS_BG) || !curAv.getName().equals(cls)) {
                            setAVText(curAv, text);
                            if(cls.equals(CLS_BG)) {
                                curAv=null;
                            }else {
                                int startPos=text.length();
                                if(i>0) { startPos++; } // space
                                curAv=createAV(cls, startPos, author, model);
                                doc.getAttributeValues().add(curAv);                                
                            }
                        }
                        // B. same av continues (hopefully it is the same one since in cooked format one never knows)
                        else {
                            ;
                        }
                    }
                    // word
                    if(i>0) {
                        text.append(' ');
                    }
                    text.append(toks[i*2]);
                }
                if(curAv!=null) {
                    setAVText(curAv, text);
                    curAv=null;
                }
                text.append('\n'); // sentence end

            } // for sents
            ci.data=text.toString();
            cis[fl]=ci;
            if(log.IFLG(Logger.INF)) {
                log.LG(Logger.INF,"Cooked text:\n"+ci.data);
            }
            // check all versions of this document have the same text (annotations may differ)
            if(fl>0 && !ci.data.equals(cis[fl-1].data)) {
                throw new IOException("Text of cooked document #"+(fl+1)+" "+ci.absUrl+" differs from #"+fl+" "+cis[fl-1].absUrl);
            }
        }
        if(log.IFLG(Logger.INF)) {
            for(AttributeValue av: doc.getAttributeValues()) {
                log.LG(Logger.INF,"AV="+av);
            }
        }
        doc.setSource(cis[0].data);
        return doc;
    }
    
    private void setAVText(AttributeValue curAv, StringBuffer text) {
        Annotation a=curAv.getAnnotations().get(0);
        a.setLength(text.length()-a.getStartOffset());
        String val=text.substring(a.getStartOffset(), a.getStartOffset()+a.getLength());
        a.setText(val);
        curAv.setText(val);
    }

    private AttributeValue createAV(String cls, int startPos, String author, DataModel model) {
        AttributeDef ad=model.getAttribute(cls);
        if(ad==null) {
            ad=new AttributeDefImpl(cls, "string");
            if(log.IFLG(Logger.USR)) log.LG(Logger.USR,"Adding attribute definition "+ad);
            model.addAttribute(ad);
        }
        double conf=1.0;
        AttributeValue av=new AttributeValueImpl(ad, null, conf, null, startPos, -1, author);
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Adding AV "+av);
        return av;
    }

    public void setAttributePreprocessor(AttributePreprocessor ap) {
        atrPreprocessor=ap;
    }

    public DocumentFormat getFormat() {
        return DocumentFormat.formatForId(DocumentFormat.COOKED);
    }
}

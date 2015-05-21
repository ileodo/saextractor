// $Id: BikeDocumentReader.java 1846 2009-03-04 08:04:54Z labsky $
package medieq.iet.components;

import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uep.util.Logger;

import medieq.iet.generic.AttributeDefImpl;
import medieq.iet.generic.AttributeValueImpl;
import medieq.iet.model.Annotation;
import medieq.iet.model.AttributeDef;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;
import medieq.iet.model.DocumentFormat;

public class BikeDocumentReader extends SGMLDocumentReader {
    private static final Pattern patTag=Pattern.compile("<([be])_([a-z_\\-.]+)/>", Pattern.CASE_INSENSITIVE);
    
    public BikeDocumentReader() {
        super("BDR");
    }
    
    public Document readDocument(Document doc, DataModel model, String baseDir,  boolean force) throws IOException {
        String src=loadDocumentContent(doc, baseDir, "iso-8859-1");
        
        // use 1 char newlines (not necessary but we preprocess source anyway...)
        src=src.replace("\r\n", "\n");
        
        int pos=0;
        // pass through the text and filter out tags, capturing their values as AttributeValues 
        LinkedList<TagItem> stack=new LinkedList<TagItem>();
        Matcher m=patTag.matcher(src);
        while(m.find(pos)) {
            String name=m.group(2).trim().toLowerCase();
            int iniBuffLen=buff.length();
            buff.append(src.substring(pos, m.start()));
            boolean isEnd=m.group(1).equals("e");
            if(isEnd) {
                if(stack.size()==0) {
                    if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,doc+": unexpected end tag "+name);
                }else {
                    TagItem start=stack.removeLast();
                    boolean ignore=false;
                    if(!start.tag.equals(name)) {
                        ignore=true;
                        // end tags could be swapped when followed after each other.
                        // test if this is the case - ignore or complain:
                        ListIterator<TagItem> tit=stack.listIterator(stack.size());
                        while(tit.hasPrevious()) {
                            TagItem cand=tit.previous();
                            if(cand.tag.equals(name)) {
                                tit.remove();
                                stack.add(start);
                                start=cand;
                                ignore=false;
                                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,doc+": unexpected end tag "+name+" expected "+start.tag+"; recovered (end tags were swapped)");
                                break;
                            }
                        }
                        if(ignore && log.IFLG(Logger.ERR)) log.LG(Logger.ERR,doc+": unexpected end tag "+name+" expected "+start.tag+"; ignoring");
                    }
                    if(!ignore) {
                        if(name.equals("paragraph")) {
                            buff.append("</p>");
                        }else {
                            Annotation curAnn=start.av.getAnnotations().get(start.av.getAnnotations().size()-1);
                            curAnn.setLength(buff.length()-iniBuffLen);
                            String text=src.substring(pos, m.start());
                            curAnn.setText(text);
                            start.av.setText(text);
                        }
                    }
                }
            }else {
                TagItem item=new TagItem(name, null);
                if(name.length()==0) {
                    if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,"Empty attribute name!");
                }else if(name.equals("paragraph")) {
                    buff.append("<p>");
                }else { // semantic tags
                    AttributeDef ad=model.getAttribute(name);
                    if(ad==null) {
                        ad=new AttributeDefImpl(name, "string");
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Adding attribute definition "+ad);
                        model.addAttribute(ad);
                    }
                    double conf=1.0;
                    AttributeValue av=new AttributeValueImpl(ad, null, conf, null, buff.length(), -1, goldStandardAuthor);
                    doc.getAttributeValues().add(av);
                    item.av=av;
                }
                stack.add(item);
            }
            pos=m.end();
        }
        buff.append(src.substring(pos));
        src=buff.toString();
        
        doc.setSource(src);
        return doc;
    }

    public DocumentFormat getFormat() {
        return DocumentFormat.formatForId(DocumentFormat.BIKE);
    }
}

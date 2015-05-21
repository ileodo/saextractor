// $Id: SeminarDocumentReader.java 1643 2008-09-12 21:56:20Z labsky $
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

public class SeminarDocumentReader extends SGMLDocumentReader {
    protected static final Pattern patTag=Pattern.compile("</?([a-z_\\-.]+)>", Pattern.CASE_INSENSITIVE);
    protected static final String defaultEnc="iso-8859-1";
    
    public boolean insertLineBreaks;
    
    public SeminarDocumentReader() {
        super("SemDR");
        insertLineBreaks=true;
    }
    
    public Document readDocument(Document doc, DataModel model, String baseDir, boolean force) throws IOException {
        // doc content
        if(doc.getEncoding()==null || doc.getEncoding().trim().length()==0)
            doc.setEncoding(defaultEnc);
        String src=loadDocumentContent(doc, baseDir, defaultEnc);

        // "convert" text to html:
        // - add header
        buff.append("<html>\n<head>\n<meta http-equiv=\"Content-Type:\" content=\"text/html; charset="+doc.getEncoding()+"\">\n</head>\n<body>\n<div>\n");
        if(insertLineBreaks) {
            // - create <br> in place of newlines
            src=src.replaceAll("\n", " \1br/\2\n"); // we will then translate \1 and \2 to < and >
        }
        
        // first line looks like <1.9.2.93.14.31.50.garth+@NIAGARA.NECTAR.CS.CMU.EDU (Garth Gibson).0>
        // leave out:
        int pos=0;
        if(src.startsWith("<") && src.length()>1 && Character.isDigit(src.charAt(1))) {
            int ei=src.indexOf('>');
            if(ei!=-1) {
                pos=ei+1;
            }
        }

        // pass through the text and filter out tags, capturing their values as AttributeValues 
        LinkedList<TagItem> stack=new LinkedList<TagItem>();
        Matcher m=patTag.matcher(src);
        while(m.find(pos)) {
            String name=m.group(1).trim().toLowerCase();
            buff.append(src.substring(pos, m.start()));
            boolean isEnd=src.charAt(m.start()+1)=='/';
            if(isEnd) {
                if(stack.size()==0) {
                    if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,doc+": unexpected end tag "+name);
                }else {
                    TagItem start=stack.removeLast();
                    boolean ignore=false;
                    if(!start.tag.equals(name)) {
                        ignore=true;
                        // often end tags are swapped when followed immediately after each other.
                        // assume this is the case and swap the end tags
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
                        }else if(name.equals("sentence")) {
                            ; // alternatively, let it become an annotation as below
                        }else {
                            Annotation curAnn=start.av.getAnnotations().get(start.av.getAnnotations().size()-1);
                            //curAnn.setLength(buff.length()-iniBuffLen);
                            //String text=src.substring(pos, m.start());
                            curAnn.setLength(buff.length()-curAnn.getStartOffset());
                            if(curAnn.getLength()<=0) {
                                throw new IllegalArgumentException("Annotation of length "+curAnn.getLength());
                            }
                            String text=buff.substring(curAnn.getStartOffset());
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
                }else if(name.equals("sentence")) {
                    ; // alternatively, let it become an annotation as below
                }else { // sentence and semantic tags
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

        // "convert" text to html:
        // 3. add footer
        buff.append("</div>\n</body>\n</html>\n");
        src=buff.toString();
        if(insertLineBreaks) {
            // 4. translate escaped <br/>
            src=src.replace('\1', '<');
            src=src.replace('\2', '>');
        }
        
        // annot tool requires \r\n but treats them as 1 char so we 
        // need single byte line ending to have indices correct:
        src=src.replace("\r\n", "\n");
        doc.setSource(src);
        return doc;
    }
    
    public DocumentFormat getFormat() {
        return DocumentFormat.formatForId(DocumentFormat.SEMINAR);
    }
}

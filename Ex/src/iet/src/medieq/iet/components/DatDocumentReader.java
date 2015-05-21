// $Id: DatDocumentReader.java 1735 2008-11-18 00:46:27Z labsky $
package medieq.iet.components;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.nio.CharBuffer;

import uep.util.Logger;
import uep.util.Options;
import uep.util.Util;
import medieq.iet.model.*;
import medieq.iet.generic.*;

public class DatDocumentReader implements DocumentReader {
    protected StringBuffer buff;
    protected CharBuffer cb;
    // protected Map<String, Instance> id2inst;
    protected Map<String, AttributeValue> id2av;
    protected Map<Instance,String> instMembers;
    protected AttributePreprocessor atrPreprocessor;
    protected Logger log;
    protected boolean discardOutOfTextAnnotations;
    
    // id si ei datatype attname
    private static final Pattern patAnn=Pattern.compile("([0-9]+) (ne[\\w\\-.]*|token|sentence) \\{\\{([0-9]+) ([0-9]+)\\}\\}"); // \\{\\{type \\{([\\w\\-.]+) ([\\w\\-.]+)\\}\\}");
    private static final Pattern patAnnProp=Pattern.compile(" \\{\\{([\\w+\\-.]+) \\{([\\w\\-.]+) ([\\w\\-.]+)\\}\\}");
    // id model(template) class  
    private static final Pattern patInstHdr=Pattern.compile("([0-9]+) (relations[\\w\\-\\.]+|[\\w\\-]+:?[\\w\\-]+)");
    private static final Pattern patInstIdxs=Pattern.compile("\\{([0-9]+) ([0-9]+)\\}");
    // attname datatype att_id_list
    private static final Pattern patInstAtt=Pattern.compile("\\{([\\w_\\-]+) \\{([\\w_\\-]+) \\{?([^\\}]*)\\}?\\}\\}", Pattern.CASE_INSENSITIVE);
    //                                                       {([a-z_]+) {([A-Z_]+) {([0-9 ]*)}}}
    private static final Pattern patNaN=Pattern.compile("[^0-9 ]");
    private static final Pattern patCurlyNonEmptyLeaf=Pattern.compile("\\{[a-z]+ [a-z]+}");
    
    // pattern to find intra-html tag context
    private static final Pattern patInTagLeft=Pattern.compile("[^<>]*<");
    private static final Pattern patInTagRight=Pattern.compile("[^<>]*>");
    
    public DatDocumentReader() {
        buff=new StringBuffer(1024);
        cb=CharBuffer.allocate(512);
        // id2inst=new HashMap<String,Instance>();
        id2av=new HashMap<String,AttributeValue>();
        instMembers=new HashMap<Instance,String>(128);
        log=Logger.getLogger("DDR");
    }
    
    public Document readDocument(String fileName, String encoding, DataModel model, String baseDir, boolean force) throws IOException {
        Document doc=new DocumentImpl(null, fileName);
        doc.setEncoding(encoding);
        return readDocument(doc, model, baseDir, force);
    }
    
    /** Attempts to populate doc with source and annotations from doc.file 
     * (assuming doc.encoding if not found in source). 
     * If the file seems not to be in dat annotation format and force==false, the return value is null 
     * and doc is not modified. If force, the system will attempt to read the document anyway. 
     * All encountered and unknown attributes and classes are added to model. */
    public Document readDocument(Document doc, DataModel model, String baseDir, boolean force) throws IOException {
        if(doc.getEncoding()==null||doc.getEncoding().trim().length()==0) {
            doc.setEncoding("utf-8");
        }
        discardOutOfTextAnnotations=Options.getOptionsInstance("iet").getIntDefault("dat_discard_oot_annots", 0)>0;
        buff.setLength(0);
        File f=new File(baseDir, doc.getFile());
        // doc.setFile(f.getAbsolutePath());
        InputStreamReader is=new InputStreamReader(new FileInputStream(f), doc.getEncoding());
        BufferedReader br=new BufferedReader(is, 1024);
        String s;
        int charCnt=-1;
        int anc1=0, anc2=0;
        // id2inst.clear();
        id2av.clear();
        instMembers.clear();
        Map<String,String> props=new TreeMap<String,String>();
        int lno=0;
        while((s=br.readLine())!=null) {
            lno++;
            s=s.trim();
            if(s.startsWith("#") || s.length()==0)
                continue;
            if(anc2==anc1)
                anc1=0; // don't expect more annotations
            
            // read all annotations, 1 line=1 annot; either attribute value or instance
            if(anc1>0) {
                Matcher m=patAnn.matcher(s);
                if(m.find()) {
                    anc2++;
                    String id=m.group(1);
                    String annType=m.group(2);
                    int offset=Integer.parseInt(m.group(3));
                    int len=Integer.parseInt(m.group(4))-offset; // end idx is not included in value (no +1)
                    if(len<=0) {
                        log.LG(Logger.WRN,doc.getFile()+":"+lno+" Discarding annotation having illegal length: "+s);
                        continue;
                    }
                    // String datatype=m.group(5);
                    // String attName=m.group(6);
                    
                    Matcher m2=patAnnProp.matcher(s);
                    int pos=m.end();
                    while(m2.find(pos)) {
                        String propName=m2.group(1);
                        String dataType=m2.group(2);
                        String propValue=m2.group(3);
                        if(!dataType.equals("GDM_STRING") && !dataType.equals("GDM_STRING_SET")) {
                            log.LG(Logger.WRN,doc+": treating label datatype "+dataType+" as string");
                        }
                        props.put(propName, propValue);
                        pos=m2.end();
                    }
                    
                    if(annType.startsWith("ne")) {
                        String attName=props.get("type");
                        
                        // ULTRA HACK
                        if(attName.equalsIgnoreCase("phone_number"))
                            attName="phone";
                        else if(attName.equalsIgnoreCase("zip_code"))
                            attName="zip";
                        else if(attName.equalsIgnoreCase("address"))
                            attName="street";
                        else if(attName.equalsIgnoreCase("degree-title"))
                            attName="title";
                        else if(attName.equalsIgnoreCase("job-title"))
                            attName="job";
                      
                        // END OF UH
                        
                        AttributeDef ad=model.getAttribute(attName);
                        if(ad==null) {
                            ad=new AttributeDefImpl(attName, "string");
                            log.LG(Logger.USR,"Adding attribute definition "+ad);
                            model.addAttribute(ad);
                        }
                        double conf=1.0;
                        String confStr=props.get("confidence");
                        if(confStr!=null) {
                            conf=Double.parseDouble(confStr);
                        }
                        AttributeValue av=new AttributeValueImpl(ad, null, conf, null, offset, len, goldStandardAuthor);
                        id2av.put(id, av);
                        doc.getAttributeValues().add(av);
                    }else if(annType.equals("token")) {
                        // TODO: store POS tags, lookup hits etc.
                    }else if(annType.equals("sentence")) {
                        // TODO: store member tokens, possibly chunks if info present
                    }else {
                        log.LG(Logger.WRN,"Unknown annotation type="+annType);
                    }
                    continue;
                }
                m=patInstHdr.matcher(s);
                if(m.find()) {
                    anc2++;
                    String id=m.group(1);
                    String className=m.group(2);
                    int colPos=className.indexOf(':');
                    if(colPos!=-1) {
                        className=className.substring(colPos+1);
                        //String modelName=className.substring(0, colPos);
                    }
                    
                    // ULTRA HACK II.
                    if(className.equalsIgnoreCase("Organization_Contact_Table") || 
                       className.equalsIgnoreCase("Person_Contact_Table"))
                        className="Contact";
                  
                    // END OF UH
                    
                    //String modelName=m.group(2);
                    //String className=m.group(3);
                    ClassDef cd=model.getClass(className);
                    if(cd==null) {
                        cd=new ClassDefImpl(className, null);
                        log.LG(Logger.USR,"Adding class definition "+cd);
                        model.addClass(cd);
                    }
                    Instance inst=new InstanceImpl(id, cd);
                    inst.setAuthor(DocumentReader.goldStandardAuthor);
                    // indices - not needed so far, throw away:
                    Matcher m2=patInstIdxs.matcher(s);
                    int pos=m.end();
                    while(m2.find(pos)) {
                        String idx1Str=m2.group(1);
                        String idx2Str=m2.group(2);
                        pos=m2.end();
                    }
                    // we must store attribute references until all attribute definitions
                    // are read since attributes may be defined later than they are used in instances
                    String rhs = s.substring(m.end());
                    // may look like {{0 0}} {{person {GDM_STRING_SET 4}} {{job title} {GDM_STRING_SET 6}}}
                    // pre-process attribute slots containing spaces to contain - instead
                    Matcher m3=patCurlyNonEmptyLeaf.matcher(rhs);
                    StringBuffer rhs2=new StringBuffer(rhs.length());
                    int i=0;
                    while(m3.find()) {
                        rhs2.append(rhs.substring(i, m3.start()));
                        rhs2.append(m3.group().substring(1,m3.group().length()-1).trim().replaceAll(" ", "_"));
                        i=m3.end();
                    }
                    rhs2.append(rhs.substring(i));
                    instMembers.put(inst, rhs2.toString());
                    // id2inst.put(id, inst);
                    doc.getInstances().add(inst);
                    continue;
                }
                log.LG(Logger.WRN,"Unexpected: "+s);
            }
            
            // set assembled attributes of instances
            Iterator<Map.Entry<Instance, String>> eit=instMembers.entrySet().iterator();
            while(eit.hasNext()) {
                Map.Entry<Instance, String> en=eit.next();
                Instance inst=en.getKey();
                Matcher m=patInstAtt.matcher(en.getValue());
                int idx=0;
                while(m.find(idx)) {
                    String attName=m.group(1);
                    String dataType=m.group(2);
                    String val=m.group(3);
                    if(attName.equals("type")) {
                        // class name was originally set to something like "relations_crf",
                        // we need to use the type property's value instead:
                        if(!inst.getClassDef().getName().equals(val)) {
                            ClassDef cd=model.getClass(val);
                            if(cd==null) {
                                cd=new ClassDefImpl(val, null);
                            }
                            inst.setClassDef(cd);
                        }
                    }else if(attName.equals("confidence")) {
                        double conf=Double.parseDouble(val);
                        inst.setScore(conf);
                    }else {
                        Matcher mNaN=patNaN.matcher(val);
                        if(mNaN.find()) {
                           ; // this is just the string value of a member attribute value; we use its id only
                        }else {
                            String ids=val;
                            if(ids==null || ids.trim().length()==0) {
                                idx=m.end();
                                continue;
                            }
                            String[] idList=ids.split(" ");
                            for(int i=0;i<idList.length;i++) {
                                AttributeValue av=id2av.get(idList[i]);
                                if(av==null) {
                                    log.LG(Logger.WRN,"Attribute id="+idList[i]+" not found reading instance "+inst.getId()+" of class "+inst.getClassDef().getName());
                                    continue;
                                }
                                if(av.getInstance()!=null) {
                                    log.LG(Logger.WRN,doc.toString()+": attribute values belonging to multiple instances not supported: AV="+av+"\n Keeping old inst "+av.getInstance()+" discarding new "+inst);
                                }else {
                                    inst.getAttributes().add(av);
                                    av.setInstance(inst);
                                }
                            }
                        }
                    }
                    idx=m.end();
                }
            }

            
            if(s.startsWith("{Document Handler}")) {
                continue;
            }
            
            // read metadata
            String[] av=s.split("::",2);
            if(av.length!=2) {
                if(force) {
                    log.LG(Logger.WRN,"Ignoring: "+s);
                }else {
                    log.LG(Logger.TRC,"Document seems not to be in dat format: "+s);
                    return null;
                }
                continue;
            }
            String a=av[0]; String v=av[1];
            log.LG(Logger.TRC,"Processing "+a+"="+v);
            if(a.equals("Encoding")) {
                if(!doc.getEncoding().equals(v)) {
                    log.LG(Logger.WRN,"Found encoding "+v+" expected "+doc.getEncoding());
                }
            }
            else if(a.equals("ExternalID")) {
                doc.setUrl(v);
            }
            else if(a.equals("RawData")) {
                charCnt=Integer.parseInt(v);
                break;
            }
            else if(a.equals("Annotations")) {
                anc1=Integer.parseInt(v);
            }
            else if(a.equals("Attributes")) {
                int attCnt=Integer.parseInt(v);
                // ignore dat label definitions
                for(int i=0;i<attCnt;i++) {
                    s=br.readLine();
                }
                continue;
            }
        }
        int r=-1;
        int rsz=0;
        while((r=br.read(cb))!=-1) {
            cb.flip();
            buff.append(cb);
            cb.clear();
            rsz+=r;
            // log.LG(Logger.TRC,"Read "+rsz+"/"+charCnt+"; buff.length="+buff.length());
        }
        int suffixLimit=50;
        int si=Math.max(buff.length()-suffixLimit, 0);            
        String suffix=buff.substring(si);
        int idx=suffix.indexOf("## - End of File -");
        int ei=(idx==-1)? buff.length(): si+idx;
        String src=buff.substring(0,ei);
        buff.setLength(0);

        // annot tool requires \r\n but treats them as 1 char
        src=src.replace("\r\n", "\n");
        int realCharCnt=src.length();
        doc.setSource(src);
        
        if(realCharCnt==charCnt || 
         ((realCharCnt==(charCnt+1)) && src.length()>0 && src.charAt(src.length()-1)=='\n')) {
            ; // ok
        }else {
            log.LG(Logger.TRC,"doc size="+realCharCnt+" chars, expected "+charCnt+": size="+ei+" with \\r\\n counted as 2, read total "+rsz+" chars till file end");
            log.LG(Logger.TRC,"doc ending: '"+doc.getSource().substring(Math.max(0, doc.getSource().length()-10))+"'");
        }
        populateAttributeTexts(doc); // just to check that text is the same except for replacements
        repairEncodingErrors(doc);
        populateAttributeTexts(doc);
        
        // AVs that are instance members are not stored in doc's global list of AVs:
        Iterator<AttributeValue> avit = doc.getAttributeValues().iterator();
        while(avit.hasNext()) {
            AttributeValue av=avit.next();
            if(av.getInstance()!=null) {
                avit.remove();
            }
        }
        return doc;
    }
    
    public static void main(String[] args) throws IOException {
        if(args.length==0) {
            System.err.println("Usage: DatDocumentReader <document> [<document2> ...]");
            System.exit(-1);
        }
        DataModel model=new DataModelImpl("no_url", "test");
        DatDocumentReader dr=new DatDocumentReader();
        List<Document> docs=new ArrayList<Document>(args.length);
        for(int i=0;i<args.length;i++) {
            System.err.println("Reading document "+args[i]);
            Document doc=null;
            try {
                doc = dr.readDocument(args[i], "utf-8", model, null, true);
                docs.add(doc);
            }catch (IOException ex) {
                System.err.println("Cannot read document "+args[i]+": "+ex);
            }
        }
        System.err.println("Read "+docs.size()+" docs");
        
        DatDocumentLabeler dlab=new DatDocumentLabeler();
        for(Document doc: docs) {
            String fn=doc.getFile()+".fixed.html";
            System.err.println("Writing "+doc+" to "+fn);
            dlab.annotateDocument(doc);
            dlab.writeAnnotatedDocument(fn, "utf-8");
            dlab.clear();
        }
        return;
    }

    public void setAttributePreprocessor(AttributePreprocessor ap) {
        atrPreprocessor=ap;
    }
    
// This is the result of opening a utf-8 doc as win-1252 and then saving it as utf-8...
// accented char and the image of its screwed 4-6 byte encoding decoded back to utf-8:
    private static String[] fix={
        "ö","Ã¶",
        "ä","Ã¤",
        "ü","Ã¼",
        "Ö","Ã–",
        "Ä","Ã„",
        "Ä","Ã\u0084",
        "Ü","Ãœ",
        "ß","Ã\u009F",
        "-","â\u0080\u0093",
        " ","Â",
        " ","â"
    };
    private static Pattern patFixEnc;
    private static Map<String,String> fixEncMap;
    static {
        StringBuffer b=new StringBuffer(64);
        b.append('(');
        fixEncMap=new HashMap<String,String>(32);
        for(int i=0;i<fix.length;i+=2) {
            if(i!=0)
                b.append('|');
            fixEncMap.put(fix[i+1], fix[i]);
            b.append(fix[i+1]);
        }
        b.append(')');
        patFixEnc=Pattern.compile(b.toString());
    }
    public void repairEncodingErrors(Document doc) {
        String src=doc.getSource();
        StringBuffer b2=new StringBuffer(src.length());
        Matcher m=patFixEnc.matcher(src);
        int i=0;
        List<Integer> offsets=new ArrayList<Integer>(32);
        List<Integer> shifts=new ArrayList<Integer>(32);
        int accumShift=0;
        while(m.find()) {
            // 1. fix document source
            int spos=m.start();
            String repl=fixEncMap.get(m.group());
            int shift = repl.length() - (m.end()-m.start());
            b2.append(src.substring(i, spos));
            b2.append(repl);
            i=m.end();
            // 2. assemble information to shift annotations
            if(shift!=0) {
                offsets.add(spos);
                accumShift+=shift;
                shifts.add(accumShift);
            }
        }
        if(b2.length()>0) {
            b2.append(src.substring(i));
            doc.setSource(b2.toString());
        }
        // shift annotations
        if(offsets.size()>0 && doc.getAttributeValues().size()>0) {
            List<Annotation> ans=new ArrayList<Annotation>(doc.getAttributeValues().size());
            for(AttributeValue av: doc.getAttributeValues()) {
                for(Annotation an: av.getAnnotations()) {
                    ans.add(an);
                }
            }
            Collections.sort(ans, new Comparator<Annotation>() {
                public int compare(Annotation o1, Annotation o2) {
                    int rc=o1.getStartOffset()-o2.getStartOffset();
                    if(rc==0)
                        rc=o1.getLength()-o2.getLength();
                    return rc;
                }});
            int curShiftIdx=-1;
            int shift=0;
            for(Annotation an: ans) {
                int si=an.getStartOffset();
                int ei=si+an.getLength();
                // find out shift at start of this annotation
                while(curShiftIdx<offsets.size()-1 && offsets.get(curShiftIdx+1)<si) {
                    curShiftIdx++;
                    shift=shifts.get(curShiftIdx);
                }
                log.LG(Logger.TRC,"Annot1: "+an);
                if(shift!=0) {
                    an.setStartOffset(an.getStartOffset()+shift);
                }
                // find out shift at end of this annotation
                int shiftEnd=shift;
                while(curShiftIdx<offsets.size()-1 && offsets.get(curShiftIdx+1)<ei) {
                    curShiftIdx++;
                    shift=shifts.get(curShiftIdx);
                }
                if(shift!=shiftEnd) {
                    an.setLength(an.getLength()+shift-shiftEnd);
                }
                String text=doc.getSource().substring(an.getStartOffset(), an.getStartOffset()+an.getLength());
                an.setText(text);
                log.LG(Logger.TRC,"Annot2: "+an+"\n");
            }
        }
    }
    
    public void populateAttributeTexts(Document doc) {
        String src=doc.getSource();
        String rev=Util.reverse(src);
        Iterator<AttributeValue> avit=doc.getAttributeValues().iterator();
        while(avit.hasNext()) {
            AttributeValue av=avit.next();
            Iterator<Annotation> anit=av.getAnnotations().iterator();
            int i=0;
            while(anit.hasNext()) {
                Annotation an=anit.next();
                int si=an.getStartOffset();
                int ei=si+an.getLength();
                String text=null;
                if(si>=0 && ei>=0 && si<ei && si<src.length() && ei<src.length()) {
                    text=src.substring(si, ei);
                    if(discardOutOfTextAnnotations) {
                        Matcher m=patInTagRight.matcher(src);
                        if(m.find(ei)) {
                            if(m.start()==ei) {
                                Matcher m2=patInTagLeft.matcher(rev);
                                if(m2.find(src.length()-si)) {
                                    if(m2.start()==src.length()-si) {
                                        log.LG(Logger.WRN, "Discarded intra-tag annotation: "+text);
                                        anit.remove();
                                        if(av.getAnnotations().size()==0)
                                            avit.remove();
                                        break;                                        
                                    }
                                }
                            }
                        }
                    }
                    log.LG(Logger.TRC,av.getAttributeDef().getName()+"='"+text+"'");
                    an.setText(text);
                }else {
                    // throw new IllegalArgumentException("Malformed annotation in doc "+doc.getFile()+": indices out of document: start="+si+", len="+an.getLength());
                    log.LG(Logger.ERR,"Malformed annotation in doc "+doc.getFile()+": indices invalid or out of document: start="+si+", len="+an.getLength()+", doclen="+doc.getSource().length());
                }
                if(i==0) {
                    av.setText(text);
                }else {
                    log.LG(Logger.ERR,doc.getFile()+": Ignored annotation #"+i+": av="+av+" start="+si+", len="+an.getLength()+", doclen="+doc.getSource().length());
                }
                i++;
            }
            if(atrPreprocessor!=null) {
                atrPreprocessor.preprocess(doc, av);
            }
        }
    }

    public DocumentFormat getFormat() {
        return DocumentFormat.formatForId(DocumentFormat.ELLOGON);
    }
}

// $Id: ModelReader.java 1696 2008-10-19 23:06:09Z labsky $
package ex.model;

import java.util.*;
import java.io.*;
import java.util.regex.*;
import uep.util.*;

import gnu.trove.TIntArrayList;
import ex.util.CaseUtil;
import ex.util.pr.PR_Evidence;
import ex.util.pr.PR_EvidenceGroup;
import ex.features.*;
import ex.ac.*;
import ex.train.KB;
import ex.reader.SemAnnot;
import ex.reader.Tokenizer;
import ex.reader.GrammarTokenizer;
import ex.util.pd.*;
import org.w3c.dom.*;

//tuva: ll=51.717110,94.453754 spn=0.102482,0.107460

public class ModelReader {
    protected BetterDOMParser parser;
    protected PatComp patComp;
    protected Logger log;
    protected String defPatEnc="utf-8";
    protected Tokenizer tokenizer;
    private Options o;
    private String modelPath=".";
    private String baseUrl=".";
    private StringBuffer dates;
    private int importDepth;
    private boolean classifierOnly;
    protected HashMap<String,String> entityMap;
    
    private static final char DIST_VAL=0; 
    private static final char DIST_CARD=1;
    private static final char DIST_LEN=2;
    
    protected static String POST_PROC_FLAG="@POPRC";

    public ModelReader() {
        log=Logger.getLogger("ModelReader");
        o=Options.getOptionsInstance();
        tokenizer=createTokenizer();
        String s=o.getProperty("model_encoding");
        if(s!=null)
            defPatEnc=s;
        classifierOnly=o.getIntDefault("classifier_only", 0)>0;
        parser=new BetterDOMParser();
        parser.ignoreIgnorableWhitespace=false;
        parser.useUserData=false;
        patComp=new PatComp();
        importDepth=0;
        dates=new StringBuffer(128);
        // settings to be loaded before the model
        AttributeDef.PRUNE_PROB_DEFAULT=o.getDoubleDefault("ac_prune_threshold", AttributeDef.PRUNE_PROB_DEFAULT);
        
        entityMap=new HashMap<String,String>();
        entityMap.put("deg", "�");
    }

    public static Tokenizer createTokenizer() {
        Tokenizer tknz=null;
        String tokCls=Options.getOptionsInstance().getProperty("tokenizer");
        if(tokCls!=null) {
            try {
                tknz=(Tokenizer) Class.forName(tokCls).newInstance();
            }catch(Exception ex) {
                Logger.LOG(Logger.ERR,"Tokenizer class "+tokCls+" not instantiated, using GrammarTokenizer: "+ex.toString());
            }
        }
        if(tknz==null)
            tknz=new GrammarTokenizer(); // new SimpleTokenizer();
        return tknz;
    }
    
    public Model read(String modelFile, KB modelKb) throws org.xml.sax.SAXException, java.io.IOException, ModelException {
        modelFile=modelFile.trim();
        File fl=new File(modelFile);
        boolean usingCached=false;
        boolean cacheOn=o.getIntDefault("use_cache", 0)>0;
        if(cacheOn && importDepth==0) {
            boolean ok=checkDates(getTimeStampFile(fl));
            if(ok) {
                try {
                    log.LG(Logger.USR,"Loading KB...");
                    KB kb=KB.load(getBinKBFile(fl).getCanonicalPath());
                    modelKb.copyFrom(kb);
                    FM.getFMInstance().registerKB(modelKb);
                    usingCached=true;
                }catch(Exception ex) {
                    log.LG(Logger.ERR,"Could not load cached KB from "+getBinKBFile(fl).getCanonicalPath());
                }
            }
            dates.setLength(0);
        }
        dates.append(modelFile+"\t"+fl.lastModified()+"\n");
        modelPath=baseUrl=fl.getParent();
        parser.parse(modelFile);
        Document doc=parser.getDocument();
        NodeList nodes=doc.getElementsByTagName("class");
        log.LG(Logger.INF,"Reading "+nodes.getLength()+" class(es)...");
        Model m=dom2model(doc, modelKb);
        m.prepare();
        /* add phrase count features for each model attribute */
        FM fm=FM.getFMInstance();
        fm.model=m;
        for(int i=0;i<m.classArray.length;i++) {
            ClassDef cls=m.classArray[i];
            for(int j=0;j<cls.attArray.length;j++) {
                PhraseCntAsAttF f=new PhraseCntAsAttF(-1,cls.attArray[j]);
                fm.registerFeature(f);
                cls.attArray[j].phraseCntF=f;
            }
        }
        if(cacheOn && importDepth==0 && !usingCached) {
            log.LG(Logger.USR,"Saving KB...");
            writeDates(getTimeStampFile(fl));
            modelKb.save(getBinKBFile(fl).getCanonicalPath());
        }
        return m;
    }

    protected File getTimeStampFile(File modelFile) throws IOException {
        return new File(new File(modelFile.getCanonicalPath()).getParent(), "."+modelFile.getName()+".timestamps");
    }

    protected File getBinKBFile(File modelFile) throws IOException {
        return new File(new File(modelFile.getCanonicalPath()).getParent(), "."+modelFile.getName()+".bin");
    }

    //  resolve relative path to resource or imported model
    protected String resolveRelativePath(String pathName) throws IOException {
        File f=new File(pathName);
        if(!f.isAbsolute()) {
            f=new File(baseUrl, pathName);
            pathName=f.getCanonicalPath();
        }
        return pathName;
    }

    // write down last modified times of this model and all referenced files - we could load from binaries next time
    protected void writeDates(File tf) {
        try {
            tf.createNewFile();
            FileWriter fw=new FileWriter(tf,false);
            String d=dates.toString();
            log.LG(Logger.USR,"Writing '"+d+"'");
            fw.write(d,0,d.length());
            fw.flush();
            fw.close();
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error writing "+tf.getName()+": "+ex);
        }
    }

    protected boolean checkDates(File tf) {
        int ok=0;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(tf)));
            String line;
            int lno=0;
            while((line=br.readLine())!=null) {
                lno++;
                String[] fileStamp=line.split("\t");
                if(fileStamp.length==2) {
                    File resFile=new File(fileStamp[0]);
                    long lastTime=-1;
                    try {
                        lastTime=Long.parseLong(fileStamp[1]);
                    }catch(NumberFormatException ex) {
                        log.LG(Logger.WRN,"Error reading timestamp "+fileStamp[1]+" from "+tf.getCanonicalPath()+": "+ex);
                        return false;
                    }
                    if(lastTime!=resFile.lastModified())
                        return false;
                    ok++;
                }
            }
        }catch(IOException ex) {
            log.LG(Logger.TRC,"Timestamp file "+tf.getName()+" not found: "+ex);
            return false;
        }
        return (ok>0);
    }

    public Model dom2model(Document doc, KB modelKb) throws org.xml.sax.SAXException, java.io.IOException, ModelException {
        Element root=doc.getDocumentElement();
        String modelName=root.getAttribute("name");
        Model m=new Model(modelName, modelKb);
        // get units, datatypes, imports
        NodeList rootChildren=root.getChildNodes();
        int rootCnt=rootChildren.getLength();
        for(int i=0; i<rootCnt; i++) {
            Node node=rootChildren.item(i);
            if(node.getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element elem=(Element)node;
            String elemName=elem.getTagName();
            if(elemName.equalsIgnoreCase("units")) { // register all units with model
                registerUnits(m, elem);
            }else if(elemName.equalsIgnoreCase("datatypes")) { // register datatype patterns with model
                registerDataTypes(m, elem);
            }else if(elemName.equalsIgnoreCase("import")) { // import all elements from another model (there must be no conflicting names)
                importModel(m, elem, modelKb);
            }else if(elemName.equalsIgnoreCase("base")) {
                String baseUrlAtt=elem.getAttribute("url");
                if(baseUrlAtt.length()>0) {
                    // if relative, make absolute (treat it relative to the model dir not current dir)
                    File f=new File(baseUrlAtt);
                    if(!f.isAbsolute()) {
                        f=new File(modelPath, baseUrlAtt);
                        baseUrlAtt=f.getCanonicalPath();
                    }
                    baseUrl=baseUrlAtt;
                }
            }else if(elemName.equalsIgnoreCase("script")) {
                registerScript(m, elem, "");
            }else if(elemName.equalsIgnoreCase("classifier")) {
                registerClassifier(m, elem, null);
            }else if(elemName.equalsIgnoreCase("finally")) {
                registerPostprocessing(m, elem);
            }
        }
        // get own classes
        NodeList classElems=root.getElementsByTagName("class");
        int classCnt=classElems.getLength();
        for(int i=0; i<classCnt; i++) {
            Element classElem=(Element)classElems.item(i);
            registerClass(m, classElem);
        }
        return m;
    }

    /** Get post-processing scripted rules. 
     * @throws ModelException */
    private void registerPostprocessing(Model m, Element elem) throws ModelException {
        NodeList children=elem.getChildNodes();
        int cnt=children.getLength();
        for(int i=0; i<cnt; i++) {
            Node node=children.item(i);
            if(node.getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element ch=(Element)node;
            String chName=ch.getTagName();
            if(chName.equalsIgnoreCase("script")) {
                registerScript(m, ch, POST_PROC_FLAG);
            }else {
                throw new ModelException("Unknown tag "+chName+" in "+elem.getTagName());
            }
        }
    }

    public void importModel(Model m, Element importElem, KB kb) throws org.xml.sax.SAXException, java.io.IOException, ModelException {
        String importedFile=importElem.getAttribute("model");
        if(importedFile.length()==0)
            throw new ModelException("Attribute model of <import> must be specified!");
        // resolve relative path to imported model
        File mf=new File(importedFile);
        if(!mf.isAbsolute()) {
            mf=new File(baseUrl,importedFile);
            importedFile=mf.getCanonicalPath();
        }
        mf=null;
        // backup path to current importing model
        String tmp=modelPath;
        String tmp2=baseUrl;
        // backup current parser
        BetterDOMParser prs=parser;
        parser=new BetterDOMParser();
        parser.useUserData=false;
        parser.ignoreIgnorableWhitespace=false;
        importDepth++;
        Model im=read(importedFile, kb);
        importDepth--;
        parser=prs;
        modelPath=tmp;
        baseUrl=tmp2;
        m.importFrom(im);
    }

    public void registerDataTypes(Model m, Element dtElem) throws ModelException {
        NodeList childList=dtElem.getChildNodes();
        for(int i=0;i<childList.getLength();i++) {
            if(childList.item(i).getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element el=(Element)childList.item(i);
            if(!el.getTagName().equals("pattern")) {
                continue;
            }
            // registers a generic datatype pattern here when ModelElement==null:
            TokenPattern dtPat=readPattern(el, TokenPattern.PAT_GEN, m, null, Model.DATATYPE_PREFIX, i);
        }
    }

    public void registerUnits(Model m, Element unitsElem) throws ModelException {
        NodeList quantList=unitsElem.getElementsByTagName("quantity");
        int quantCnt=quantList.getLength();
        Vector<Unit> units=null;
        for(int i=0; i<quantCnt; i++) {
            // register new quantity
            Element quantity=(Element)quantList.item(i);
            String qid=quantity.getAttribute("id");
            String baseUnit=quantity.getAttribute("baseUnit");
            if(m.quantities.containsKey(qid)) {
                throw(new ModelException("duplicit quantities with id="+qid));
            }
            units=new Vector<Unit>(10);
            m.quantities.put(qid, units);
            // register units with quantity
            NodeList unitList=quantity.getElementsByTagName("unit");
            int unitCnt=unitList.getLength();
            for(int j=0; j<unitCnt; j++) {
                Element unitElem=(Element)unitList.item(j);
                String id=unitElem.getAttribute("id");
                String cvrt=unitElem.getAttribute("convert");
                double ratio=-1.0;
                if(cvrt!=null && cvrt.length()>0) {
                    try {
                        ratio=(new Double(cvrt)).doubleValue();
                    }catch(NumberFormatException ex) {
                        throw(new ModelException("Unit id="+id+": cannot convert "+cvrt+" to double: "+ex.getMessage()));
                    }
                }
                Unit unit=new Unit(id,null,ratio);
                units.add(unit);
                m.units.put(id,unit);
            }
        }
        log.LG(Logger.INF,"Units read: "+m.units.size());
    }

    private void registerClass(Model m, Element classElem) throws ModelException {
        String disabled=classElem.getAttribute("enabled").trim().toLowerCase();
        if(disabled.equals("0")||disabled.equals("no")||disabled.equals("false"))
            return;
        String className=classElem.getAttribute("id");
        ClassDef cls=new ClassDef(className, m);
        // prune probability threshold
        String pruneStr=classElem.getAttribute("prune");
        if(pruneStr.length()!=0) {
            cls.pruneProb=readDouble(pruneStr);
        }
        // min/max count per document
        int[] cards=readCard(classElem.getAttribute("counts"));
        if(cards!=null && (cards[0]>0 || (cards[1]>0 && cards[1]!=Integer.MAX_VALUE))) {
            cls.countDist=new IntDistribution(cards[0], cards[1]);
        }
        // add attributes
        NodeList attList=classElem.getElementsByTagName("attribute");
        int attCnt=attList.getLength();
        for(int aidx=0; aidx<attCnt; aidx++) {
            Element attElem=(Element)attList.item(aidx);
            registerAttribute(m, cls, attElem);
        }
        // ordering and context patterns for attributes
        NodeList childList=classElem.getChildNodes();
        for(int eidx=0;eidx<childList.getLength();eidx++) {
            if(childList.item(eidx).getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element elem=(Element) childList.item(eidx);
            if(elem.getTagName().equals("pattern") || elem.getTagName().equals("axiom")) {
                String namePath=cls.name;
                TokenPattern pat=readPattern(elem, TokenPattern.PAT_CLS, m, cls, namePath, eidx);
                if(pat!=null)
                    cls.ctxPatterns.add(pat); // both ctx and value patterns stored here
            }else if(elem.getTagName().equals("script")) {
                registerScript(m, elem, "");
            }else if(elem.getTagName().equals("classifier")) {
                registerClassifier(m, elem, cls);
            }else if(elem.getTagName().equals("attribute")) {
                ; // already handled above
            }else {
                throw new ModelException("Unknown tag in class: "+elem.getTagName());
            }
        }
        // resolve unresolved references to attributes
        try {
            patComp.resolveReferences(cls);
        }catch(TokenPatternSyntaxException e) {
            throw new ModelException("Can't resolve attribute references for class "+cls.name+": "+e);
        }
        m.addClass(cls);
        log.LG(Logger.INF,"Class "+className+": "+attCnt+" attributes read.");
    }
    
    private void registerAttribute(Model m, ClassDef cls, Element attElem) throws ModelException {
        String disabled=attElem.getAttribute("enabled").trim().toLowerCase();
        if(disabled.equals("0")||disabled.equals("no")||disabled.equals("false"))
            return;
        // name
        String attName=attElem.getAttribute("id");
        if(attName.length()==0) {
            String msg="Attribute id cannot be empty in class '"+cls.name+"'";
            log.LG(Logger.ERR,msg);
            throw(new ModelException(msg));
        }
        AttributeDef attDef=new AttributeDef(attName,-1,cls);
        attDef.logLevel=(short) readIntMagic(attElem.getAttribute("log"));
        // abstract and extends
        String abstractTypeStr=attElem.getAttribute("abstract");
        if(abstractTypeStr.length()!=0) {
            abstractTypeStr=abstractTypeStr.trim();
            if(abstractTypeStr.equals("1")) {
                attDef.abstractType=1;
            }
        }
        String extendsStr=attElem.getAttribute("extends");
        if(extendsStr.length()!=0) {
            extendsStr=extendsStr.trim();
            if(!cls.attributes.containsKey(extendsStr)) {
                String msg="Attribute "+attName+": extends unknown base attribute '"+extendsStr+"'";
                log.LG(Logger.ERR,msg);
                throw(new ModelException(msg));
            }
            attDef.parent=(AttributeDef)cls.attributes.get(extendsStr);
        }
        // data type
        String attType=attElem.getAttribute("type");
        if(attType.length()!=0) {
            attType=attType.trim().toUpperCase();
            int dataType=-1;
            for(int j=0;j<AttributeDef.dataTypes.length;j++)
                if(attType.equals(AttributeDef.dataTypes[j]))
                    dataType=j;
            if(dataType==-1) {
                String msg="Attribute "+attName+": has unknown type '"+attType+"' in class '"+cls.name+"'";
                log.LG(Logger.ERR,msg);
                throw(new ModelException(msg));
            }
            attDef.dataType=dataType;
        }else if(attDef.parent!=null) {
            attDef.dataType=attDef.parent.dataType;
        }else {
            String msg="Attribute "+attName+": type is empty in '"+cls.name+"'";
            log.LG(Logger.ERR,msg);
            throw(new ModelException(msg));
        }
        // dbname
        attDef.dbName=null;
        String dbName=attElem.getAttribute("dbname");
        if(dbName.length()!=0) {
            attDef.dbName=dbName.trim().toUpperCase();
        }
        // dimension
        String attDim=attElem.getAttribute("dim");
        if(attDim.length()!=0) {
            attDef.dim=readInt(attDim);
        }else {
            attDef.dim=1;
        }
        // engaged probability
        String engStr=attElem.getAttribute("eng");
        if(engStr.length()!=0) {
            attDef.engagedProb=readDouble(engStr);
        }
        // prune probability threshold
        String pruneStr=attElem.getAttribute("prune");
        if(pruneStr.length()!=0) {
            attDef.pruneProb=readDouble(pruneStr);
        }
        // color
        String clr=attElem.getAttribute("color");
        if(clr.length()!=0)
            attDef.setColor(clr);
        // cardinality, optionality
        int[] cards=readCard(attElem.getAttribute("card"));
        if(cards!=null) {
            attDef.cardDist=new IntDistribution(cards[0], cards[1]);
            attDef.minCard=attDef.cardDist.getMinIntValue();
            attDef.maxCard=attDef.cardDist.getMaxIntValue();
        }else if(attDef.parent==null) {
            throw new ModelException("Cardinality not specified for non-inherited attribute '"+attDef.name+"'");
        }
        // card will be taken from parent in AttributeDef.prepare()
        
        // units (list of unit ids)
        String unitStr=attElem.getAttribute("units");
        if(unitStr.length()!=0) {
            String[] lst=unitStr.split("\\s*,\\s*");
            Unit[] units=new Unit[lst.length];
            for(int j=0;j<lst.length;j++) {
                if(!m.units.containsKey(lst[j])) {
                    String msg="Attribute "+attName+": unknown unit '"+lst[j]+"'";
                    log.LG(Logger.ERR,msg);
                    throw(new ModelException(msg));
                }
                units[j]=(Unit)m.units.get(lst[j]);
            }
            attDef.units=units;
        }else if(attDef.parent!=null) {
            attDef.units=attDef.parent.units;
        }
        // prior probability
        double prior=readDouble(attElem.getAttribute("prior"));
        if(prior>0 && prior<1)
            attDef.prClass.setPrior(prior);

        /* get child elements of attribute: pattern|context|value|axiom */
        StringBuffer buff=new StringBuffer(256);

        /* GENERAL PURPOSE PATTERNS: attribute->pattern */
        NodeList childList=attElem.getChildNodes();
        for(int i=0;i<childList.getLength();i++) {
            if(childList.item(i).getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element el=(Element)childList.item(i);
            if(el.getTagName().equals("pattern")) {
                // reads and registers a pattern
                TokenPattern pat=readPattern(el, TokenPattern.PAT_GEN, m, attDef, attDef.getFullName(), i);
            }else if(el.getTagName().equals("classifier")) {
                registerClassifier(m, el, attDef);
            }
        }

        /* ATTRIBUTE VALUE: patterns, length constraint, value distribution, <contains> section */
        childList=attElem.getElementsByTagName("value");
        for(int i=0;i<childList.getLength();i++) {
            Element val=(Element)childList.item(i);
            NodeList inList=val.getChildNodes();
            for(int j=0;j<inList.getLength();j++) {
                if(inList.item(j).getNodeType()!=Node.ELEMENT_NODE)
                    continue;
                Element el=(Element)inList.item(j);
                String elName=el.getTagName();
                if(elName.equals("pattern")) { // || elName.equals("axiom")) {
                    TokenPattern pat=readPattern(el, TokenPattern.PAT_VAL, m, attDef, attDef.getFullName(), j);
                    if(pat!=null)
                        attDef.addPattern(pat);
                }else if(elName.equals("or")) {
                    readGroup(el, PR_EvidenceGroup.GRP_OR, TokenPattern.PAT_VAL, m, attDef, attDef.getFullName(), j);
                }else if(elName.equals("and")) {
                    readGroup(el, PR_EvidenceGroup.GRP_AND, TokenPattern.PAT_VAL, m, attDef, attDef.getFullName(), j);
                }else if(elName.equals("length")) {
                    NodeList lst=el.getElementsByTagName("distribution");
                    if(lst.getLength()>0) {
                        readDist((Element) lst.item(0), DIST_LEN, attDef);
                        if(lst.getLength()>1) {
                            log.LG(Logger.ERR,parser.getInfoString(el)+": cannot specify more than 1 distribution for attribute length; using the 1st one");
                        }
                    }                            
                }else if(elName.equals("distribution")) {
                    readDist(el, DIST_VAL, attDef);
                }else if(elName.equals("card")) {
                    NodeList lst=el.getElementsByTagName("distribution");
                    if(lst.getLength()>0) {
                        readDist((Element) lst.item(0), DIST_CARD, attDef);
                        if(lst.getLength()>1) {
                            log.LG(Logger.ERR,parser.getInfoString(el)+": cannot specify more than 1 distribution for attribute cardinality; using the 1st one");
                        }
                    }
                }else if(elName.equals("contains")) {
                    readContains(el, attDef);
                }else if(elName.equals("axiom") || elName.equals("refers") || elName.equals("transform")) {
                    ; // these tags are registered below at once
                }else {
                    log.LG(Logger.WRN,"Ignoring unexpected tag "+elName+" under value");
                }
            }
        }

        /* ATTRIBUTE CONTEXT: patterns */
        childList=attElem.getElementsByTagName("context");
        for(int i=0;i<childList.getLength();i++) {
            Element ctx=(Element)childList.item(i);
            NodeList inList=ctx.getChildNodes();
            for(int j=0;j<inList.getLength();j++) {
                if(inList.item(j).getNodeType()!=Node.ELEMENT_NODE)
                    continue;
                Element el=(Element)inList.item(j);
                String elName=el.getTagName();
                if(elName.equals("pattern")) {
                    TokenPattern pat=readPattern(el, TokenPattern.PAT_CTX_LR, m, attDef, attDef.getFullName(), j);
                    if(pat!=null)
                        attDef.addPattern(pat);
                }else if(elName.equals("or")) {
                    readGroup(el, PR_EvidenceGroup.GRP_OR, TokenPattern.PAT_CTX_LR, m, attDef, attDef.getFullName(), j);
                }else if(elName.equals("and")) {
                    readGroup(el, PR_EvidenceGroup.GRP_AND, TokenPattern.PAT_CTX_LR, m, attDef, attDef.getFullName(), j);
                }else if(elName.equals("axiom") || elName.equals("refers") || elName.equals("transform")) {
                    ; // these tags are registered below at once
                }else {
                    log.LG(Logger.WRN,"Ignoring unexpected tag "+elName+" under context");
                }
            }
        }

        /* ATTRIBUTE AXIOMS and value TRANSFORMs */
        String[] scriptTags={"axiom", "transform", "refers"};
        byte[] scriptTypes={Axiom.TYPE_AXIOM, Axiom.TYPE_TRANSFORM, Axiom.TYPE_REFERS};
        for(int k=0;k<scriptTags.length;k++) {
            String tagName=scriptTags[k];
            childList=attElem.getElementsByTagName(tagName);
            for(int i=0;i<childList.getLength();i++) {
                Element axElem=(Element) childList.item(i);
                String axId=axElem.getAttribute("id");
                if(axId.length()==0) {
                    XMLLocInfo info=parser.getInfo(axElem);
                    int patNumId=(info!=null)? info.line: i;
                    axId="SCR_"+attDef.getName()+"_"+patNumId;
                }
                byte condType=condType2Code(axElem.getAttribute("cond"));
                String condSrc=null;
                NodeList cdataList=axElem.getChildNodes();
                for(int j=0;j<cdataList.getLength();j++) {
                    switch(cdataList.item(j).getNodeType()) {
                    case Node.TEXT_NODE:
                    case Node.CDATA_SECTION_NODE:
                        break;
                    case Node.ELEMENT_NODE: {
                        Element cel=(Element) cdataList.item(j);
                        if(cel.getTagName().equalsIgnoreCase("cond")) {
                            if(condSrc!=null)
                                throw new ModelException("Multiple "+tagName+" conditions not allowed; previous="+condSrc);
                            condSrc=cel.getTextContent();
                            condType=Axiom.AXIOM_COND_CUSTOM;
                        }
                    }
                    default:
                        continue;
                    }
                    String val=cdataList.item(j).getNodeValue();
                    buff.append(val);
                }

                Axiom ax=new Axiom(axId, buff.toString(), 0, attDef.name, scriptTypes[k], condType, condSrc);
                // register axiom, transform or refers script
                switch(scriptTypes[k]) {
                case Axiom.TYPE_AXIOM:
                    cls.addAxiom(ax);
                    buff.setLength(0);
                    m.addAxiom(ax);
                    break;
                case Axiom.TYPE_TRANSFORM:
                    attDef.transforms.add(ax);
                    break;
                case Axiom.TYPE_REFERS:
                    if(attDef.referScript!=null)
                        throw new ModelException("Only a single refers script may be specified for an attribute");
                    attDef.referScript=ax;
                    // abusing condText and condScript to store pre-compiled statement 
                    // used when evaluating reference detection script
                    ax.condType=Axiom.AXIOM_COND_NONE;
                    ax.condText="$other="+attDef.varName;
                    break;
                }
            }
        }

        /* register attribute with class */
        cls.addAttribute(attDef);
    }

    /** Reads and registers patterns contained within an OR group. OR groups
     * can appear in attribute's value and context sections. */
    private void readGroup(Element el, int type, short patCtx, Model m,
            AttributeDef attDef, String fullName, int order) throws ModelException {
        XMLLocInfo info=parser.getInfo(el);
        int orNumId=(info!=null)? info.line: order;
        double prec=readDouble(el.getAttribute("p"));
        double cover=readDouble(el.getAttribute("cover"));
        if(classifierOnly && !isNegative(el.getAttribute("feature"))) {
            prec=-1; cover=-1;
        }
        PR_EvidenceGroup grp=new PR_EvidenceGroup("GRP-"+type+"-"+fullName+"-"+orNumId, type, prec, cover, (byte)0, -1);
        NodeList orList=el.getChildNodes();
        for(int k=0;k<orList.getLength();k++) {
            if(orList.item(k).getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element patElem=(Element)orList.item(k);
            String patElName=patElem.getTagName();
            if(patElName.equals("pattern")) {
                TokenPattern pat=readPattern(patElem, patCtx, m, attDef, attDef.getFullName(), order+k);
                if(pat!=null) {
                    if(type==PR_EvidenceGroup.GRP_AND)
                        pat.flags |= TokenPattern.FLAG_AND_GROUP_MEMBER;
                    else if(type==PR_EvidenceGroup.GRP_OR)
                        pat.flags |= TokenPattern.FLAG_OR_GROUP_MEMBER;
                    attDef.addPattern(pat);
                    grp.evList.add(pat.evidence);
                }
            }else {
                log.LG(Logger.WRN,"Ignoring unexpected tag "+patElName+" under OR group");
            }
        }
        attDef.prClass.addEvidenceGroup(grp);
    }

    private void registerClassifier(Model m, Element el, ModelElement parElem) throws ModelException {
        ClassifierDef cs=null;
        try {
            String modelFile = resolveRelativePath(el.getAttribute("model"));
            double minConfidence = el.getAttribute("confidencethreshold").trim().length()>0? new Double(el.getAttribute("confidencethreshold")): -1;
            cs=new ClassifierDef(el.getAttribute("id"), el.getAttribute("name"), minConfidence, 
                    modelFile, el.getAttribute("datasource"), el.getAttribute("classtype"),
                    el.getAttribute("elements"), parElem);
            NodeList childList=el.getChildNodes();
            for(int i=0;i<childList.getLength();i++) {
                if(childList.item(i).getNodeType()!=Node.ELEMENT_NODE)
                    continue;
                Element child=(Element) childList.item(i);
                if(child.getTagName().equals("options")) {
                    cs.options=(cs.options==null)? child.getTextContent(): 
                        (cs.options+"\n"+child.getTextContent());
                }else if(child.getTagName().equals("feature")) {
                    cs.addFeature(child.getAttribute("type"), child.getAttribute("len"), 
                         child.getAttribute("minocc"), child.getAttribute("mi"), child.getAttribute("maxcnt"), 
                         child.getAttribute("ignore"), child.getAttribute("pos"),
                         child.getAttribute("source"), child.getAttribute("book"), 
                         parser.getInfoString(child));
                }else if(child.getTagName().equals("param")) {
                    String v=child.getAttribute("value");
                    if(v.length()==0) {
                        v=child.getTextContent();
                    }
                    cs.params.put(child.getAttribute("name"), v);
                }else {
                    throw new ModelException("Unknown classifier child: "+child.getTagName());
                }
            }
        }catch(NumberFormatException ex) {
            throw new ModelException("Error reading classifier: "+ex);
        }catch(IOException ex) {
            throw new ModelException("Error accessing classifier model: "+ex);
        }
        m.addClassifier(cs);
    }

    private byte condType2Code(String type) throws ModelException {
        byte condType=Axiom.AXIOM_COND_NONE;
        String condText=type.trim().toLowerCase();
        if(condText.length()>0) {
            if(condText.equals("all"))
                condType=Axiom.AXIOM_COND_ALL;
            else if(condText.equals("any"))
                condType=Axiom.AXIOM_COND_ANY;
            else if(!condText.equals("none") && !condText.equals("custom"))
                throw new ModelException("Invalid Axiom cond value: "+condText+" (allowed=any|all|custom|none)");
        }
        return condType;
    }
    
    private void readDist(Element el, char ctx, AttributeDef attDef) throws ModelException {
        char distType=Distribution.TYPE_MINMAX;
        String sType=el.getAttribute("type");
        if(sType.length()!=0) {
            char distType2=DistributionFactory.string2Type(sType);
            if(distType2==Distribution.TYPE_UNKNOWN) {
                throw new ModelException("Unknown distribution type specified: "+sType);
            }else {
                distType=distType2;
            }
        }
        String sMin=el.getAttribute("min");
        String sMax=el.getAttribute("max");
        double min=-1.0;
        double max=-1.0;
        if(sMin.length()!=0)
            min=readDouble(sMin);
        if(sMax.length()!=0)
            max=readDouble(sMax);
        
        switch(ctx) {
        case DIST_VAL:
            attDef.valueDist=createDistribution(distType, Distribution.RANGE_UNKNOWN, min, max, el);
            attDef.minValue=min;
            attDef.maxValue=max;
            break;
        case DIST_LEN:
            attDef.lengthDist=(IntDistribution) createDistribution(distType, Distribution.RANGE_INT, min, max, el);
            attDef.minLength=(int) min;
            attDef.maxLength=(int) max;
            TokenPattern dataTypePat=null;
            try {
                dataTypePat=attDef.getDataTypePattern();
            }catch(TokenPatternSyntaxException ex) {
                log.LG(Logger.WRN,"Pattern not found for datatype "+attDef.dataType+" of attribute "+attDef.name);
            }
            TokenPattern custPat=null;
            if(dataTypePat!=null) {
                custPat=new TokenPattern(dataTypePat);
                custPat.minLen=attDef.minLength;
                custPat.maxLen=attDef.maxLength;
            }else {
                String src="<tok/>{"+attDef.minLength+","+attDef.maxLength+"}";
                String patId="pat_dtype_auto_"+attDef.name;
                int matchMode=TokenPattern.MATCH_IGNORE_LEMMA | TokenPattern.MATCH_IGNORE_CASE;
                custPat=new TokenPattern(patId, src, TokenPattern.PAT_VAL, attDef, matchMode, null, 0);
                try {
                    patComp.compile(custPat, null, attDef, attDef.myClass.model.kb, tokenizer);
                }catch(TokenPatternSyntaxException ex) {
                    throw new ModelException(parser.getInfoString(el)+": error compiling custom data type pattern: "+ex);
                }
            }
            custPat.evidence=new PR_Evidence("DATATYPE_"+attDef.name, -1.0, -1.0, (byte)0, -1);
            attDef.dataTypePattern=custPat;
            break;
        case DIST_CARD:
            if(min==-1.0 && max==-1.0) { // could have been defined earlier as the card attribute 
                min=attDef.cardDist.getMinValue();
                max=attDef.cardDist.getMaxValue();
            }
            attDef.cardDist=(IntDistribution) createDistribution(distType, Distribution.RANGE_INT, min, max, el);
            attDef.minCard=(int) min;
            attDef.maxCard=(int) max;
            break;
        }
    }
    
    protected Distribution createDistribution(char distType, char distRange, double min, double max, Element el) throws ModelException {
        Distribution dist=null;
        switch(distType) {
        case Distribution.TYPE_MINMAX:
            dist=DistributionFactory.createDistribution(distType, distRange, (int)min, (int)max, null);
            break;
        
        case Distribution.TYPE_TABLE: {
            String content=el.getTextContent();
            dist=DistributionFactory.createDistribution(distType, distRange, (int)min, (int)max, content);
            break;
        }

        case Distribution.TYPE_NORMAL:
        case Distribution.TYPE_MIXTURE: {
            String content=el.getTextContent();
            dist=DistributionFactory.createDistribution(distType, distRange, min, max, content);
            break;
        }
        }
        return dist;
    }
    
    protected int readInt(String str) throws ModelException {
        int n=-1;
        str=str.trim();
        if(str.equalsIgnoreCase("n") || str.equals("*")) {
            n=Integer.MAX_VALUE;
        }else {
            try {
                n=Integer.parseInt(str);
            }catch(NumberFormatException ex) {
                String msg="Number expected reading '"+str+"': "+ex.getMessage();
                log.LG(Logger.ERR,msg);
                throw(new ModelException(msg));
            }
        }
        return n;
    }

    protected double readDouble(String str) throws ModelException {
        double n=-1.0;
        str=str.trim();
        if(str.length()>0) {
            try {
                n=Double.parseDouble(str);
            }catch(NumberFormatException ex) {
                String msg="Number expected reading '"+str+"': "+ex.getMessage();
                log.LG(Logger.ERR,msg);
                throw(new ModelException(msg));
            }
        }
        return n;
    }

    public String readFile(String file, String enc) throws ModelException {
        if(enc.length()==0)
            enc=defPatEnc;
        File f=new File(file);
        dates.append(file+"\t"+f.lastModified()+"\n");
        int sz=(int)f.length();
        StringBuffer content=new StringBuffer(sz);
        int bs=512;
        char[] buff=new char[bs];
        int read=0;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f),enc), bs);
            while((read=br.read(buff,0,bs))>=0) {
                content.append(buff,0,read);
            }
        }catch(IOException ex) {
            throw new ModelException("Error reading "+file+": "+ex.toString());
        }
        return content.toString();
    }

    protected void readContains(Element el, AttributeDef attDef) throws ModelException {
        ArrayList<ContainedAttribute> alst=new ArrayList<ContainedAttribute>(16);
        NodeList attList=el.getChildNodes();
        for(int i=0;i<attList.getLength();i++) {
            if(attList.item(i).getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element attEl=(Element) attList.item(i);
            if(!attEl.getTagName().equals("att"))
                throw new ModelException("Unexpected element '"+attEl.getTagName()+"' in <contains>: expected <att>");
            String attName=attEl.getAttribute("ref");
            int[] cards=readCard(attEl.getAttribute("card"));
            if(cards==null)
                throw new ModelException("Cardinality must be specified <contains> section of attribute '"+attDef.name+"', att='"+attName+"'");
            double prec=readDouble(attEl.getAttribute("p"));
            double cover=readDouble(attEl.getAttribute("cover"));
            ContainedAttribute ca=new ContainedAttribute(null,cards[0],cards[1],prec,cover);
            ca.obj=attName;
            alst.add(ca);
        }
        int cnt=alst.size();
        attDef.containsList=new ContainedAttribute[cnt];
        for(int i=0;i<cnt;i++) {
            attDef.containsList[i]=(ContainedAttribute) alst.get(i);
        }
    }

    protected TokenPattern readTok(Element tokElem, Model m, ModelElement mElem) throws ModelException {
        TokenPattern subPat=null;
        StringBuffer buff=null;
        String ignore=tokElem.getAttribute("ignore").toLowerCase();
        // find text
        NodeList cdataList=tokElem.getChildNodes();
        for(int j=0;j<cdataList.getLength();j++) {
            switch(cdataList.item(j).getNodeType()) {
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                break;
            default:
                continue;
            }
            if(buff==null)
                buff=new StringBuffer(128);
            buff.append(cdataList.item(j).getNodeValue());
        }
        // text found
        Pattern regexp=null;
        if(buff!=null && buff.length()>0) {
            String content=buff.toString().trim();
            if(true) {
                try {
                    int flags=Pattern.CANON_EQ;
                    if(ignore.indexOf("case")!=-1) {
                        flags|=Pattern.CASE_INSENSITIVE;
                        flags|=Pattern.UNICODE_CASE;
                    }
                    if(ignore.indexOf("accent")!=-1) {
                        // Alpha=0391 alpha=03b1 Omega=03a9 omega=03c9
                        content=content.replace("[\u0391-\u03a9", "[A-Z");
                        content=content.replace("[\u03b1-\u03c9", "[a-z");
                        content=content.replace("\u0391-\u03a9]", "A-Z]");
                        content=content.replace("\u03b1-\u03c9]", "a-z]");
                        content=CaseUtil.removeAccents(content);
                        // stored in the compiled regexp for FATokenState.accept() to know:
                        flags|=TokenPattern.MATCH_IGNORE_ACCENT;
                    }
                    regexp=Pattern.compile(content,flags);
                }catch(PatternSyntaxException ex) {
                    throw new ModelException(parser.getInfoString(tokElem)+": error parsing regexp in token: "+ex);
                }
            }else {
                subPat=new TokenPattern("pat_tok", content);
                subPat.modelElement=mElem;
                // ignore lemma, case, or nothing?
                if(ignore!=null) {
                    if(ignore.indexOf("lemma")!=-1)
                        subPat.matchFlags |= TokenPattern.MATCH_IGNORE_LEMMA;
                    if(ignore.indexOf("case")!=-1)
                        subPat.matchFlags |= TokenPattern.MATCH_IGNORE_CASE;
                    if(ignore.indexOf("accent")!=-1)
                        subPat.matchFlags |= TokenPattern.MATCH_IGNORE_ACCENT;
                }
                try {
                    patComp.compile(subPat, null, mElem, m.kb, tokenizer);
                }catch(TokenPatternSyntaxException ex) {
                    throw new ModelException(parser.getInfoString(tokElem)+": error parsing token content: "+ex);
                }
            }
        }
        // no text, only single token type/case/regexp specified  
        if(subPat==null) {
            String[] attVals=new String[] 
              { tokElem.getAttribute("type"), tokElem.getAttribute("case"), tokElem.getAttribute("tag") };
            EnumFeature[] feats=new EnumFeature[]
              { TokenTypeF.getSingleton(), TokenCapF.getSingleton(), TagNameF.getSingleton()};

            String[] vals=null;
            TIntArrayList fids=new TIntArrayList(4);
            TIntArrayList fvals=new TIntArrayList(8);
            TIntArrayList lastOffsets=new TIntArrayList(4);
            boolean orUsed=false;
            for(int k=0;k<attVals.length;k++) {
                Feature f=(Feature) feats[k];
                if(attVals[k]==null || attVals[k].length()==0)
                    continue;
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"tok "+k+"="+attVals[k]);
                vals=attVals[k].toUpperCase().split("\\|");
                for(int j=0;j<vals.length;j++) {
                    int intVal=((EnumFeature) f).fromString(vals[j]);
                    if(intVal==-1) {
                        throw new ModelException(parser.getInfoString(tokElem)+": unknown value '"+vals[j]+"' ("+attVals[k]+") for feature "+f.name);
                    }
                    fvals.add(intVal);
                }
                switch(fvals.size()) {
                case 0: continue;
                case 1: break;
                default: orUsed=true;
                }
                // remember feature=A|B
                fids.add(f.id);
                lastOffsets.add(fvals.size()-1);
            }
            if(fids.size()==0)
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,parser.getInfoString(tokElem)+": no constraints specified in <tok>");
            // make fa with single state
            FAState st=null;
            if(orUsed) {
                // OR on feature values (case=CA _OR_ LC), but AND on individual features (case _AND_ token type)
                boolean featOr=false;
                Object data=(regexp!=null)? regexp: "(OTOK)";
                st=new FATokenOrState(fids.toNativeArray(), fvals.toNativeArray(), lastOffsets.toNativeArray(), featOr, data);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"FATokenOrState");
            }else {
                Object data=(regexp!=null)? regexp: "(TOK)";
                st=new FATokenState(fids.toNativeArray(), fvals.toNativeArray(), data);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"FATokenState");
            }
            st.neg = isPositive(tokElem.getAttribute("neg"));
            FA fa=new FA(st,st);
            subPat=new TokenPattern("pat_tok", "<tok/>");
            subPat.modelElement=mElem;
            subPat.fa=fa;
        }
        return subPat;
    }    
    
    protected TokenPattern readTag(Element tagElem, Model m, ModelElement mElem) throws ModelException {
        String data="tag";
        int tagId=-1;
        int tagType=-1;
        int tagForm=FATagState.START_TAG;
        String type=tagElem.getAttribute("type").trim();
        if(type.length()>0) {
            if(type.startsWith("^"))
                type=type.substring(1);
            if(type.endsWith("$")) {
                tagForm=FATagState.END_TAG;
                type=type.substring(0,type.length()-1).trim();
            }
            else if(type.startsWith("/")) {
                tagForm=FATagState.END_TAG;
                type=type.substring(1).trim();
                if(type.endsWith("/")) {
                    tagForm=FATagState.INLINE_TAG;
                    type=type.substring(0,type.length()-1).trim();
                }
            }
            tagType=TagTypeF.getSingleton().fromString(type);
            if(tagType==-1)
                throw new ModelException("Unknown tag type used in pattern: "+type+
                        " Possible values: "+TagTypeF.getSingleton().enumerateValues(null));
            data=type;
        }
        String name=tagElem.getAttribute("name").trim();
        if(name.length()>0) {
            if(name.startsWith("^"))
                name=name.substring(1);
            if(name.endsWith("$")) {
                tagForm=FATagState.END_TAG;
                name=name.substring(0,name.length()-1).trim();
            }
            else if(name.startsWith("/")) {
                tagForm=FATagState.END_TAG;
                name=name.substring(1).trim();
                if(name.endsWith("/")) {
                    tagForm=FATagState.INLINE_TAG;
                    name=name.substring(0,name.length()-1).trim();
                }
            }
            tagId=TagNameF.getSingleton().fromString(name);
            if(tagId==TagNameF.UNK_TAG)
                throw new ModelException("Unknown tag name used in pattern: "+name+
                        " Possible values: "+TagNameF.getSingleton().enumerateValues(null));
            data=name;
        }
        String form=tagElem.getAttribute("form");
        if(form.length()>0) {
            if(form.equalsIgnoreCase("end")) {
                tagForm=FATagState.END_TAG;
                data="</"+data+">";
            }else if(form.equalsIgnoreCase("inline")) {
                tagForm=FATagState.INLINE_TAG;
                data="<"+data+"/>";
            }else if(form.equalsIgnoreCase("start")) {
                tagForm=FATagState.START_TAG;
                data="<"+data+">";
            }else {
                throw new ModelException("Unknown tag form used in pattern: "+form+
                        " Possible values: start, end, inline");
            }
        }
        FAState st=new FATagState(tagId, tagType, tagForm, data);
        st.neg = isPositive(tagElem.getAttribute("neg"));
        FA fa=new FA(st,st);
        
        int lno=(parser.getInfo(tagElem)!=null)? parser.getInfo(tagElem).line: 0;
        TokenPattern subPat=new TokenPattern("pat_tag_"+mElem.name+"_"+lno, data);
        subPat.modelElement=mElem;
        subPat.fa=fa;
        return subPat;
    }
    
    public TokenPattern readPhr(Element phrElem, Model m, ModelElement mElem) throws ModelException {
        byte combType=FAPhraseState.LAB_CMB_AND;
        byte labType=FAPhraseState.LAB_BODY;
        boolean usesACs=false;
        String labelSpec=phrElem.getAttribute("name").trim();
        if(labelSpec.length()==0) {
            labelSpec=phrElem.getAttribute("ac").trim();
            usesACs=labelSpec.length()>0;
        }
        if(labelSpec.startsWith("^")) {
            labType=FAPhraseState.LAB_START;
            labelSpec=labelSpec.substring(1).trim();
        }else if(labelSpec.endsWith("$")) {
            labType=FAPhraseState.LAB_END;
            labelSpec=labelSpec.substring(0,labelSpec.length()-1).trim();
        }
        String[] labelStrings=labelSpec.split("\\s*\\&\\s*");
        if(labelStrings.length==1) {
            labelStrings=labelSpec.split("\\s*\\|\\s*");
            if(labelStrings.length>1) {
                combType=FAPhraseState.LAB_CMB_OR;
            }
        }
        List<Integer> labs=new ArrayList<Integer>(labelStrings.length);
        List<Integer> tags=new ArrayList<Integer>(labelStrings.length);
        List<String> acNames=new ArrayList<String>(labelStrings.length);
        double[] minConfs=null;
        if(usesACs) {
            for(String acn: labelStrings) {
                acNames.add(acn);
            }
        }else {
            // translate labels into features
            for(String str: labelStrings) {
                if(isUpperCase(str)) {
                    int htmlCode=TagNameF.getSingleton().fromString(str);
                    int htmlType=-1;
                    if(htmlCode!=TagNameF.UNK_TAG) {
                        tags.add(FAPhraseState.TAGNAME_BASE+htmlCode);
                        continue;
                    }else if((htmlType=TagTypeF.getSingleton().fromString(str))!=-1) {
                        tags.add(FAPhraseState.TAGTYPE_BASE+htmlType);
                        continue;
                    }
                }
                AnnotationF f=AnnotationF.getAnnotation(SemAnnot.TYPE_CHUNK, str, true);
                labs.add(f.labelId);
            }
        }
        FAState st=new FAPhraseState(labs, tags, acNames, minConfs, combType, labType);
        if(usesACs) {
            patComp.addToResolveList(st);
        }
        st.neg = isPositive(phrElem.getAttribute("neg"));
        FA fa=new FA(st,st);
        int lno=(parser.getInfo(phrElem)!=null)? parser.getInfo(phrElem).line: 0;
        TokenPattern subPat=new TokenPattern("pat_phr_"+mElem.name+"_"+lno, labelSpec);
        subPat.modelElement=mElem;
        subPat.fa=fa;
        if(usesACs) {
            subPat.contentType |= TokenPattern.PATTERN_WITH_ACS;
            for(String acn: labelStrings) {
                subPat.addUsedElement(acn);
            }
            if(labType==FAPhraseState.LAB_END) {
                m.setGenerateExtraInfo(true);
            }
        }else {
            subPat.contentType |= TokenPattern.PATTERN_WITH_LABELS;
        }
        return subPat;
    }
    
    public TokenPattern readPattern(Element patElem, short patCtx, Model m, ModelElement mElem, 
            String namePath, int order) throws ModelException {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"reading pattern on line "+parser.getInfoString(patElem));
        XMLLocInfo info=parser.getInfo(patElem);
        int patNumId=(info!=null)? info.line: order;

        if(isNegative(patElem.getAttribute("enabled")))
            return null;

        String patType=patElem.getAttribute("type");
        double prec=readDouble(patElem.getAttribute("p"));
        double recall=readDouble(patElem.getAttribute("cover"));
        if(patElem.getTagName().equals("axiom")) {
            patType="script";
            if(recall==-1)
                recall=1;
        }
        if(classifierOnly && !isNegative(patElem.getAttribute("feature"))) {
            prec=-1; recall=-1;
        }
        String srcRef=patElem.getAttribute("src");
        if(srcRef.length()>0) {
            try {
                srcRef=resolveRelativePath(srcRef);
            }catch(IOException ex) {
                throw new ModelException(parser.getInfoString(patElem)+
                        ": invalid path/file specified for pattern "+mElem.getName()+"_"+patNumId);
            }
        }
        String enc=patElem.getAttribute("encoding");
        if(enc.length()==0)
            enc=defPatEnc;
        
        // ignore
        String ign=patElem.getAttribute("ignore").toLowerCase();
        int matchMode=0;
        if(ign.indexOf("case")!=-1)
            matchMode |= TokenPattern.MATCH_IGNORE_CASE;
        if(ign.indexOf("lemma")!=-1)
            matchMode |= TokenPattern.MATCH_IGNORE_LEMMA;
        if(ign.indexOf("accent")!=-1)
            matchMode |= TokenPattern.MATCH_IGNORE_ACCENT;
        // boolean ignoreCase=(ign.indexOf("case")!=-1);
        // boolean ignoreLemma=(ign.indexOf("lemma")!=-1);
        if(mElem==null && (prec!=-1.0 || recall!=-1.0)) {
            throw new ModelException(parser.getInfoString(patElem)+
              ": cannot specify precision/recall for patterns outside of value or context elements of attribute, "+
              "error reading pattern "+mElem.getName()+"_"+patNumId);
        }

        // force one of given case values
        int fcArea=TokenPattern.FC_ALL;
        String forceCase=patElem.getAttribute("case").trim();
        int[] caseVals=null;
        if(forceCase.length()>0) {
            if(forceCase.startsWith("^")) {
                fcArea|=TokenPattern.FC_START;
                forceCase=forceCase.substring(1).trim();
            }
            if(forceCase.endsWith("$")) {
                fcArea|=TokenPattern.FC_END;
                forceCase=forceCase.substring(0,forceCase.length()-1).trim();
            }
            String[] vals=forceCase.trim().toUpperCase().split("\\|");
            caseVals=new int[vals.length];
            EnumFeature capF=TokenCapF.getSingleton();
            for(int j=0;j<vals.length;j++) {
                int intVal=capF.fromString(vals[j]);
                if(intVal==-1) {
                    throw new ModelException(parser.getInfoString(patElem)+": unknown value '"+vals[j]+"' ("+forceCase+") for case");
                }
                caseVals[j]=intVal;
            }
        }
        
        int[] tokLenRange=readCard(patElem.getAttribute("length").trim());
        if(tokLenRange==null) {
            tokLenRange=new int[] {0,Integer.MAX_VALUE};
        }
        
        // conditions under which to apply this axiom/pattern
        byte condType=condType2Code(patElem.getAttribute("cond"));
        String condSrc=null;
        NodeList nodes=patElem.getElementsByTagName("cond");
        if(nodes.getLength()>1)
            throw new ModelException(parser.getInfoString(patElem)+": multiple <cond> elements not supported inside pattern");
        if(nodes.getLength()==1) {
            Element cel=(Element) nodes.item(0);
            condSrc=cel.getTextContent();
        }

        TokenPattern tp=null; // token pattern will be created for type=list|pattern
        int rc=0;
        if(patType.equals("format")) {
            // name of one of the preset formatting patterns
            if(patCtx!=TokenPattern.PAT_VAL && patCtx<TokenPattern.PAT_CLS) {
                throw new ModelException(parser.getInfoString(patElem)+": format patterns only allowed in attribute's value section");
            }
            String data=patElem.getTextContent();
            readFormatPattern(data, prec, recall, patElem, m, mElem);
            
        }else if(patType.equals("script")) {
            if(patCtx!=TokenPattern.PAT_VAL && patCtx<TokenPattern.PAT_CLS) {
                throw new ModelException(parser.getInfoString(patElem)+": script patterns only allowed in attribute's value section or in class");
            }
            String data=patElem.getTextContent();
            readScriptPattern(data, prec, recall, patElem, m, mElem, patNumId, condType, condSrc);
            
        }else if(patType.equals("list")) {
            String listName;
            if(srcRef.length()>0) {
                log.LG(Logger.USR,"Reading list "+srcRef+"...");
                markFile(srcRef);
                listName="LST_"+srcRef;
                tp=new TokenPattern(listName, listName, patCtx, mElem, matchMode, 
                        caseVals, fcArea, condType, tokLenRange[0], tokLenRange[1]);
                rc=patComp.compileListFromFile(tp, m.kb, tokenizer, srcRef, enc);
            }else {
                listName="LST_"+((mElem!=null)? mElem.getName(): "global")+"_"+patNumId;
                tp=new TokenPattern(listName, listName, patCtx, mElem, matchMode, 
                        caseVals, fcArea, condType, tokLenRange[0], tokLenRange[1]);
                String data=patElem.getTextContent();
                rc=patComp.compileListFromString(tp, m.kb, tokenizer, listName, data);
            }
            if(rc!=0)
                throw new ModelException(parser.getInfoString(patElem)+": error compiling list '"+listName+"'");
            // the compiled FATrieState can also be negated:
            tp.fa.startState.neg = isPositive(patElem.getAttribute("neg"));
            PR_Evidence ev=new PR_Evidence(listName, prec, recall, (byte)0, -1);
            tp.evidence=ev;
            
        }else if(patType.equals("pattern") || patType.length()==0) {
            // list of patterns produced by child xml elements
            String patName="PAT_"+((mElem!=null)? mElem.getName(): "global")+"_"+patNumId;
            tp=new TokenPattern(patName, patName, patCtx, mElem, matchMode,
                    caseVals, fcArea, condType, tokLenRange[0], tokLenRange[1]);
            ArrayList<TokenPattern> subPatterns=new ArrayList<TokenPattern>(16);
//            if(patElem.getAttribute("id").equals("tester")) {
//                int stopDebuggerHere=0;
//            }
            tp.source=readTokenPattern(subPatterns, prec, recall, patElem, m, mElem, namePath, patCtx);
            try {
                patComp.compile(tp, subPatterns, mElem, m.kb, tokenizer);
            }catch(TokenPatternSyntaxException ex) {
                throw new ModelException(parser.getInfoString(patElem)+": error compiling pattern '"+patName+"': "+ex);
            }
            PR_Evidence ev=new PR_Evidence(patName, prec, recall, (byte)0, -1);
            tp.evidence=ev;
            
        }else {
            throw new ModelException(parser.getInfoString(patElem)+": Unknown pattern type '"+patType+"'");
        }
        
        if(tp!=null) {
            registerPattern(m, tp, patElem, mElem);
            tp.useAsFeature=!isNegative(patElem.getAttribute("feature"));
            tp.logLevel=isPositive(patElem.getAttribute("log"))? (short)1: (short)0;
            if(isPositive(patElem.getAttribute("greedy")))
                tp.flags |= TokenPattern.FLAG_GREEDY;
            if(isPositive(patElem.getAttribute("linear")))
                tp.flags |= TokenPattern.FLAG_LINEAR;
        }
        return tp;
    }

    protected void registerPattern(Model m, TokenPattern tp, Element patElem, ModelElement mElem) throws ModelException {
        String id=patElem.getAttribute("id");
        if(id.length()>0) {
            if(id.indexOf('.')!=-1) {
                String msg="Model element "+((mElem!=null)?mElem.getFullName():"root")+": invalid id in pattern (must not contain '.')";
                log.LG(Logger.ERR,msg);
                throw(new ModelException(msg));
            }
            String fullId=(mElem!=null)? 
                absPatternName(id, mElem.getFullName()): 
                (Model.DATATYPE_PREFIX+"."+id.toLowerCase()); 
            m.addPattern(fullId, tp);
        }
    }
    
    protected void readFormatPattern(String content, double prec, double recall, Element patElem, Model m, ModelElement mElem) throws ModelException {
        content=content.trim().toUpperCase();
        DefaultPattern dp=DefaultPattern.create(content, prec, recall);
        if(dp==null) {
            throw new ModelException(parser.getInfoString(patElem)+": unknown formatting pattern type '"+content+"'");
        }
        mElem.addPattern(dp);
    }

    protected void readScriptPattern(String content, double prec, double recall, 
            Element patElem, Model m, ModelElement mElem, int order, byte condType, String cond) 
           throws ModelException {
        XMLLocInfo info=parser.getInfo(patElem);
        int patNumId=(info!=null)? info.line: order;
        String patName="SCR_"+mElem.getName()+"_"+patNumId;
        Axiom ax=new Axiom(patName, content, 0, mElem.getName(), Axiom.TYPE_VALUE_PATTERN, condType, cond);
        PR_Evidence ev=new PR_Evidence(patName, prec, recall, (byte)0, -1);
        ScriptPattern sp=new ScriptPattern(ax, ev);
        mElem.addPattern(sp);
    }
    
    protected String readTokenPattern(List<TokenPattern> subPatterns, double prec, double recall, Element patElem, Model m, 
            ModelElement mElem, String namePath, short patCtx) throws ModelException {
        String patternData=null;
        // read from src if given
        String patSrc=patElem.getAttribute("src");
        String enc=patElem.getAttribute("encoding");
        if(patSrc.length()!=0) {
            String absFile=null;
            try {
                absFile=resolveRelativePath(patSrc);
            }catch(IOException ex) {
                throw new ModelException(parser.getInfoString(patElem)+
                        ": invalid path/file specified for sub-pattern of "+mElem.getName());
            }
            log.LG(Logger.USR,"Reading "+absFile+" enc="+enc);
            patternData=readFile(absFile, enc);
        }
        // read element content if src not given
        else {
            StringBuffer buff=new StringBuffer(256);
            NodeList childList=patElem.getChildNodes();
            for(int i=0;i<childList.getLength();i++) {
                switch(childList.item(i).getNodeType()) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    buff.append(childList.item(i).getNodeValue());
                    break;
                case Node.ENTITY_REFERENCE_NODE:
                    EntityReference er=(EntityReference) childList.item(i);
                    String enVal=er.getNodeValue();
                    String enName=er.getNodeName();
                    if(enVal==null) {
                        enVal=entityMap.get(enName);
                    }
                    if(enVal==null) {
                        String msg=parser.getInfoString(patElem)+": reference to an unknown entity "+enName;
                        log.LG(Logger.ERR,msg);
                        throw new ModelException(msg);
                    }
                    buff.append("karel");
                    break;
                case Node.ELEMENT_NODE: // att|tok|pattern|unit|tag
                    Element el=(Element)childList.item(i);
                    String elName=el.getTagName();
                    // TOK
                    if(elName.equals("tok")) {
                        TokenPattern subPat=readTok(el, m, mElem);
                        // add FA for child element and append placeholder into pattern source
                        subPatterns.add(subPat);
                        buff.append(" \1"); // delimit prev token with ' '
                    }
                    // SUB-PATTERN
                    else if(elName.equals("pattern")) { // child pattern
                        String id=el.getAttribute("id");
                        if(id.length()>0)
                            log.LG(Logger.WRN,parser.getInfoString(patElem)+
                             ": <pattern> in contexts other than <attribute> cannot have id='"+id+"' specified "+
                             "(use the ref attribute to point to ids of existing patterns).");
                        String ref=el.getAttribute("ref");
                        TokenPattern sub=null;
                        if(ref.length()>0) {
                            String fullName=absPatternName(ref,namePath);
                            sub=m.genPatterns.get(fullName);
                            if(sub==null) {
                                StringBuffer sb=new StringBuffer(256);
                                Iterator<String> it=m.genPatterns.keySet().iterator();
                                while(it.hasNext()) {
                                    sb.append(it.next());
                                    if(it.hasNext())
                                        sb.append(",");
                                }
                                throw(new ModelException(parser.getInfoString(patElem)+
                                        ": pattern ref="+ref+"("+fullName+") points to unknown pattern definition; known patterns: "+
                                        sb.toString()));
                            }
                        }
                        if(sub==null) {
                            sub=readPattern(el, patCtx, m, mElem, namePath, i);
                            if(sub==null)
                                throw new ModelException(parser.getInfoString(patElem)+
                                ": only patterns of type token or list allowed as sub-patterns");
                        }
                        subPatterns.add(sub);
                        buff.append(" \1"); // placeholder
                        // ATT
                    }else if(elName.equals("att")) {
                        String ref=el.getAttribute("ref");
                        // TBD
                        // UNIT
                    }else if(elName.equals("unit")) {
                        // TBD
                        // TAG
                    }else if(elName.equals("tag")) {
                        TokenPattern subPat=readTag(el, m, mElem);
                        // add FA for child element and append placeholder into pattern source
                        subPatterns.add(subPat);
                        buff.append(" \1"); // delimit prev token with ' '
                    }else if(elName.equals("lab")) {
                        TokenPattern subPat=readPhr(el, m, mElem);
                        // add FA for child element and append placeholder into pattern source
                        subPatterns.add(subPat);
                        buff.append(" \1"); // delimit prev token with ' '

                    }else {
                        String msg=parser.getInfoString(patElem)+": unexpected element "+elName+" in pattern";
                        log.LG(Logger.WRN,msg);
                        throw new ModelException(msg);
                    }
                    break;
                case Node.COMMENT_NODE: 
                    continue;
                default:
                    String msg=parser.getInfoString(patElem)+": unexpected data in pattern: "+childList.item(i);
                    log.LG(Logger.WRN,msg);
                    throw new ModelException(msg);
                    // continue;
                }
            } // for pattern content
            patternData=buff.toString();
        }
        if(patternData==null || patternData.length()==0)
            throw(new ModelException(parser.getInfoString(patElem)+": empty pattern"));
        return patternData;
    }

    private void registerScript(Model m, Element elem, String flag) throws ModelException {
        String srcRef=elem.getAttribute("src");
        String enc=elem.getAttribute("encoding");
        String content=null;
        if(srcRef.length()>0) {
            try {
                srcRef=resolveRelativePath(srcRef);
            }catch(IOException ex) {
                throw new ModelException(parser.getInfoString(elem)+": invalid script path/file specified.");
            }
            log.LG(Logger.USR,"Reading "+srcRef);
            content=readFile(srcRef, enc);
        }else {
            StringBuffer buff=new StringBuffer(128);
            NodeList childList=elem.getChildNodes();
            for(int i=0;i<childList.getLength();i++) {
                switch(childList.item(i).getNodeType()) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    buff.append(childList.item(i).getNodeValue());
                    break;
                }
            }
            content=buff.toString();
            srcRef="inline_"+parser.getInfoString(elem);
        }
        String key=srcRef;
        if(flag!=null) {
            key=srcRef+"_"+flag;
        }
        int i=0;
        while(m.userScripts.containsKey(key)) {
            key=srcRef+"_"+i+flag;
        }
        m.userScripts.put(key, content);
    }
    
    public String absPatternName(String id, String namePath) {
        if(id.indexOf('.')!=-1)
            return id;
        return namePath+'.'+id;
    }
    
    void markFile(String pathName) {
        File f=new File(pathName);
        dates.append(pathName+"\t"+f.lastModified()+"\n");
    }

    protected int readIntMagic(String s) throws ModelException {
        int rc=0;
        s=s.trim();
        if(s.length()==0)
            rc=0;
        else if(isPositive(s))
            rc=1;
        else if(isNegative(s))
            rc=0;
        else {
            try {
                rc=Integer.parseInt(s);
            }catch(NumberFormatException e) {
                String msg="Error reading number '"+s+"'";
                log.LGERR(msg);
                throw new ModelException(msg);
            }
        }
        return rc;
    }
    
    protected static final Pattern cardPat2=Pattern.compile("^([0-9n]+)\\-([0-9n]+)$", Pattern.CASE_INSENSITIVE);
    protected static final Pattern cardPat1=Pattern.compile("^([0-9n]+)$", Pattern.CASE_INSENSITIVE);
    protected int[] readCard(String s) throws ModelException {
        if(s==null || s.length()==0) {
            return null;
        }
        String min=null;
        String max=null;
        Matcher mat=cardPat2.matcher(s);
        if(mat.find()) {
            min=mat.group(1);
            max=mat.group(2);
        }else {
            mat=cardPat1.matcher(s);
            if(mat.find()) {
                min=mat.group(1);
                max=mat.group(1);
            }
        }
        if(min==null || max==null) {
            String msg="Error reading cardinality '"+s+"'";
            log.LG(Logger.ERR,msg);
            throw(new ModelException(msg));
        }
        int[] rc=new int[2];
        rc[0]=readInt(min);
        rc[1]=readInt(max);
        return rc;
    }

    public static void main(String args[]) {
        Logger.init("model.log", -1, -1, null);
        Logger log=Logger.getLogger("ModelReader");
        if(args.length==0 || args.length>1) {
            System.err.println("Usage: java ex.model.ModelReader ontology.xml");
            System.err.println("Reads in an extraction ontology.");
            return;
        }
        Options o=Options.getOptionsInstance();
        String cfg="config.cfg";
        try {
            o.load(new FileInputStream(cfg));
        }catch(IOException ex) {
            System.err.println("Cannot find "+cfg+": "+ex.getMessage());
        }

        // instantiate FM before constructing ModelReader
        FM fm=FM.getFMInstance();
        ModelReader reader=new ModelReader();
        KB modelKb=new KB("model",1000,5000); // initally empty KB
        fm.registerKB(modelKb);
        Model m=null;
        try {
            m=reader.read(args[0], modelKb);
        }catch(org.xml.sax.SAXException sex) {
            System.err.println("Error XML parsing model "+args[0]+": "+sex.getMessage());
        }catch(ModelException mex) {
            System.err.println("Error reading model "+args[0]+": "+mex.getMessage());
            mex.printStackTrace();
        }catch(java.io.IOException iex) {
            System.err.println("Cannot open model "+args[0]+": "+iex.getMessage());
        }
        log.LG(Logger.INF,"Model "+m.name+" from "+m.fileName+" read ok");
        
        // disconnect from lemmatizer etc.
        fm.deinit();
    }
    
    protected boolean isPositive(String str) {
        String s=str.trim().toLowerCase();
        if(s.length()>0 && (s.equals("1") || s.equals("true") || s.equals("yes")))
            return true;
        return false;
    }

    protected boolean isNegative(String str) {
        String s=str.trim().toLowerCase();
        if(s.length()>0 && (s.equals("0") || s.equals("false") || s.equals("no")))
            return true;
        return false;
    }
    
    private boolean isUpperCase(String str) {
        for(int i=0;i<str.length();i++) {
            char c=str.charAt(i);
            if(Character.isLowerCase(c))
                return false;
        }
        return true;
    }
}

// $Id: Model.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import java.io.IOException;
import java.util.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ErrorReporter;
import ex.util.Const;
import uep.util.Logger;
import uep.util.Options;
import uep.util.Util;
import ex.ac.AC;
import ex.ac.TokenPattern;
import ex.reader.Document;
import ex.train.KB;
import ex.ac.TokenPatternSyntaxException;
import ex.wrap.FmtPattern;

public class Model {
    public static final int OK=0;
    public static final int MAXCARD=-1;
    public static final int MINCARD=-2;
    public static final int AXIOMBROKEN=-3;

    static final String DATATYPE_PREFIX="_datatypes";
    static final String UNIT_PREFIX="_units";

    public String name;
    public KB kb;

    public HashMap<String,ClassDef> classes;
    public ClassDef[] classArray;
    private List<ClassDef> clsList;

    // set of standalone attributes
    public ClassDef standaloneAtts;
    
    public HashMap<String,TokenPattern> genPatterns;
    public TokenPattern[] genPatternArray;

    public HashMap<String,Vector<Unit>> quantities; // 'quantity.id' => Unit[]
    public HashMap<String,Unit> units; // 'unit.id' => Unit
    public Unit[] unitArray;

    public HashMap<String,Axiom> axioms;
//    public Axiom[] axiomArray;
    
    public Context context;
    public Scriptable scope;
    public Script clearScript;
    public AxiomErrorReporter aer;

    protected Map<String,String> userScripts;
    
    // all model elements: classDefs and attributeDefs keyed by their full names
    protected Map<String,ModelElement> elements;
    
    // single storage for all classifiers
    public ArrayList<ClassifierDef> classifiers;
    
    // scripts that define post-processing rules
    // TODO: use Axioms instead of directly storing Scripts)
    protected List<Script> postRules;
    
    public String fileName;
    boolean generateExtraInfo;
    private Logger log;

    public Model(String n, KB modelKb) {
        name=n;
        kb=modelKb;
        classes=new HashMap<String,ClassDef>(8);
        clsList=new LinkedList<ClassDef>();
        genPatterns=new HashMap<String,TokenPattern>(32);
        quantities=new HashMap<String,Vector<Unit>>(8);
        units=new HashMap<String,Unit>(16);
        axioms=new HashMap<String,Axiom>(32);
        classArray=null;
        genPatternArray=null;
        unitArray=null;
//        axiomArray=null;
        lastColorUsed=-1;
        colorsUsed=new HashMap<String,String>(32);
        userScripts=new HashMap<String,String>(8);
        postRules=new ArrayList<Script>(4);
        log=Logger.getLogger("Model");
        // js
        context=Context.enter();
        scope=context.initStandardObjects();
        clearScript=null;
        aer=new AxiomErrorReporter(log);
        context.setErrorReporter(aer);
        context.exit();
        classifiers=new ArrayList<ClassifierDef>(8);
        elements=new HashMap<String,ModelElement>(8);
        standaloneAtts=new ClassDef("_standalone", this);
        generateExtraInfo=false;
    }

    public void close() {
        //context.exit();
    }

    public int addClass(ClassDef cd) {
        if(classes.containsKey(cd.name)) {
            log.LG(Logger.ERR,"Class named '"+cd.name+"' already exists!");
            return Const.EX_ERR;
        }
        cd.id=clsList.size();
        classes.put(cd.name, cd);
        clsList.add(cd);
        return Const.EX_OK;
    }
    
    public int addStandaloneAttribute(AttributeDef ad) throws ModelException {
        if(elements.containsKey(ad.name)) {
            log.LG(Logger.ERR,"Element named '"+ad.name+"' already exists!");
            return Const.EX_ERR;
        }
        standaloneAtts.addAttribute(ad);
        standaloneAtts.prepare();
        elements.put(ad.name, ad);
        return Const.EX_OK;
    }

    public int addPattern(String name, TokenPattern pat) {
        if(genPatterns.containsKey(name)) {
            log.LG(Logger.ERR,"Pattern named '"+name+"' already exists!");
            return Const.EX_ERR;
        }
        genPatterns.put(name, pat);
        return Const.EX_OK;
    }

    /** Registers an axiom ax under its ax.name, currently only makes sure 
     * all axioms have different names. */
    public int addAxiom(Axiom ax) {
        if(axioms.containsKey(ax.name)) {
            log.LG(Logger.ERR,"Axiom named '"+ax.name+"' already exists!");
            return Const.EX_ERR;
        }
        axioms.put(ax.name, ax);
        return Const.EX_OK;
    }

    public void prepare() throws ModelException {
        // common script
        String commonScriptFile=Options.getOptionsInstance().getProperty("common_script", "/res/common.js");
        if(commonScriptFile.trim().length()!=0) {
            try {
                String script=Util.loadFileFromJarOrDisc(commonScriptFile, "utf-8", this.getClass());
                eval(script);
            }catch(IOException ex) {
                log.LG(Logger.ERR,"Error loading common script "+commonScriptFile);
            }
        }
        // populate class array
        int cnt=classes.size();
        classArray=new ClassDef[cnt];
        // clsList used to preserve order: classes.values().iterator();
        Iterator<ClassDef> cit=clsList.iterator();
        int i=0;
        while(cit.hasNext()) {
            classArray[i]=cit.next();
            classArray[i].prepare();
            i++;
        }
        // populate unit array
        cnt=units.size();
        unitArray=new Unit[cnt];
        Iterator<Unit> uit=units.values().iterator();
        i=0;
        while(uit.hasNext()) {
            unitArray[i++]=uit.next();
        }
        // populate genPatternArray incl. datatype patterns
        cnt=genPatterns.size();
        genPatternArray=new TokenPattern[cnt];
        Iterator<TokenPattern> pit=genPatterns.values().iterator();
        i=0;
        while(pit.hasNext()) {
            genPatternArray[i++]=(TokenPattern) pit.next();
        }
        // axioms
//        cnt=axioms.size();
//        axiomArray=new Axiom[cnt];
//        it=axioms.values().iterator();
//        i=0;
//        while(it.hasNext()) {
//            axiomArray[i++]=(Axiom) it.next();
//        }
        // build clear script (TODO: multiple classes to be solved)
        StringBuffer cs=new StringBuffer(512); 
        for(i=0;i<classArray.length;i++) {
            ClassDef cd=classArray[i];
            for(int j=0;j<cd.attArray.length;j++) {
                cs.append(cd.attArray[j].varName);
                cs.append("=undefined;\n");
            }
        }
        context.enter();
        clearScript=context.compileString(cs.toString(),"clearScript",0,null);
        Set<Map.Entry<String, String>> namedScripts=userScripts.entrySet();
        Iterator<Map.Entry<String, String>> sit=namedScripts.iterator();
        while(sit.hasNext()) {
            Map.Entry<String, String> scr=sit.next();
            Script precomp=context.compileString(scr.getValue(),scr.getKey(),0,null);
            if(precomp!=null) {
                if(scr.getKey().endsWith(ModelReader.POST_PROC_FLAG)) {
                    // store for later execution
                    postRules.add(precomp);
                }else {
                    // just eval once all other user scripts
                    Object rc=precomp.exec(context, scope);
                }
            }
        }
        context.exit();
        // populate elements map
        for(i=0;i<classArray.length;i++) {
            ClassDef cd=classArray[i];
            elements.put(cd.getFullName(), cd);
            for(int j=0;j<cd.attArray.length;j++) {
                AttributeDef ad=cd.attArray[j];
                elements.put(ad.getFullName(), ad);
            }
        }
        // prepare classifiers
        for(ClassifierDef csd: classifiers) {
            csd.prepare(this);
        }
    }

    public void importFrom(Model m) {
        // import classes, units, genPatterns incl datatypes, axioms
        classes.putAll(m.classes);
        units.putAll(m.units);
        genPatterns.putAll(m.genPatterns);
        axioms.putAll(m.axioms);
    }

    public TokenPattern getDataTypePattern(String dataType) throws TokenPatternSyntaxException {
        TokenPattern pat=(TokenPattern) genPatterns.get(DATATYPE_PREFIX+"."+dataType.toLowerCase());
        if(pat==null) 
            throw new TokenPatternSyntaxException("Datatype pattern for '"+dataType+"' not found!");
        return pat;
    }

    /* ecma context manipulation */
    public void clearScope() {
        context.enter();
        clearScript.exec(context, scope);
        context.exit();
    }

    public void setVar(String name, Object value) {
        scope.put(name,scope,value);
    }

    public Script compile(String expr, String name) {
        context.enter();
        Script script=context.compileString(expr,name,0,null);
        context.exit();
        return script;
    }
    
    public Object eval(Script script) {
        context.enter();
        Object rc=script.exec(context, scope);
        context.exit();
        return rc;
    }
    
    public Object eval(String expr) {
        context.enter();
        Script script=context.compileString(expr,"anonymous",0,null);
        Object rc=script.exec(context, scope);
        context.exit();
        return rc;
    }
    
    public byte evalReferenceScript(AC primary, AC reference) {
        byte rc=AC.REF_NO;
        context.enter();
        clearScript.exec(context, scope);
        // $attName="Chuck"; [ if($parent==undefined) $parent="Chuck"; ... ]
        reference.getScript().exec(context, scope);
        // $other=$attName
        reference.getAttribute().referScript.condScript.exec(context, scope);
        // $attName="Charles"; [ if($parent==undefined) $parent="Charles"; ... ]
        primary.getScript().exec(context, scope);
        // isReference($attName,$other)
        Object retVal=primary.getAttribute().referScript.contentScript.exec(context, scope);
        if(!(retVal instanceof java.lang.Boolean))
            throw new AxiomException("Refers script "+primary.getAttribute()+"("+primary.getAttribute().referScript.lno+") returned "+retVal+
            " for ACs="+primary+", "+reference+" (boolean type required)");
        if(Logger.IFLOG(Logger.TRC)) Logger.LOG(Logger.TRC,"AC "+primary+","+reference+": refers script "+primary.getAttribute().referScript+" evaluated to "+retVal);
        if(((Boolean)retVal).booleanValue())
            rc=AC.REF_BIDIRECTIONAL;
        context.exit();
        return rc;
    }

    public void addLocalPatterns(List<FmtPattern> locPats) {
        for(int i=0;i<classArray.length;i++) {
            classArray[i].addLocalPatterns(locPats);            
        }
    }
    
    public void clearLocalPatterns() {
        for(int i=0;i<classArray.length;i++) {
            classArray[i].clearLocalPatterns();
        }
    }
    
    /* labeling colors */
    protected int lastColorUsed;
    protected HashMap<String,String> colorsUsed;
    protected String getNextColor() {
        lastColorUsed++;
        while(lastColorUsed<colors.length && colorsUsed.containsKey(colors[lastColorUsed]))
            lastColorUsed++;
        return colors[lastColorUsed];
    }
    protected static final String[] colors=new String[] {
        "aquamarine",
        "blueviolet",
        "burlywood",
        "cadetblue",
        "chartreuse",
        "chocolate",
        "coral",
        "cornflowerblue",
        "crimson",
        "cyan",
        "darkcyan",
        "darkgoldenrod",
        "darkgray",
        "darkkhaki",
        "darkmagenta",
        "darkolivegreen",
        "darkorange",
        "darkorchid",
        "darksalmon",
        "darkseagreen",
        "darkslateblue",
        "darkviolet",
        "deeppink",
        "dodgerblue",
        "forestgreen",
        "fuchsia",
        "gold",
        "goldenrod",
        "gray",
        "green",
        "greenyellow",
        "hotpink",
        "indianred",
        "khaki",
        "lawngreen",
        "lightblue",
        "lightcoral",
        "lightgreen",
        "lightpink",
        "lightsalmon",
        "lightseagreen",
        "lightskyblue",
        "lightslategray",
        "lightsteelblue",
        "lime",
        "limegreen",
        "magenta",
        "mediumauqamarine",
        "mediumorchid",
        "mediumpurple",
        "mediumseagreen",
        "mediumslateblue",
        "mediumspringgreen",
        "mediumturquoise",
        "olive",
        "olivedrab",
        "orange",
        "orangered",
        "orchid",
        "palegoldenrod",
        "palegreen",
        "paleturquoise",
        "palevioletred",
        "peachpuff",
        "peru",
        "pink",
        "plum",
        "powderblue",
        "purple",
        "rosybrown",
        "royalblue",
        "saddlebrown",
        "salmon",
        "sandybrown",
        "seagreen",
        "sienna",
        "silver",
        "skyblue",
        "slateblue",
        "slategray",
        "springgreen",
        "steelblue",
        "tan",
        "teal",
        "thistle",
        "tomato",
        "turquoise",
        "violet",
        "wheat",
        "yellow",
        "yellowgreen"
    };

    public AttributeDef getAttribute(int idx) {
        AttributeDef ad=null;
        if(idx<0)
            return null;
        int base=0;
        if(classArray!=null) {
            for(int c=0;c<classArray.length;c++) {
                ClassDef cd=classArray[c];
                if(idx-base<cd.attArray.length) {
                    ad=cd.attArray[idx-base];
                    break;
                }
                base+=cd.attArray.length;
            }
        }
        if(ad==null && standaloneAtts!=null && standaloneAtts.attArray!=null && (idx-base)<standaloneAtts.attArray.length) {
            ad=standaloneAtts.attArray[idx-base];
        }
        return ad;
    }
    
    public int getAttributeCount() {
        int cnt=(standaloneAtts!=null && standaloneAtts.attArray!=null)? standaloneAtts.attArray.length: 0;
        if(classArray!=null) {
            for(int c=0;c<classArray.length;c++) {
                cnt+=classArray[c].attArray.length;
            }
        }
        return cnt;
    }
    
    public ModelElement getElementByName(String fullName) {
        return elements.get(fullName);
    }

    public void addClassifier(ClassifierDef cs) {
        classifiers.add(cs);
    }
    
    public boolean hasClassifiers() {
        return classifiers.size()>0;
    }

    public void setGenerateExtraInfo(boolean on) {
        generateExtraInfo=on;
    }
    
    public boolean getGenerateExtraInfo() {
        return generateExtraInfo;
    }

    /** Sets the document javascript variable so that scripted evidence can 
     * access document class and other properties. */
    public void setCurrentDocument(Document exDoc, medieq.iet.model.DataModel ietModel) {
        // document
        String key=String.valueOf(exDoc.hashCode());
        objsForJS.put(key, exDoc);
        eval("document=Packages.ex.model.Model.getDocument(\""+key+"\");");
        objsForJS.remove(key);
        // model
        key=String.valueOf(ietModel.hashCode());
        objsForJS.put(key, ietModel);
        eval("model=Packages.ex.model.Model.getModel(\""+key+"\");");
        objsForJS.remove(key);
    }
    private static Map<String,Object> objsForJS=new HashMap<String,Object>(4);
    public static Document getDocument(String key) {
        return (Document) objsForJS.get(key);
    }
    public static medieq.iet.model.DataModel getModel(String key) {
        return (medieq.iet.model.DataModel) objsForJS.get(key);
    }
    
    /** Executes all post-processing rules (scripts). */
    public void postprocess() {
        for(Script scr: postRules) {
            eval(scr);
        }
    }
}

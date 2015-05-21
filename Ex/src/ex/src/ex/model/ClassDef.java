// $Id: ClassDef.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import java.util.*;
import ex.util.Const;
import uep.util.Logger;
import ex.util.pd.IntDistribution;
import ex.util.pr.PR_Class;
import ex.ac.AC;
import ex.ac.TokenPattern;
import ex.parser.ICBase;
import ex.wrap.FmtPattern;
import ex.util.pr.*;
import org.mozilla.javascript.Script;

public class ClassDef extends ModelElement {
    public HashMap<String,AttributeDef> attributes;
    public AttributeDef[] attArray;
    private List<AttributeDef> attList;
    public Model model;
    protected ArrayList<Axiom> axiomList;
    public Axiom[] axioms;
    protected Logger log;
    public int id;
    public List<TokenPattern> ctxPatterns; // token patterns, typically with $inneratts, $neighbors or $ - placeholder for the whole IC value
    public List<ScriptPattern> scriptPatterns; // axiom-like; pattern matches if script returns true
    public List<DefaultPattern> defPatternList; // default patterns (contained in one tag etc.)
    public List<FmtPattern> locPatterns; // fmt patterns induce from the current set of docs
    // public static int classCount=0;
    public static double PRIOR_DEFAULT=0.01;
    public static double PRUNE_PROB_DEFAULT = 0.01; // all ICs below this cond prob are discarded (already during generation)

    /** binary class representing this class in PR model */
    public PR_Class prClass;
    
    /** ICs below this combined prob are discarded (already during generation) */ 
    public double pruneProb;
    
    /** holds minimal and maximal count of instances of this type for one document.  */
    public IntDistribution countDist;
    
    public ClassDef(String name, Model model) {
        this.name=name;
        this.model=model;
        // id=classCount++;
        this.id=-1; // determined when added to model in Model.addClass(cd)
        attributes=new HashMap<String,AttributeDef>(10);
        attArray=null;
        attList=new LinkedList<AttributeDef>();
        axiomList=new ArrayList<Axiom>(10);
        axioms=null;
        ctxPatterns=new ArrayList<TokenPattern>(8);
        scriptPatterns=new ArrayList<ScriptPattern>(8);
        defPatternList=new ArrayList<DefaultPattern>(8);
        locPatterns=new ArrayList<FmtPattern>(8);
        prClass=new PR_Class(this,PRIOR_DEFAULT,null);
        pruneProb=-1; // becomes PRUNE_PROB_DEFAULT in prepare() by default
        countDist=null; // instance count per document is not limited by default
        log=Logger.getLogger("Model");
    }
    
    public Model getModel() {
        return model;
    }

    public String getFullName() {
        return name;
    }
    
    public int addAttribute(AttributeDef ad) {
        if(attributes.containsKey(ad.name)) {
            log.LG(Logger.ERR,"Attribute named '"+ad.name+"' already exists!");
            return Const.EX_ERR;
        }
        attributes.put(ad.name, ad);
        attList.add(ad);
        return Const.EX_OK;
    }

    public void addLocalPatterns(List<FmtPattern> locPats) {
        //locPatterns.addAll(locPats);
        for(int i=0;i<attArray.length;i++) {
            attArray[i].addLocalPatterns(locPats);
        }
    }
    
    public void clearLocalPatterns() {
        //locPatterns.clear();
        for(int i=0;i<attArray.length;i++) {
            attArray[i].clearLocalEvidence();
        }
    }
    
    public int getAttCount() {
        return attributes.size();
    }

    public void addAxiom(Axiom ax) {
        axiomList.add(ax);
    }

    public void prepare() throws ModelException {
        // probs
        if(pruneProb==-1)
            pruneProb = PRUNE_PROB_DEFAULT; // or get from parent when we introduce class inheritance 
        // prepare axioms
        int cnt=axiomList.size();
        axioms=new Axiom[cnt];
        int i;
        for(i=0;i<cnt;i++) {
            axioms[i]=(Axiom) axiomList.get(i);
            axioms[i].prepare(model, this);
        }
        // populate attribute array, prepare attributes
        cnt=attributes.size();
        attArray=new AttributeDef[cnt];
        // attList only used to preserve doc order: attributes.values().iterator();
        Iterator<AttributeDef> it=attList.iterator();
        i=0;
        while(it.hasNext()) {
            attArray[i]=it.next();
            attArray[i].id=i; // assign attribute id==idx
            attArray[i].prepare();
            i++;
        }
        // prepare evidence from default, context+value and script patterns
        int sz=defPatternList.size()+ctxPatterns.size()+scriptPatterns.size();
        prClass.evs=new PR_Evidence[sz];
        i=0;
        Iterator<DefaultPattern> it1=defPatternList.iterator();
        while(it1.hasNext()) {
            DefaultPattern pat=it1.next();
            prClass.evs[i]=pat.evidence;
            pat.evidence.idx=i;
            i++;
        }
        Iterator<TokenPattern> it2=ctxPatterns.iterator();
        while(it2.hasNext()) {
            TokenPattern pat=it2.next();
            prClass.evs[i]=pat.evidence;
            pat.evidence.idx=i;
            i++;
        }
        Iterator<ScriptPattern> it3=scriptPatterns.iterator();
        while(it3.hasNext()) {
            ScriptPattern pat=it3.next();
            pat.axiom.prepare(model, this);
            prClass.evs[i]=pat.evidence;
            pat.evidence.idx=i;
            i++;
        }
    }

    /** Check whether {IC+AC} can be valid supposing IC can be valid:
	this only checks class axioms that apply to the AC being added.
	"IC can be valid" means that the IC either is valid or can become 
	valid by adding more ACs.
     */
    public boolean canBecomeValid(ICBase ic, AC ac) throws AxiomException {
        AttributeDef ad=ac.getAttribute();
        if(ad.axioms==null)
            return true;
        model.clearScope();
        model.eval(ic.contentScript);
        model.setVar(ac.getAttribute().varName, ac.value);
        boolean rc=true;
        for(int i=0;i<ad.axioms.length;i++) {
            rc=isValid(ad.axioms[i], true);
            if(!rc) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"canBecomeValid(IC) "+ic+" + "+ac+" axiom failed: "+ad.axioms[i]);
                break;
            }
        }
        return rc;
    }

    /** Checks if this IC satisfies relevant axioms defined on the class.
	If partial==true, only those axioms that apply to already populated attributes are evaluated,
	otherwise all axioms are examined */
    public boolean isValid(ICBase ic, boolean partial) throws AxiomException {
        if(ic.clsDef.axioms==null)
            return true;
        model.clearScope();
        model.eval(ic.contentScript);
        boolean rc=true;
        // evaluate all axioms referring to ic's class
        for(int i=0;i<ic.clsDef.axioms.length;i++) {
            rc=isValid(ic.clsDef.axioms[i], partial);
            if(!rc) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"isValid(IC) "+ic.toString()+" axiom failed: "+ic.clsDef.axioms[i]);
                break;
            }
        }
        return rc;
    }

    /** Converts a script result to boolean. */
    private boolean toBoolean(Object val) throws AxiomException {
        boolean rc=false;
        if(val==null)
            ;
        else if(val instanceof java.lang.Boolean)
            rc=((java.lang.Boolean) val).booleanValue();
        else if(val instanceof java.lang.Number)
            rc=(((java.lang.Number) val).intValue()!=0);
        else
            throw new AxiomException("Cannot convert to boolean: "+val.toString());
        return rc;
    }
    
    /** Evaluates a class axiom. Expects the scope is cleared and only the examined IC's variables 
     * are present in it. */
    public boolean isValid(Axiom ax, boolean partial) throws AxiomException {
        Object retVal;
        // skip axiom if cond is not met, e.g. when not all of the axiom's vars are defined 
        // and its type is AXIOM_COND_ALL 
        if(ax.condScript!=null) {
            if(! evalCond(ax.condScript))
                return true;
        }
        retVal=model.eval(ax.contentScript);
        boolean rc=false;
        if(retVal==null)
            log.LG(Logger.ERR,"Axiom "+ax.toString()+" evaluated as null");
        try {
            rc=toBoolean(retVal);
        }catch(AxiomException e) {
            throw new AxiomException("Axiom "+ax.name+"("+ax.lno+"): "+e.getMessage());
        }
        return rc;
    }
    
    /** Evaluates a boolean precompiled script */
    public boolean evalCond(Script condScript) throws AxiomException {
        Object retVal=model.eval(condScript);
        boolean rc=false;
        try {
            rc=toBoolean(retVal);
        }catch(AxiomException e) {
            throw new AxiomException("Error evaluating cond expr: "+e.getMessage());
        }
        return rc;
    }

//    public static void resetId() {
//        classCount=0;
//    }

    public String getName() {
        return name;
    }

    public PR_Class getPR_Class() {
        return prClass;
    }

    public boolean addPattern(ScriptPattern sp) {
        scriptPatterns.add(sp);
        return true;
    }

    public boolean addPattern(DefaultPattern dp) {
        defPatternList.add(dp);
        return false;
    }

    public String toString() {
        return name;
    }
    
//    public boolean equals(Object o) {
//        if(o instanceof medieq.iet.model.ClassDef) {
//            medieq.iet.model.ClassDef ietCls=(medieq.iet.model.ClassDef) o;
//            return name.equals(ietCls.getName()) || getFullName().equals(ietCls.getName());
//        }
//        return super.equals(o);
//    }
}

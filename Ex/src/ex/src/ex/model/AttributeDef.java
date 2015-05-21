// $Id: AttributeDef.java 2038 2009-05-21 00:23:51Z labsky $
package ex.model;

import java.util.*;
import java.util.regex.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import uep.util.*;

import ex.util.Trie;
import ex.util.pr.PR_Class;
import ex.util.pr.PR_Evidence;
import ex.util.pr.PR_EvidenceGroup;
import ex.train.*;
import ex.features.PhraseCntAsAttF;
import ex.reader.Document;
import ex.ac.AC;
import ex.ac.TokenPattern;
import ex.ac.TokenPatternSyntaxException;
import ex.wrap.FmtPattern;
import ex.util.pd.*;

public class AttributeDef extends ModelElement {
    public static final int TYPE_UNDEFINED=0;
    public static final int TYPE_NAME     =1;
    public static final int TYPE_TEXT     =2;
    public static final int TYPE_INT      =3;
    public static final int TYPE_FLOAT    =4;
    public static final int TYPE_BOOL     =5;
    public static final int TYPE_DATE     =6;
    public static final int TYPE_TIME     =7;
    public static final int TYPE_DATETIME =8;
    public static String[] dataTypes={
        "UNDEFINED","NAME","TEXT","INT","FLOAT","BOOL","DATE","TIME","DATETIME"
    };
    public static final int INVALID_VALUE = -1;
    public static double PRIOR_DEFAULT = 0.01; // default prior probability for all attributes
    public static double ENGAGED_PROB_DEFAULT = 0.75; // default probability of being engaged as part of myClass
    public static double PRUNE_PROB_DEFAULT = 0.01; // all ACs below this cond prob are discarded
    public static final int MAX_LENGTH_DEFAULT = Integer.MAX_VALUE;
    public static double LENGTH_BOOST = 1e-7;
    
    public int id;
    public String dbName; // name of the db column (or null if the attribute only exists as part of some other container)
    public int dataType;
    public int dim;

    // patterns for context and value
    public TokenPattern[] ctxPatterns;
    public TokenPattern[] valPatterns;
    private List<TokenPattern> ctxPatternList;
    private List<TokenPattern> valPatternList;
    protected List<FmtPattern> locPatternList;
    protected List<DefaultPattern> defPatternList;
    
    // fixed-length list of default evidence placeholders enumerated in DefaultPattern
    public static final int DEF_EVIDENCE_OFF=0;
    public static final int DEF_EVIDENCE_USE_RECALL=1;
    public static final int DEF_EVIDENCE_ON=2;
    public static int default_evidence_mode=DEF_EVIDENCE_OFF; // set by ACFinder from Options
    
    // how to handle length and numeric value evidence
    public static final int LEN_EVIDENCE_OFF=0;
    public static final int LEN_EVIDENCE_BOOST=1;
    public static final int LEN_EVIDENCE_SUPPRESS=2;
    public static int length_evidence_mode=LEN_EVIDENCE_OFF; // set by ACFinder from Options
    public static final String EVNAME_ATTR_LEN = "ATTR_LEN";

    public static final int NUMVAL_EVIDENCE_OFF=0;
    public static final int NUMVAL_EVIDENCE_BOOST=1;
    public static final int NUMVAL_EVIDENCE_SUPPRESS=2;
    public static int numval_evidence_mode=NUMVAL_EVIDENCE_OFF; // set by ACFinder from Options
    public static final String EVNAME_ATTR_NUMVAL = "ATTR_NUMVAL";
    
    // see setBulgarianConstant()
    public static int normalize_ac_probs=1;
    
    // script patterns for value
    public List<ScriptPattern> scriptPatterns;

    // raw value transformation scripts to be applied before normalization
    public List<Axiom> transforms;
    // script to determine whether one attribute value may refer to
    // or be compatible with another value of the same attribute 
    // inside one class instance so that the attribute cardinality is not increased
    public Axiom referScript;
    
    // attribute inheritance
    public int abstractType;
    public AttributeDef parent;
    public int depth;

    public Unit[] units; // first unit is default // public Unit defaultUnit;
    
    // limits + distributions for length, value, cardinality
    // length in tokens:
    public IntDistribution lengthDist;
    public int minLength; public int maxLength; // cached locally (from dist)
    // normalized value (only used for TYPE_INT, TYPE_FLOAT):
    public Distribution valueDist;
    public double minValue; public double maxValue; // cached
    // some attributes may have multiple values (product is made in 4 colors, monitor has multiple resolutions):
    public IntDistribution cardDist;
    public int minCard; public int maxCard; // cached
    
    // ACs below this condProb are discarded
    public double pruneProb;
    
    // contains section
    public ContainedAttribute[] containsList;

    // class we belong to
    public ClassDef myClass;
    // associated features
    public PhraseCntAsAttF phraseCntF; // its value is count a phrase was seen in training marked as this attribute
    // datatype-defined pattern to be used in value patterns
    protected TokenPattern dataTypePattern;

    public double engagedProb; // P(it occurs as part of myClass instance | this attribute occurs) 
    public double bulgarianConstant; // see setBulgarianConstant()
    
    public Axiom[] axioms;
    protected Set<Axiom> axiomSet;
    public String varName;
    
    public short logLevel;
    
    public static Logger log;
    
    public AttributeDef(String n, int dt, ClassDef cls) {
        if(log==null) {
            log=Logger.getLogger("ac");
        }
        name=n;
        dataType=dt;
        dim=1;
        myClass=cls;
        abstractType=0;
        parent=null;
        depth=0;
        units=null;
        valueDist=null; minValue=-1; maxValue=-1; // numeric only
        lengthDist=null; minLength=-1; maxLength=-1;
        cardDist=null; minCard=-1; maxCard=-1;
        pruneProb=-1; // becomes PRUNE_PROB_DEFAULT in prepare() by default
        phraseCntF=null;
        ctxPatterns=null;
        valPatterns=null;
        ctxPatternList=new ArrayList<TokenPattern>(16);
        valPatternList=new ArrayList<TokenPattern>(16);
        locPatternList=new ArrayList<FmtPattern>(8);
        scriptPatterns=new ArrayList<ScriptPattern>(8);
        defPatternList=new ArrayList<DefaultPattern>(8);
        transforms=new ArrayList<Axiom>(8);
        referScript=null; // user-defined; e.g. isReference($,$other) 
        prClass=new PR_Class(this,PRIOR_DEFAULT,null);
        engagedProb=-1; // becomes ENGAGED_PROB_DEFAULT in prepare() by default
        bulgarianConstant=1.0;
        axiomSet=null;
        axioms=null;
        varName="$"+name;
        applySettings();
        logLevel=0;
    }

    public Model getModel() {
        return myClass.getModel();
    }
    
    public int getDefaultEvidenceCount() {
        return defPatternList.size();
    }
    
    /*
    public byte[] getDefaultEvidenceValues() {
        byte[] eVals=new byte[prClass.evs.length];
        java.util.Arrays.fill(eVals, 0, defPatternList.size(), (byte)-1);
        java.util.Arrays.fill(eVals, defPatternList.size(), eVals.length, (byte) 0);
        return eVals;
    }
    */
    
    public int getMinCardOfRootAttribute() {
        AttributeDef ad=this;
        while(ad.parent!=null)
            ad=ad.parent;
        return ad.minCard;
    }

    public int getMaxCardOfRootAttribute() {
        AttributeDef ad=this;
        while(ad.parent!=null)
            ad=ad.parent;
        return ad.maxCard;
    }

    public boolean addPattern(ScriptPattern sp) {
        scriptPatterns.add(sp);
        return true;
    }
    
    public boolean addPattern(DefaultPattern dp) {
        int len=defPatternList.size();
        for(int i=0;i<len;i++) {
            DefaultPattern existing=defPatternList.get(i);
            if(existing.evidence.idx==dp.evidence.idx) {
                existing.evidence.prec=dp.evidence.prec;
                existing.evidence.recall=dp.evidence.recall;
                dp=null;
                break;
            }
        }
        if(dp!=null)
            defPatternList.add(dp);
        return true;
    }
    
    public void addPattern(TokenPattern pat) throws ModelException {
        switch(pat.type) {
        case TokenPattern.PAT_VAL:
            valPatternList.add(pat);
            break;
        case TokenPattern.PAT_CTX_L:
        case TokenPattern.PAT_CTX_R:
        case TokenPattern.PAT_CTX_LR:
            ctxPatternList.add(pat);
            break;
        default:
            throw new ModelException("Only patterns with type=context|value can be added to attribute; type="+pat);
        }
    }

    public void addLocalPatterns(List<FmtPattern> locPats) {
        Iterator<FmtPattern> it=locPats.iterator();
        boolean applies=false;
        while(it.hasNext()) {
            FmtPattern pat=it.next();
            if(pat.layout.contains(this)) {
                addLocalPattern(pat);
                applies=true;
            }
        }
        if(applies) {
            prepareLocalEvidence();
        }
    }
    
    public void addLocalPattern(FmtPattern fmtPat) {
        locPatternList.add(fmtPat);
    }
    
    public void prepareLocalEvidence() {
        Iterator<FmtPattern> it=locPatternList.iterator();
        while(it.hasNext()) {
            PR_Evidence ev=it.next().evidence;
            prClass.addEvidence(ev);
        }
    }
    
    public void clearLocalEvidence() {
        prClass.removeLastEvidence(locPatternList.size());
        locPatternList.clear();
    }
    
    public void addAxiom(Axiom ax) {
        if(axiomSet==null)
            axiomSet=new HashSet<Axiom>(8);
        else if(axiomSet.contains(ax))
            return;
        axiomSet.add(ax);
    }

    private static Trie boolTokens=new Trie(null, (char)0);
    private static final String[] boolTokensTrue ={"yes","y","true","1","ano","a"};
    private static final String[] boolTokensFalse={"no","n","na","n/a","false","0","ne","není"};
    static {
        int i;
        for(i=0;i<boolTokensTrue.length;i++) 
            boolTokens.put(boolTokensTrue[i], Boolean.TRUE);
        for(i=0;i<boolTokensFalse.length;i++) 
            boolTokens.put(boolTokensFalse[i], Boolean.FALSE);
    }

    private static String numberGroupSeps=", ";
    private static String numberFloatSeps=".";
    private static Pattern intPat=null;
    private static Pattern floatPat=null;
    private static Pattern grpsPat=null;
    
    public void applySettings() {
        Options o=Options.getOptionsInstance();
        if(o.getIntDefault("default_evidence_mode", DEF_EVIDENCE_OFF) > DEF_EVIDENCE_OFF) {
            DefaultPattern.addAllPatterns(this);
        }
        LENGTH_BOOST = o.getDoubleDefault("ac_length_boost", LENGTH_BOOST);
        numberGroupSeps = o.getProperty("number_group_seps", numberGroupSeps);
        numberFloatSeps = o.getProperty("number_float_seps", numberFloatSeps);
        intPat=Pattern.compile("[0-9"+numberGroupSeps+"]+");
        floatPat=Pattern.compile("[0-9"+numberGroupSeps+"]*[0-9]["+numberFloatSeps+"][0-9]+");
        grpsPat=Pattern.compile("["+numberGroupSeps+"]");
    }
    
    public Object normalize(AbstractToken[] tokens, int offset, int len) {
        String sep=(dataType==TYPE_INT || dataType==TYPE_FLOAT)? "": " ";
        Object raw=Document.toString(tokens,offset,len,sep);
        Object rc=raw;
        
        // apply all transformations found till root
        AttributeDef ad=this;
        while(ad!=null) {            
            if(ad.transforms.size()>0) {
                myClass.model.clearScope();
                StringBuffer buff=new StringBuffer(64);
                Iterator<Axiom> it=ad.transforms.iterator();
                while(it.hasNext()) {
                    // set attribute value
                    String acScriptString=ad.toScript(buff, raw);
                    myClass.model.eval(acScriptString);
                    // apply transformation
                    Axiom trn=it.next();
                    Object retVal=myClass.model.eval(trn.contentScript);
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"AC "+name+"="+rc+": transformation "+trn+" evaluated to "+retVal+
                            ((retVal!=null && !(retVal instanceof org.mozilla.javascript.Undefined))? 
                                    (" "+retVal.getClass().getCanonicalName()):""));
                    if(retVal==null) {
                        return null; // cannot normalize this attribute 
                    }else if(retVal instanceof org.mozilla.javascript.Undefined) {
                        // transformation does not apply
                    }else {
                        rc=retVal;
                    }
                }
            }
            ad=ad.parent;
        }
        
        // convert to data type, check data type constraints
        switch(dataType) {
        case TYPE_UNDEFINED:
            log.LG(Logger.ERR,"normalize: AC "+name+"="+rc+": UNDEFINED data type, assuming string");	    
        case TYPE_NAME:
        case TYPE_TEXT:
            if(len<minLength || len>maxLength) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"normalize: AC "+name+"="+rc+" len="+len+" out of range ["+minLength+","+maxLength+"]: "+Document.toString(tokens,offset,len," "));
                rc=null;
                break;
            }
            break;

        case TYPE_BOOL:
            if(! (rc instanceof Boolean)) {
                Boolean val=(Boolean) boolTokens.get(rc.toString(), true);
                if(val!=null)
                    rc=val;
            }
            if(! (rc instanceof Boolean)) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"normalize: AC "+name+"="+rc+" cannot be converted to BOOL");
                rc=null;
            }
            break;
	    
        case TYPE_INT: {
            if(rc instanceof Integer || rc instanceof Short || rc instanceof Long) {
                break;
            }
            String data=rc.toString();
            Matcher mat=intPat.matcher(data);
            Exception nex=null;
            if(mat.find()) {
                String num=grpsPat.matcher(mat.group(0)).replaceAll("");
                try {
                    Integer val=new Integer(num);
                    val=(Integer) checkNumRange(val);
                    rc=val;
                }catch(NumberFormatException ex) {
                    nex=ex;
                }
            }
            if(! (rc instanceof Integer)) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"normalize: AC "+name+"="+rc+" cannot be converted to INT "+((nex!=null)?(": "+nex):""));
                rc=null;
            }
            break;
        }

        case TYPE_FLOAT: {
            if(rc instanceof Double || rc instanceof Float) {
                break;
            }
            String data=rc.toString();
            // data=data.replace(',','.');
            Matcher mat=floatPat.matcher(data);
            boolean ok=mat.find();
            if(!ok) {
                mat=intPat.matcher(data);
                ok=mat.find();
            }
            Exception nex=null;
            if(ok) {
                String num=grpsPat.matcher(mat.group(0)).replaceAll("");
                try {
                    Double val=new Double(num);
                    val=(Double) checkNumRange(val);
                    rc=val;
                }catch(NumberFormatException ex) {
                    nex=ex;
                }
            }
            if(! (rc instanceof Double)) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"normalize: AC "+name+"="+rc+" cannot be converted to FLOAT "+((nex!=null)?(": "+nex):""));
                rc=null;
            }
            break;
        }

        case TYPE_DATE:
        case TYPE_TIME:
        case TYPE_DATETIME:
            log.LGERR("Attribute "+name+"='"+Document.toString(tokens,offset,len," ")+"' cannot be converted to DATETIME: NOT IMPLEMENTED; keeping as string");
            break;
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF, "Normalized AC "+name+"="+Document.toString(tokens,offset,len," ")+" type="+dataTypes[dataType]+" to "+rc);
        return rc;
    }
    
    /** Generates an assignment statement like $attName="attValue"; */
    public void scriptAssign(StringBuffer buff, String name, Object attValue) {
        buff.append(name);
        buff.append("=");
        if(attValue instanceof java.lang.String) {
            buff.append("\"");
            // buff.append(qPat.matcher((String)value).replaceAll("\\\""));
            buff.append(uep.util.Util.escapeJSString(attValue.toString()));
            buff.append("\"");
        }else if(attValue==null) {
            buff.append("undefined");
        }else {
            buff.append(attValue);
        }
        buff.append(";\n");
    }
    
    /** Prepares textual version of AC's content script, including assign statements for parent attributes. */
    public String toScript(StringBuffer buff, Object attValue) {
        int idx=buff.length();
        scriptAssign(buff, varName, attValue);
        // TODO: check this handling of specializations is correct
        AttributeDef ad=parent;
        while(ad!=null && (ad.maxCard>1 || ad.maxCard!=-1)) {
            buff.append("if(");
            buff.append(ad.varName);
            buff.append("==undefined)");
            scriptAssign(buff, ad.varName, attValue);
            ad=ad.parent;
        }
        String acScriptString=buff.substring(idx);
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC, "toScript:\n"+acScriptString); 
        return acScriptString; // to be cached by the calling AC
    }
    
    /** Evaluates all script patterns for the given AC, setting the corresponding
     * evidence values. The caller should then call AC.condProb() to recompute 
     * AC score when appropriate. Returns true if all scripts evaluated ok. */
    public boolean evalScriptPatterns(AC ac) throws AxiomException {
        myClass.model.clearScope();
        myClass.model.eval(ac.getScript());
        Iterator<ScriptPattern> it=scriptPatterns.iterator();
        while(it.hasNext()) {
            ScriptPattern sp=it.next();
            Object retVal=myClass.model.eval(sp.axiom.contentScript);
            if(retVal==null) {
                log.LG(Logger.ERR,"ScriptPattern "+sp+" evaluated as null");
                return false;
            }
            if(!(retVal instanceof java.lang.Boolean))
                throw new AxiomException("ScriptPattern "+sp+"("+sp.axiom.lno+") returned "+retVal+
                " for AC="+ac+" (boolean type required)");
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"AC "+ac+": script pattern "+sp+" evaluated to "+retVal);
            if(((Boolean)retVal).booleanValue()) {
                byte val = (byte) ((sp.evidence.prec==-1.0)? -1: 1);
                ac.setEvidenceValue(sp.evidence.idx, val);
            }else {
                byte val = (byte) ((sp.evidence.recall==-1.0)? -1: 0);
                ac.setEvidenceValue(sp.evidence.idx, val);
            }
        }
        return true;
    }
    
    /** Runs value transformation scripts on the raw value of the given AC. 
     *  The raw value must be specified as ac.value (as if it was normalized).
     *  The resulting value will replace it unless the transformation returns undefined.
     *  If the transformation returns null, the AC will not pass normalization.
     *  All other returned types will be subject to normalization based on attribute data type.
     * @param ac The ac whose value is to be transformed.
     * @return true if all transformation scripts evaluated successfully, false on error.
     */
    public boolean evalTransforms(AC ac) throws AxiomException {
        myClass.model.clearScope();
        myClass.model.eval(ac.getScript());
        Iterator<Axiom> it=transforms.iterator();
        while(it.hasNext()) {
            Axiom trn=it.next();
            Object retVal=myClass.model.eval(trn.contentScript);
            log.LG(Logger.ERR,"Transformaton result: "+retVal);
            if(retVal==null) { // we returned undefined
                // keep value as is
            }else if(retVal instanceof org.mozilla.javascript.NativeObject) { 
                // ac.value=null; // TODO: we need to tell null somehow...
                ac.value=retVal.toString();
            }else if(retVal instanceof java.lang.String) {
                ac.value=retVal;
            }else {
                ac.value=retVal.toString();
            }
        }
        return true;
    }

    /** generate alternative representations of the given attribute value according to its datatype */
    public String[] getAlts(String val) {
        String[] alts=null;
        String err=null;
        switch(dataType) {
        case TYPE_NAME:
        case TYPE_TEXT:
            alts=new String[1];
            alts[0]=val;
            break;
	    
        case TYPE_BOOL:
            Boolean b=(Boolean) boolTokens.get(val, true);
            if(b==null)
                break;
            alts=(b.booleanValue())? boolTokensTrue: boolTokensFalse;
            break;
	    
        case TYPE_INT: {
            Integer num=null;
            try {
                num=new Integer(val);
            }catch(NumberFormatException ex) {
                err="Attribute "+name+"='"+val+"' cannot be converted to INT: "+ex.toString();
                break;
            }
            Trie uniq=new Trie(null ,(char)0);
            uniq.put(val); // original
            uniq.put(String.valueOf(num.intValue())); // no thousands separator
            DecimalFormatSymbols symbols=new DecimalFormatSymbols();
            for(int i=0;i<numberGroupSeps.length();i++) {
                symbols.setGroupingSeparator(numberGroupSeps.charAt(i)); // all thousands separators
                DecimalFormat fmt=new DecimalFormat("###,###", symbols);
                fmt.setGroupingSize(3);
                uniq.put(fmt.format(num.intValue())); // alternative
            }
            alts=new String[uniq.size()];
            uniq.fillArray(alts,0);
            break;
        }

        case TYPE_FLOAT: {
            Double num=null;
            try {
                num=new Double(val);
            }catch(NumberFormatException ex) {
                err="Attribute "+name+"='"+val+"' cannot be converted to FLOAT: "+ex.toString();
                break;
            }
            Trie uniq=new Trie(null ,(char)0);
            uniq.put(val); // original
            /* 1. patterns where only integer part is specified */
            uniq.put(String.valueOf(Math.round(num.doubleValue()))); // no thousands separator
            DecimalFormatSymbols symbols=new DecimalFormatSymbols();
            for(int i=0;i<numberGroupSeps.length();i++) {
                symbols.setGroupingSeparator(numberGroupSeps.charAt(i));
                DecimalFormat fmt=new DecimalFormat("###,###", symbols);
                fmt.setGroupingSize(3);
                uniq.put(fmt.format(num.doubleValue()));
            }

            /* 2. patterns where float part with lengths 1,2,3 is specified */
            for(int i=0;i<numberGroupSeps.length();i++) {
                symbols.setGroupingSeparator(numberGroupSeps.charAt(i));
                for(int m=0;m<2;m++) {
                    for(int j=0;j<numberFloatSeps.length();j++) {
                        symbols.setDecimalSeparator(numberFloatSeps.charAt(j));
                        DecimalFormat fmt=new DecimalFormat("###,###.###", symbols);
                        fmt.setGroupingSize(3);
                        for(int k=1;k<=2;k++) {
                            fmt.setMinimumFractionDigits(k);
                            fmt.setMaximumFractionDigits(k);
                            uniq.put(fmt.format((m==0)? num.doubleValue(): Math.round(num.doubleValue()))); // alternative
                        }
                    }
                }
            }
            alts=new String[uniq.size()];
            uniq.fillArray(alts,0);
            break;
        }

        case TYPE_DATE:
        case TYPE_TIME:
        case TYPE_DATETIME:
            log.LG(Logger.ERR,"Attribute "+name+"='"+val+"' cannot be converted to DATETIME: NOT IMPLEMENTED");
            alts=new String[1];
            alts[0]=val;
            break;
        }

        /* on failure, just give the single original possibility */
        if(alts==null) {
            log.LG(Logger.WRN,"Cannot convert "+dataTypes[dataType]+" attribute training value '"+val+"' to "+dataTypes[dataType]);
            alts=new String[1];
            alts[0]=val;
        }
        /* log it */
        if(log.IFLG(Logger.TRC))  {
            String lg="Alts: ";
            for(int i=0;i<alts.length;i++) {
                if(i>0) lg+= "|";
                lg+= alts[i];
            }
            log.LG(Logger.TRC,lg);
        }
        if(err!=null)
            log.LG(Logger.TRC,err);
        
        return alts;
    }

    public void prepare() throws ModelException {
        /* prepare any unprepared ancestors */
        if(parent!=null && (parent.prClass==null || parent.prClass.evs==null)) 
            parent.prepare();

        super.prepare();
        
        if(engagedProb==-1)
            engagedProb = (parent!=null)? parent.engagedProb: ENGAGED_PROB_DEFAULT;
        if(pruneProb==-1)
            pruneProb = (parent!=null)? parent.pruneProb: PRUNE_PROB_DEFAULT;

        if(lengthDist==null && parent!=null)
            lengthDist=parent.lengthDist;
        if(minLength==-1)
            minLength = (parent!=null)? parent.minLength: 1;
        if(maxLength==-1)
            maxLength = (parent!=null)? parent.maxLength: MAX_LENGTH_DEFAULT;

        if(valueDist==null && parent!=null)
            valueDist=parent.valueDist;
        if(minValue==-1 && parent!=null)
            minValue = parent.minValue;
        if(maxValue==-1 && parent!=null)
            maxValue = parent.maxValue;

        if(cardDist==null && parent!=null) {
            cardDist=new IntDistribution(0, parent.cardDist.getMaxIntValue());
            minCard=0;
            maxCard=cardDist.getMaxIntValue();
        }
        if(cardDist==null || minCard==-1) {
            throw new ModelException("Cardinality not specified for "+name);
        }
        
        // own evidence counts
        int vlen=valPatternList.size();
        int clen=ctxPatternList.size();
        int dlen=defPatternList.size(); // same length for all attributes; recalls may differ
        int slen=scriptPatterns.size();
        int glen=0;
        if(prClass.evidenceGroups!=null) {
            for(PR_EvidenceGroup grp: prClass.evidenceGroups) {
                if(grp.type==PR_EvidenceGroup.GRP_AND) {
                    glen++;
                }
            }
        }
        // parent's evidence count
        int pEvLen=0;

        // add evidence from ancestors as references to their PR_Evidence (evidence.idx remains the same eventhough class changes)
        if(parent!=null) {
            pEvLen=parent.prClass.evs.length - parent.defPatternList.size(); // dlen == parent.defaultEvidenceArray.length
            if(lengthDist==null)
                lengthDist=parent.lengthDist;
        }
        // add default evidence
        PR_Evidence[] evs=new PR_Evidence[pEvLen+dlen+vlen+clen+slen+glen];
        
        for(int i=0;i<dlen;i++) {
            evs[i]=defPatternList.get(i).evidence;
            evs[i].idx=i;
        }
        if(pEvLen>0) {
            System.arraycopy(parent.prClass.evs, parent.defPatternList.size(), evs, dlen, pEvLen);
        }
        int baseIdx=pEvLen+dlen;
        valPatterns=new TokenPattern[vlen];
        for(int i=0;i<vlen;i++) {
            valPatterns[i]=(TokenPattern)valPatternList.get(i);
            if(valPatterns[i].evidence==null) {
                throw new ModelException("Attribute '"+name+"' has no or invalid evidence at index "+i+" of "+vlen);
            }
            evs[baseIdx+i]=valPatterns[i].evidence;
            evs[baseIdx+i].idx=baseIdx+i;
        }
        //valPatternList=null;

        baseIdx+=vlen;
        ctxPatterns=new TokenPattern[clen];
        for(int i=0;i<clen;i++) {
            ctxPatterns[i]=(TokenPattern)ctxPatternList.get(i);
            evs[baseIdx+i]=ctxPatterns[i].evidence;
            evs[baseIdx+i].idx=baseIdx+i;
        }
        //ctxPatternList=null;
        
        baseIdx+=clen;
        for(int i=0;i<slen;i++) {
            ScriptPattern sp=scriptPatterns.get(i);
            evs[baseIdx+i]=sp.evidence;
            evs[baseIdx+i].idx=baseIdx+i;
            sp.axiom.prepare(myClass.model, myClass);
        }
        
        baseIdx+=slen;
        if(glen>0) {
            int i=0;
            for(PR_EvidenceGroup grp: prClass.evidenceGroups) {
                if(grp.type==PR_EvidenceGroup.GRP_AND) {
                    evs[baseIdx+i]=grp;
                    evs[baseIdx+i].idx=baseIdx+i;
                    i++;
                }
            }
        }

        prClass.setEvidence(evs);

        /* contains section */
        for(int i=0;containsList!=null && i<containsList.length; i++) {
            AttributeDef ad=(AttributeDef) myClass.attributes.get(containsList[i].obj);
            if(ad==null)
                throw new ModelException("Attribute '"+name+"' contains unknown attribute '"+containsList[i].obj+"'");
            containsList[i].attDef=ad;
        }

        /* axioms that apply to this attribute */
        if(axiomSet!=null) {
            int acnt=axiomSet.size();
            axioms=new Axiom[acnt];
            Iterator<Axiom> it=axiomSet.iterator();
            int i=0;
            while(it.hasNext()) {
                axioms[i]=it.next();
                i++;
            }
            axiomSet=null;
        }
        
        /* compile value transform scripts */
        for(int i=0;i<transforms.size();i++) {
            transforms.get(i).prepare(myClass.model, myClass);
        }

        /* compile custom reference detection script */
        if(referScript!=null) {
            referScript.prepare(myClass.model, myClass);
        }
        
        // determine depth in inheritance hierarchy
        depth=1;
        AttributeDef ad=parent;
        while(ad!=null) {
            depth++;
            ad=ad.parent;
        }
        
        // sort evidence groups if any
        prClass.prepare();
        
        // precompute normalization constant
        setBulgarianConstant();
    }

    public TokenPattern getDataTypePattern() throws TokenPatternSyntaxException {
        if(dataTypePattern!=null) // return my own special, otherwise standard datatype model
            return dataTypePattern;
        return myClass.model.getDataTypePattern(dataTypes[dataType]);
    }

    public String getFullName() {
        return myClass.name+"."+name;
    }

    public String toString() {
        return name;
    }

    /** @return true when this Attribute is strictly descendant of (not equal to) given ancestor. */
    public boolean isDescendantOf(AttributeDef anc) {
        AttributeDef ad=parent;
        while(ad!=null) {
            if(ad==anc)
                return true;
            ad=ad.parent;
        }
        return false;
    }

    /** Gets the depth in inheritance hierarchy, 1 if no parent AttributeDef exists */
    public int getDepth() {
        return depth;
    }

    /** Returns how many times @ad attribute can be contained. Is intentionally not recursive.
     */
    public int canContain(AttributeDef ad) {
        if(containsList==null)
            return 0;
        for(int i=0;i<containsList.length;i++) {
            if(containsList[i].attDef==ad) {
                return containsList[i].maxCard;
            }
        }
        return 0;
    }

    public Number checkNumRange(Number n) {
        if((minValue!=-1.0 && n.doubleValue()<minValue) ||
           (maxValue!=-1.0 && n.doubleValue()>maxValue)) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Numeric AC "+name+"="+n+" out of range ["+minValue+","+maxValue+"]");
            n=null;
        }
        return n;
    }
    
    // normalizes 2 probabilities of AC being extracted standalone (orphanProb) 
    // and as part of an instance (engagedProb); this is in order not to damage 
    // these 2 alternatives as opposed to the probability of this AC being false alarm (mistakeProb)
    private void setBulgarianConstant() {
        if(normalize_ac_probs!=0)
            bulgarianConstant = 1 / ((engagedProb>=0.5)? engagedProb: (1-engagedProb));
        else
            bulgarianConstant = 1;
    }

    /** Returns REF_NO if the two ACs do not refer to a common entity.
     * Otherwise, if it is possible to tell the "primary" occurence from the two,
     * either REF_YES or REF_INVERSE is returned to indicate that 
     * reference refers to primary or vice versa, respectively; otherwise REF_BIDIRECTIONAL is returned. */
    public byte references(AC primary, AC reference) {
        byte rc=AC.REF_NO;
        // TODO: eval custom scripts if given for this AttributeDef
        if(primary.getAttribute().referScript!=null) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"evalRef: pri="+primary.getNameText()+", ref="+reference.getNameText());
            rc=myClass.model.evalReferenceScript(primary, reference);
        }else {
            switch(this.dataType) {
            case TYPE_NAME:
            case TYPE_TEXT:
                if((primary.value.toString()).trim().equalsIgnoreCase((reference.value.toString()).trim()))
                    rc=AC.REF_BIDIRECTIONAL;
                break;
            default:
                if(primary.value.equals(reference.value))
                    rc=AC.REF_BIDIRECTIONAL;
            }
        }
        int lvl=(rc==AC.REF_NO)? Logger.TRC: Logger.INF;
        if(log.IFLG(lvl)) log.LG(lvl,"refrc="+rc+": pri:"+primary.getAttribute().name+"="+primary.getText()+", sec:"+reference.getAttribute().name+"="+reference.getText());
        return rc;
    }
    
//    public boolean equals(Object o) {
//        if(o instanceof medieq.iet.model.AttributeDef) {
//            medieq.iet.model.AttributeDef ietAd=(medieq.iet.model.AttributeDef) o;
//            return name.equals(ietAd.getName()) || getFullName().equals(ietAd.getName());
//        }
//        return super.equals(o);
//    }
}

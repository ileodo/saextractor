// $Id: AC.java 2038 2009-05-21 00:23:51Z labsky $
package ex.ac;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

//import org.cyberneko.html.HTMLElements;

import java.util.LinkedList;
import java.util.List;

import org.mozilla.javascript.Script;
import uep.util.Logger;

import ex.util.pr.*;
import ex.reader.TokenAnnot;
import ex.reader.TagAnnot;
//import ex.features.TagTypeF;
import ex.reader.Document;
import ex.model.AttributeDef;
import ex.model.DefaultPattern;
import ex.model.ModelElement;
import ex.util.pd.IntDistribution;
import ex.util.pd.FloatDistribution;
import ex.util.pd.Distribution;

public class AC extends PR_Example implements Annotable, Extractable {
    public static final int OVL_NONE=0;
    public static final int OVL_CROSS=1;
    public static final int OVL_CONTAINS_CAN=2;
    public static final int OVL_CONTAINS_CANNOT=3;
    public static final int OVL_CONTAINED_CAN=4;
    public static final int OVL_CONTAINED_CANNOT=5;
    public static final int OVL_ERR=-1;
    // protected static final Pattern qPat=Pattern.compile("\"");

    /** AC.references return codes */
    public static final byte REF_YES=1;
    public static final byte REF_INVERSE=2;
    public static final byte REF_BIDIRECTIONAL=3;
    public static final byte REF_SPECIALIZED=4;
    public static final byte REF_NO=5;
    
    public TokenAnnot startToken;
    public int len;
    public Object value;
    protected String cachedString;
    protected String cachedScriptString;
    protected Script cachedScript; // compiled version of cachedString
    
    protected double latticeProb;

    public Document doc;
    
    // Other ACs of the same AttributeDef with the same or equivalent value 
    // found in the vicinity of this AC; this list is assembled before instance parsing.
    // refersTo list contains just 'reference candidates' which may be later confirmed or ignored
    public LinkedList<AC> refersTo;
    
    static Logger log;

    public AC(AttributeDef ad, TokenAnnot sta, int ln, boolean cValue, byte[] eVs, Document doc) {
        super(ad, ad.prClass, cValue, eVs); // init PR_Example with ad (PR_Class)
        if(log==null) {
            log=Logger.getLogger("ac");
        }
        this.doc=doc;
        startToken=sta;
        len=ln;
        setValue();
        cachedString=null;
        cachedScript=null;

        if(value==null) // could not be normalized
            return;
        /*
        StringBuffer b=new StringBuffer(64);
        getText(b);
        if(b.toString().equals("202 / 347 - 8600")) {
            boolean stopDebuggerHere=true;
        }
        */       
        
        if(AttributeDef.default_evidence_mode>AttributeDef.DEF_EVIDENCE_OFF) {
            applyDefaultEvidence();
        }
        if(AttributeDef.length_evidence_mode!=AttributeDef.LEN_EVIDENCE_OFF) {
            applyLengthEvidence();
        }
        if(AttributeDef.numval_evidence_mode!=AttributeDef.NUMVAL_EVIDENCE_OFF) {
            applyValueEvidence();
        }
        if(ad.scriptPatterns.size()>0) {
            applyScriptPatterns();
        }
        //Logger.LOG(Logger.TRC,"AC(PR_Example) "+toString()+" condProb="+classProb); // not all feats set yet
        latticeProb=-1;
        
        //condProb(); // this is not final computation; further evidence may be added; just to log something
        //if(log.IFLG(Logger.INF)) log.LG(Logger.INF, "Normalized AC "+ad.name+"="+Document.toString(doc.tokens,startToken.idx,len," ")+" type="+AttributeDef.dataTypes[ad.dataType]+" to "+value); //  +" p="+classProb);
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF, "create: "+this);
    }
    
    public void clear() {
        startToken=null;
        value=null;
        cachedString=null;
        cachedScriptString=null;
        cachedScript=null;
        doc=null;
        if(refersTo!=null) {
            refersTo.clear();
            refersTo=null;
        }
        super.clear();
    }

    /** Attempts to compute normalized value with respect to data type */
    public void setValue() {
        value=getAttribute().normalize(doc.tokens, startToken.idx, len);
    }
    
    public double condProb() {
        super.condProb(); // sets classProb
        classProb+= this.getLength() * AttributeDef.LENGTH_BOOST;
        if(classProb>1)
            classProb=1;
        return classProb;
    }
    
    /** Returns a rc indicating the overlap condition of this AC with respect to the given ac. */
    public int overlapsWith(AC ac) {
        int si=startToken.idx, ei=si+len-1;
        int si2=ac.startToken.idx, ei2=si+ac.len-1;
        if(ei<si2 || ei2>si)
            return AC.OVL_NONE;
        if((si2>si && ei2>ei) || (si>si2 && ei>ei2))
            return AC.OVL_CROSS;
        AttributeDef ad=getAttribute();
        AttributeDef ad2=ac.getAttribute();
        // I contain ac && I am contained in ac:
        if(si2==si && ei2==ei) {
            if(ad2.canContain(ad)>0)
                return OVL_CONTAINED_CAN;
            if(ad.canContain(ad2)>0)
                return OVL_CONTAINS_CAN;
            return OVL_CONTAINED_CANNOT;
        }
        // I contain:
        if(si2>=si && ei2<=ei)
            return (ad.canContain(ad2)>0)? OVL_CONTAINS_CAN: OVL_CONTAINS_CANNOT;
        if(si>=si2 && ei<=ei2)
            return (ad2.canContain(ad)>0)? OVL_CONTAINED_CAN: OVL_CONTAINED_CANNOT;
        // impossible:
        log.LG(Logger.ERR,"overlapsWith internal error");
        return OVL_ERR;
    }

    public TagAnnot getParentBlock(int type) {
        return doc.getParentBlock(startToken, doc.tokens[startToken.idx+len-1], type);
    }

    /** Boosts this AC with the context pattern match pm or creates a new AC 
     *  which is a specialization of this AC with identical position and length in case 
     *  the context pattern match indicates the specialization of this AC's AttributeDef. 
     *  Returns 1 if the pattern was applied or 0 if the pattern had no effect on this AC. */
    public int applyContextPattern(PatMatch pm, Document doc, List<AC> derivedACs) {
        int cnt=0;
        AttributeDef myAttr=getAttribute();
        AttributeDef evAttr=pm.pat.getAttributeDef();
        // evidence supports exactly the AC's attribute, or its generalization
        if(myAttr==evAttr || myAttr.isDescendantOf(evAttr)) {
            setEvidenceValue(pm.pat.evidence.idx, (byte)1);
            cnt++;
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Boosting ac "+this+" with ctx "+pm);
        }
        /* evidence supports attribute's specialization: if AC does not exist for it, 
	       add it with the same evidence as its generalization and boost it */
        else if(evAttr.isDescendantOf(myAttr)) {
            AC specAC=startToken.findAC(evAttr, false);
            if(specAC==null) {
                specAC=findAC(derivedACs, evAttr, startToken, len);
            }
            if(specAC==null) {
                // specAC=new AC(evAttr,startToken,len,false,evAttr.getDefaultEvidenceValues(),doc);
                specAC=new AC(evAttr,startToken,len,false,null,doc);
                if(specAC.value==null) {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Specialized AC "+specAC+" was not created due to unsatisfied constraints");
                }else {
                    specAC.copyEvidenceValuesFrom(this);
                    specAC.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                    specAC.condProb();
                    // specAC.setValue(); // called in ctor
                    // do not add yet, only assemble, having it present in this token would prevent further specialized ACs 
                    // produced by competing ACs:
                    // doc.addAC(specAC);
                    derivedACs.add(specAC);
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Produced new specialized AC="+specAC);
                    cnt++;
                }
            }
        }
        return cnt;
    }
    
    public int applyDefaultEvidence() {
        int[] crossTagCounts={0,0,0,0};
        doc.fillCrossTagCounts(startToken.idx, startToken.idx+len-1, crossTagCounts);
        if(crossTagCounts[0]>0) {
            setEvidenceValue(DefaultPattern.EVIDENCE_NOCROSS_INLINE_TAG, (byte)0);
        }
        if(crossTagCounts[1]>0) {
            setEvidenceValue(DefaultPattern.EVIDENCE_NOCROSS_BLOCK_TAG, (byte)0);
        }
        if(crossTagCounts[2]==0) {
            setEvidenceValue(DefaultPattern.EVIDENCE_FIT_IN_TAG, (byte)0);
        }
        if(crossTagCounts[3]==0) {
            setEvidenceValue(DefaultPattern.EVIDENCE_HAS_ONE_PARENT, (byte)0);
        }
        return 0;
    }
    
    public void applyLengthEvidence() {
        PR_Evidence ev=null;
        byte val=1;
        AttributeDef ad=getAttribute();
        if(ad.lengthDist==null) { // || ad.lengthDist.getType()!=Distribution.TYPE_MINMAX
            // TODO: provide some default length evidence if not supplied: this is a must since
            // distribution-less attributes would get suppressed in LEN_EVIDENCE_BOOST mode;
            // or would be favored in LEN_EVIDENCE_SUPPRESS mode
        }else {
            ev=new PR_Evidence(AttributeDef.EVNAME_ATTR_LEN, 0.0, 0.0, (byte)0, -1);
            if(len>=ad.lengthDist.getMinIntValue() && len<=ad.lengthDist.getMaxIntValue()) {
                if(ad.lengthDist.getType()==Distribution.TYPE_MINMAX) {
                    // don't apply length evidence for values within min/max only modelled by min/max
                    ev=null;
                }else if(AttributeDef.length_evidence_mode==AttributeDef.LEN_EVIDENCE_BOOST) {
                    ev.recall=ad.lengthDist.getProb(len);
                    /* use Bayes formula to obtain P(att|len) from P(len|att);
                       the denominator contains P(len) which we consider 1/max_att_len
                       the P(att) in nominator = ad.prClass.prior * 1/max_att_len 
                       (ad.prClass.prior says the portion of tokens which start the attr) 
                       1/max_att_len get cancelled */
                    ev.prec = ev.recall * ad.prClass.prior;
                    val=1;
                
                }else { // LEN_EVIDENCE_SUPPRESS
                    double prob_of_observing_better_value=ad.lengthDist.getBetterValueProb(len);
                    ev.recall = prob_of_observing_better_value;
                    // int lenRange = ad.lengthDist.getMaxIntValue() - ad.lengthDist.getMinIntValue() + 1;
                    ev.prec = ad.prClass.prior * 2; // really don't know what to put here... hopefully of marginal importance
                    val=0;
                }
            }else {
                ev.recall=1.0;
                ev.prec=ad.prClass.prior * 2; // anything>prior will do
                val=0;
            }
        }
        if(ev!=null) {
            addCustomEvidence(ev, val);
        }
    }
    
    public void applyValueEvidence() {
        if(value==null)
            return;
        AttributeDef ad=getAttribute();
        double doubleValue;
        switch(ad.dataType) {
        case AttributeDef.TYPE_INT:
        case AttributeDef.TYPE_FLOAT:
            doubleValue=((Number)value).doubleValue();
            break;
        default:
            return;
        }
        PR_Evidence ev=null;
        byte val=0;
        if(ad.valueDist==null || ad.valueDist.getType()==Distribution.TYPE_MINMAX) {
            // TODO: hopefully nothing
        }else {
            ev=new PR_Evidence(AttributeDef.EVNAME_ATTR_NUMVAL, 0.0, 0.0, (byte)0, -1);
            if(doubleValue>=ad.valueDist.getMinValue() && doubleValue<=ad.valueDist.getMaxValue()) {
                double prob_of_observing_better_value = (ad.valueDist.getRangeType()==Distribution.RANGE_INT)? 
                        ((IntDistribution)ad.valueDist).getBetterValueProb(((Number)value).intValue()) :
                        ((FloatDistribution)ad.valueDist).getBetterValueProb(doubleValue);
                ev.recall = prob_of_observing_better_value;
                ev.prec = ad.prClass.prior * 2; // really don't know what to put here... hopefully of marginal importance
                val=0;
            }else {
                ev.recall=1.0;
                ev.prec=ad.prClass.prior * 2; // anything>prior will do
                val=0;
            }
        }
        if(ev!=null) {
            addCustomEvidence(ev, val);
        }
    }

    public void applyScriptPatterns() {
        //StringBuffer b=new StringBuffer(64);
        //getText(b);
        //if(b.toString().equals("162 00")) {
        //    int stop=1;
        //}
        getAttribute().evalScriptPatterns(this);
    }

    public AttributeDef getAttribute() {
        return (AttributeDef) obj;
    }
    
    /* Annotable interface */
    public int getLength() { return len; }

    public int getType() { 
        return Annotable.TYPE_AC;
    }

    public double getProb() {
        return classProb;
    }
    
    public boolean isHopeless() {
        return getProb()<getAttribute().pruneProb;
    }

    public ModelElement getModelElement() {
        return (ModelElement) obj;
    }

    /* Extractable interface */

    /** probability of this AC (either orphan or engaged - depending on the task), 
      * used for best path search in lattice */
    public double getLatticeProb() {
        if(latticeProb==-1)
            latticeProb=Math.log(getProb());
        return latticeProb;
    }

    public void setLatticeProb(double newScore) {
        // throw new UnsupportedOperationException("Cannot set lattice probability of AC; it is always computed from P(attr|evidence) and P(part_of_instance|attr)");
        latticeProb=newScore;
    }

    /** probability if orphan */
    public double getOrphanProb() {
        // AC really represents Attribute but is standalone (not part of any instance),
        // or it does not represent an Attribute at all (AC should not exist)
        // return Math.log( 1.0 - (getAttribute().engagedProb * getProb()) );
        // could also be computed as:
        // (getProb() * (1.0 - getAttribute().engagedProb)) + (1.0 - getProb());
        
        // we must not merge the above 2 cases, here we consider just:
        double p = getProb() * (1.0 - getAttribute().engagedProb) * getAttribute().bulgarianConstant;
        return Math.log(p);
    }
    
    /** probability if engaged in IC */
    public double getEngagedProb() {
        double p = getProb() * getAttribute().engagedProb * getAttribute().bulgarianConstant;
        if(p>1) {
            log.LGERR("engprob="+p+" of ac="+this);
            p=1;
        }
        return Math.log(p);
    }

    /** probability of this AC being 'false alarm' */
    public double getMistakeProb() {
        return Math.log( 1.0 - getProb() );
    }

    public int getStartIdx() {
        return startToken.idx;
    }

    public int getEndIdx() {
        return doc.tokens[startToken.idx+len-1].idx;
    }

    /* Comparable interface - see PR_Example */
    public int compareTo(PR_Example ex) {
        int rc=super.compareTo(ex);
        if(rc==0 && ex instanceof AC) {
            // favor the subclass:
            if(this.getAttribute().isDescendantOf(((AC)ex).getAttribute())) {
                rc=1;
            }else if(((AC)ex).getAttribute().isDescendantOf(this.getAttribute())) {
                rc=-1;
            }
        }
        return rc;
    }

    public TokenAnnot getStartToken() {
        return startToken;
    }

    public TokenAnnot getEndToken() {
        return doc.tokens[startToken.idx+len-1];
    }

    public StringBuffer getText(StringBuffer buff) {
        buff.append(startToken.token);
        for(int i=1;i<len;i++) {
            buff.append(" ");
            buff.append(doc.tokens[startToken.idx+i].token);
        }
        return buff;
    }
    
    public String getText() {
        return getText(new StringBuffer(64)).toString();
    }

    public String getNameText() {
        StringBuffer b=new StringBuffer(64);
        b.append(getAttribute().name);
        b.append("=");
        getText(b);
        return b.toString();
    }

    public void toHtmlString(StringBuffer buff) {
        //if(cachedString!=null) {
        //    buff.append(cachedString);
        //    return;
        //}
        int idx=buff.length();
        buff.append(getAttribute().toString());
        buff.append("=");
        getText(buff);
        buff.append(" ("+PR_Class.fmtNum(getProb())+")");
        boolean verbose=true;
        if(verbose) {
            buff.append("; ");
            buff.append("P(apr)="+PR_Class.fmtNum(cls.prior)+"\n");
            //if(cls.evs.length!=eVals.length) {
                // Logger.LOG(Logger.ERR,"Class evidence list len="+cls.evs.length+" example list len="+eVals.length);
            //}
            for(int i=0;i<cls.evs.length;i++) {
                buff.append("; ");
                PR_Evidence ev=cls.evs[i];
                if(eVals.length<=i) {
                    // Logger.LOG(Logger.ERR,ev.name + " is missing from evidence list!");
                    buff.append(ev.name + "=n/a");
                    continue;
                }
                if(eVals[i]!=0)
                    buff.append("<span style=\"color:"+((eVals[i]==1)? "green": "orange")+"\">");
                buff.append(ev.name + "=" + eVals[i] +
                        " P(A|e"+(i+1)+")="+PR_Class.fmtNum(ev.prec)+
                        " P(e"+(i+1)+"|A)="+PR_Class.fmtNum(ev.recall));
                if(eVals[i]!=0)
                    buff.append("</span>");
            }
        }
        cachedString=buff.substring(idx);        
    }

    // debugging only
    public String toString(StringBuffer b) {
        //if(cachedString!=null)
        //    return cachedString;
        // toString(b);
        b.append(getAttribute().name+"=");
        getText(b);
        b.append("["+this.getStartIdx()+","+this.getEndIdx()+"]("+PR_Class.fmtNum(getProb())+")");
        return b.toString();
    }

    public String toString() {
        StringBuffer b=new StringBuffer(128);
        toString(b);
        return b.toString();
    }

    public String toStringIncEvidence(int evidenceFilter) {
        StringBuffer b=new StringBuffer(128);
        toString(b);
        if(evidenceFilter>0) {
            for(int i=0;i<cls.evs.length;i++) {
                if(eVals[i]==1) {
                    PR_Evidence ev=cls.evs[i];
                    b.append(","+ev.name+"="+eVals[i]);
                }
            }
            for(int i=0;i<cls.evs.length;i++) {
                if(eVals[i]!=1 && 
                  (evidenceFilter==2 || (eVals[i]==0 && cls.evs[i].recall>=0.05))) {
                    PR_Evidence ev=cls.evs[i];
                    b.append(","+ev.name+"="+eVals[i]);
                }
            }            
        }
        return b.toString();
    }
    
//    private void scriptAssign(AttributeDef ad, StringBuffer buff) {
//        buff.append(ad.varName);
//        buff.append("=");
//        if(value instanceof java.lang.String) {
//            buff.append("\"");
//            // buff.append(qPat.matcher((String)value).replaceAll("\\\""));
//            buff.append(uep.util.Util.escapeJSString(((String)value)));
//            buff.append("\"");
//        }else {
//            buff.append(value);
//        }
//        buff.append(";\n");
//    }
    
    /* Prepares textual version of content script. */
    public void toScript(StringBuffer buff) {
        if(cachedScriptString!=null) {
            buff.append(cachedScriptString);
            return;
        }
        cachedScriptString=getAttribute().toScript(buff, value);
    }
  
//    public void toScript(StringBuffer buff) {
//        if(cachedScriptString!=null) {
//            buff.append(cachedScriptString);
//            return;
//        }
//        int idx=buff.length();
//        AttributeDef ad=getAttribute();
//        scriptAssign(ad, buff);
//        // TODO: check this handling of specializations is correct
//        ad=ad.parent;
//        while(ad!=null && (ad.maxCard>1 || ad.maxCard!=-1)) {
//            buff.append("if(");
//            buff.append(ad.varName);
//            buff.append("==undefined)");
//            scriptAssign(ad, buff);
//            ad=ad.parent;
//        }
//        cachedScriptString=buff.substring(idx);
//    }

    /* TODO: cachedJS used once in AC.evalScriptPatterns() and once during TIC1 creation
     * however we could re-think the single Script generation per IC in multiple Script 
     * evaluation per AC, avoiding repetitive script compilation. */
    public Script getScript() {
        if(cachedScript!=null) {
            return cachedScript;
        }
        StringBuffer buff=new StringBuffer(96);
        cachedScriptString = getAttribute().toScript(buff, value);
        buff.setLength(0);
        getText(buff);
        cachedScript=getAttribute().myClass.model.compile(cachedScriptString, "AC "+buff);
        return cachedScript;
    }
    
    public void resetCachedScript() {
        cachedScriptString=null;
        cachedScript=null;
    }
    
    public String toTableRow() {
        return toTableRow(false, false);
    }

    public String toTableRow(boolean head, boolean isReference) {
        return "<tr "+(head? "style=\"color:blue\"": "")+"><td>"+
            (isReference? "ref:": "")+getAttribute().name+"</td><td>"+value+"</td><td>("+
            PR_Class.fmtNum(getProb())+")</td></tr>\n";
    }

    /** TODO: speed; replace this impl. by map/sortedset. */
    protected static AC findAC(List<AC> lst, AttributeDef ad, TokenAnnot startToken, int len) {
        AC ret=null;
        for(AC cand: lst) {
            if((ad==null || cand.getAttribute()==ad) &&
               (startToken==null || cand.startToken==startToken) && 
               (len==-1 || cand.len==len)) {
                ret=cand;
                break;
            }
        }
        return ret;
    }
    
    /** Returns REF_NO if the two ACs do not refer to a common entity;
     * otherwise, if it is possible to tell the "primary" occurrence from the two,
     * either REF_YES or REF_INVERSE is returned; otherwise REF_BIDIRECTIONAL is returned. */
    public byte references(AC other, List<AC> inducedSpecials) {
        byte rc=REF_NO;
        if(this.value!=null && other.value!=null) {
            if(getAttribute()==other.getAttribute()) {
                rc=getAttribute().references(this,other);
            }else if(inducedSpecials!=null) {
                if(getAttribute().isDescendantOf(other.getAttribute())) {
                    // other is to be specialized
                    AC spec=other.startToken.findAC(getAttribute(), true, other.len);
                    if(spec==null) {
                        spec=findAC(inducedSpecials, getAttribute(), other.startToken, other.len);
                    }
                    if(spec==null) {
                        // for 2nd arg (the reference $other), the reference script does not care about its 
                        // attribute name so we can use it for the generalization:
                        rc=getAttribute().references(this,other);
                        if(rc!=REF_NO) {
                            spec=new AC(getAttribute(),other.startToken,other.len,false,null,doc);
                            if(spec.value==null) {
                                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Specialized referenced AC "+spec+" not created due to unsatisfied constraints");
                                rc=REF_NO;
                            }else {
                                spec.copyEvidenceValuesFrom(other);
                                spec.condProb();
                                // doc.addAC(spec);
                                inducedSpecials.add(spec);
                                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Specialized referenced AC="+spec.toStringIncEvidence(1)+" from "+other.toStringIncEvidence(1));
                                rc=REF_SPECIALIZED;
                            }
                        }
                    }
                }else if(other.getAttribute().isDescendantOf(getAttribute())) {
                    // this is to be specialized
                    AC spec=startToken.findAC(other.getAttribute(), true, len);
                    if(spec==null) {
                        spec=findAC(inducedSpecials, other.getAttribute(), startToken, len);
                    }
                    if(spec==null) {
                        // must use the 2nd arg $other, for the generalization, which is the opposite order,
                        // therefore REF_INVERSE and REF_YES are swapped, but this does not matter 
                        rc=getAttribute().references(other,this);
                        if(rc!=REF_NO) {
                            spec=new AC(other.getAttribute(),startToken,len,false,null,doc);
                            if(spec.value==null) {
                                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Specialized referenced AC "+spec+" not created due to unsatisfied constraints");
                                rc=REF_NO;
                            }else {
                                spec.copyEvidenceValuesFrom(this);
                                spec.condProb();
                                // doc.addAC(spec);
                                inducedSpecials.add(spec);
                                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Specialized referenced AC="+spec.toStringIncEvidence(1)+" from "+this.toStringIncEvidence(1));
                                rc=REF_SPECIALIZED;
                            }
                        }
                    }
                }
            }
        }
        return rc;
//        if(getAttribute()!=other.getAttribute() || this.value==null || other.value==null)
//            return REF_NO;
//        return getAttribute().references(this,other);
    }
    
    /** Adds a reference to another AC, e.g. "Dr. Smith" and "Dr. John Smith, DrSc." 
     * refer to each other. */ 
    public void addReferenceTo(AC other) {
        if(refersTo==null)
            refersTo=new LinkedList<AC>();
        refersTo.add(other);
    }

    /** Searches for matches of his attribute's value and context patterns, 
     * and sets respective evidence values to 1. Does not clear any evidence values. */
    public void applyPatternEvidence(Document doc) {
        // set value pattern evidence
        if(startToken.matchStarts!=null) {
            for(PatMatch pm: startToken.matchStarts) {
                // value pattern spanning exactly the size of this AC which counts as evidence?
                if(pm.pat.type==TokenPattern.PAT_VAL && pm.len==this.len && pm.pat.evidence!=null) {
                    // pattern match belongs to this attribute?
                    AttributeDef ad = pm.pat.getAttributeDef();
                    if(ad!=null && (ad==getAttribute() || getAttribute().isDescendantOf(ad))) {
                        this.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                    }
                }                    
            }
        }
        // set context pattern evidence (limited to linear token sequence so far)
        // left context:
        if(startToken.idx>=1) {
            TokenAnnot prev=doc.tokens[startToken.idx-1];
            if(prev.matchEnds!=null) {
                // patterns that end exactly at the token preceding this AC
                for(PatMatch pm: prev.matchEnds) {
                    // left context pattern that counts as evidence
                    if(pm.pat.type==TokenPattern.PAT_CTX_L && pm.pat.evidence!=null) {
                        // pattern match belongs to this attribute?
                        AttributeDef ad = pm.pat.getAttributeDef();
                        if(ad!=null && (ad==getAttribute() || getAttribute().isDescendantOf(ad))) {
                            this.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                        }
                    }                    
                }
            }
        }
        // right context:
        TokenAnnot endToken=this.getEndToken();
        if(endToken.idx<doc.tokens.length-1) {
            TokenAnnot next=doc.tokens[endToken.idx+1];
            if(next.matchStarts!=null) {
                // patterns that start exactly at the token following this AC
                for(PatMatch pm: next.matchStarts) {
                    // right context pattern that counts as evidence
                    if(pm.pat.type==TokenPattern.PAT_CTX_R && pm.pat.evidence!=null) {
                        // pattern match belongs to this attribute?
                        AttributeDef ad = pm.pat.getAttributeDef();
                        if(ad!=null && (ad==getAttribute() || getAttribute().isDescendantOf(ad))) {
                            this.setEvidenceValue(pm.pat.evidence.idx, (byte)1);
                        }
                    }                    
                }
            }
        }
        // left+right context:
        // FIXME: not handled yet.
    }
}

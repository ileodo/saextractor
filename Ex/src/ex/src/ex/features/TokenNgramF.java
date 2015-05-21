// $Id: TokenNgramF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uep.util.Logger;

import ex.model.AttributeDef;
import ex.model.Model;
import ex.model.ModelElement;
import ex.model.ModelException;
import ex.train.FeatCand;
import ex.util.pd.IntDistribution;

public class TokenNgramF extends PhraseF implements IntFeature {
    public int localId;
    public byte posType;
    public ModelElement elem;
    public int occCnt;
    public double pwmi;
    
    // this feature holds when ngram: 
    public static final byte NGR_BEFORE=1; // ends right before phrase
    public static final byte NGR_AFTER=2;  // starts right after phrase
    public static final byte NGR_EQUALS=3; // phrase is ngram
    
    public static final byte NGR_CONTAINED=4; // phrase contains ngram but is not PREFIX, SUFFIX, or EQUALS
    public static final byte NGR_PREFIX=5;    // phrase starts with ngram
    public static final byte NGR_SUFFIX=6;    // phrase ends with ngram

    public static final byte NGR_CONTAINS=7;    // ngram contains phrase but is not BEGINS_WITH, ENDS_WITH, or EQUALS
    public static final byte NGR_BEGINS_WITH=8; // ngram begins with phrase
    public static final byte NGR_ENDS_WITH=9;   // ngram ends with phrase
    
    public static final byte NGR_OVERLAPS_LEFT=10; // ngram's left boundary overlaps with phrase but is not CONTAINED
    public static final byte NGR_OVERLAPS_RIGHT=11; // ngram's right boundary overlaps with phrase but is not CONTAINED

    // first and last markers
    public static final byte NGR_POS_FIRST=NGR_BEFORE;
    public static final byte NGR_POS_LAST=NGR_OVERLAPS_RIGHT;

    public static String[] posNames={
        "NGR_UNKNOWN_POSITION",
        "NGR_BEFORE","NGR_AFTER","NGR_EQUALS",
        "NGR_CONTAINED", "NGR_PREFIX","NGR_SUFFIX",
        "NGR_CONTAINS", "NGR_BEGINS_WITH", "NGR_ENDS_WITH",
        "NGR_OVERLAPS_LEFT", "NGR_OVERLAPS_RIGHT"
    };
    static HashMap<String,Byte> posIds=new HashMap<String,Byte>();
    static {
        for(byte i=0;i<posNames.length;i++)
            posIds.put(posNames[i], i);
    }
    
    static HashMap<TokenNgramF,TokenNgramF> values=new HashMap<TokenNgramF,TokenNgramF>(128);
    
    /** This feature. */
    public TokenNgramF(int localId, ModelElement elem, byte posType, int occCnt, double pwmi) {
        super(FM.getFMInstance().getNextFeatureId(), 
              ((elem==null)? "BG": elem.getName())+"_"+posNames[posType]+"_"+occCnt+"_"+pwmi, 
              VAL_BOOLEAN);
        this.localId=localId;
        this.elem=elem;
        this.posType=posType;
        this.occCnt=occCnt;
        this.pwmi=pwmi;
    }
    
    /** Returns on or off. */
    public String toString(int val) {
        return boolValue2string(val);
    }

    /** Returns textual name of ngram's position relative to an attribute phrase. */
    //public static String posToString(byte pos) {
    //    return posNames[pos];
    //}
    public static String pos2string(byte pos) {
        return posNames[pos];
    }
    
    public static byte string2pos(String pos) {
        return posIds.get(pos);
    }

    public boolean equals(Object other) {
        if(this==other) {
            return true;
        }
        TokenNgramF o=(TokenNgramF)other;
        return this.elem==o.elem && this.posType==o.posType && 
               this.occCnt==o.occCnt && this.pwmi==o.pwmi;
    }
    
    public static java.text.NumberFormat nf=java.text.NumberFormat.getInstance(Locale.ENGLISH);
    static {
        nf.setMaximumFractionDigits(5);
        nf.setGroupingUsed(false);
    }
    
    public String toString() {
        return localId+"-"+((elem!=null)?elem.name:"bg")+"-"+pos2string(posType)+",n="+
            occCnt+",i="+nf.format(pwmi);
    }
    
    static final Pattern dataPat=Pattern.compile("\\s*(\\d+)\\-([a-zA-Z_]+)\\-([a-zA-Z_]+),n=(\\d+),i=([\\d\\.\\-]+)");
    
    /** parses ngram feature description, e.g. "303-name-NGR_AFTER,n=26967,i=3.39554" */
    public static TokenNgramF fromString(String str, Model model, boolean updateModel) {
        TokenNgramF lookup=new TokenNgramF(0, null, (byte)0, 0, 0.0);
        
        Matcher m=dataPat.matcher(str);
        if(m.find()) {
            // read numbers:
            try {
                lookup.localId = Integer.parseInt(m.group(1));
                lookup.occCnt = Integer.parseInt(m.group(4));
                lookup.pwmi = Double.parseDouble(m.group(5));
            }catch(NumberFormatException e) {
                throw new IllegalArgumentException("Error reading ngram feature data: "+e+": "+str);
            }
            // find model element:
            String elStr = m.group(2);
            if(model!=null) {
                lookup.elem=model.getElementByName(elStr);
                if(lookup.elem==null && updateModel && !elStr.equalsIgnoreCase("bg")) {
                    AttributeDef ad=new AttributeDef(elStr, AttributeDef.TYPE_NAME, model.standaloneAtts);
                    ad.cardDist=new IntDistribution(0, Integer.MAX_VALUE);
                    ad.minCard=0;
                    ad.maxCard=Integer.MAX_VALUE;
                    try {
                        model.addStandaloneAttribute(ad);
                    }catch (ModelException e) {
                        throw new IllegalArgumentException("Internal error: "+e);
                    }
                    lookup.elem=ad;
                }
            }
            if(lookup.elem==null && !elStr.equalsIgnoreCase("bg")) {
                Logger.LOGERR("Treating "+elStr+" as bg");
            }
            // set position:
            String posStr = m.group(3);
            lookup.setPosType(string2pos(posStr));
        }else {
            throw new IllegalArgumentException("Error reading ngram feature data: "+str);
        }
        TokenNgramF ret=values.get(lookup);
        if(ret==null) {
            values.put(lookup, lookup);
            ret=lookup;
        }
        return ret;
    }

    private void setPosType(byte posType) {
        this.posType=posType;
        this.name=posNames[posType];
    }

    public static TokenNgramF fromCandidate(FeatCand fc) {
        TokenNgramF lookup=new TokenNgramF(fc.id, fc.elem, fc.pos, fc.occCnt, fc.maxPWMI);
        TokenNgramF ret=values.get(lookup);
        if(ret==null) {
            values.put(lookup, lookup);
            ret=lookup;
        }
        return ret;
    }
}

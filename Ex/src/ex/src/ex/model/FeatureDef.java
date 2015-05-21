// $Id: FeatureDef.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import java.util.HashMap;
import java.util.Map;

import ex.train.NgramFeatureBook;

public class FeatureDef {
    public int type;
    public int minOcc; // ignore ngrams with lesser counts
    public int minLen; // ngram len
    public int maxLen;
    public double miLow; // ngram significance estimated by min & max MI of ngram and an attribute
    public double miHi;
    public int maxCnt; 
    public int ignoreFlags; // ignore case, lemma or nothing
    public int positionFlags;
    public int srcType; // source of ngrams: training documents or supplied ngram file
    public String bookFile;  // name of source file if given
    public NgramFeatureBook book; // the loaded feature book
    public String id; // based on model name, classifier name and line
    
    /** Use as feature each token n-gram satisfying criteria of this feature definition. */
    public static final int TYPE_NGRAM = 1;
    /** Use all patterns defined by the model's value and context sections as features. */
    public static final int TYPE_MODEL = 2;
    public static int type2id(String t) throws ModelException {
        int rc;
        t=t.trim().toLowerCase();
        if(t.equals("ngram"))
            rc=TYPE_NGRAM;
        else if(t.equals("model"))
            rc=TYPE_MODEL;
        else
            throw new ModelException("Unknown feature type: '"+t+"'");
        return rc;
    }
    
    /** For TYPE_NGRAM, specifies at which position the n-gram is used to produce a feature. */
    public static final int POS_BEF = 1;
    public static final int POS_AFT = 2;
    public static final int POS_EQU = 4;
    public static final int POS_PRE = 8;
    public static final int POS_SUF = 16;
    public static final int POS_SUB = 32;
    protected static Map<String,Integer> posMap=new HashMap<String,Integer>(8);
    static {
        posMap.put("before", POS_BEF); posMap.put("bef", POS_BEF);
        posMap.put("after",  POS_AFT); posMap.put("aft", POS_AFT);
        posMap.put("equals", POS_EQU); posMap.put("eq",  POS_EQU); posMap.put("",  POS_EQU);
        posMap.put("prefix", POS_PRE); posMap.put("pre", POS_PRE);
        posMap.put("suffix", POS_SUF); posMap.put("suf", POS_SUF);
        posMap.put("substring", POS_SUB); posMap.put("sub", POS_SUB); posMap.put("content", POS_SUB);
    }
    public static int pos2id(String p) throws ModelException {
        Integer i=posMap.get(p.trim().toLowerCase());
        if(i==null)
            throw new ModelException("Unknown feature position: '"+p+"'");
        return i;
    }
    
    /** For TYPE_NGRAM, specifies whether different casing / stemming produces different n-grams. */
    public static final int IGN_CASE = 1;
    public static final int IGN_LEMMA = 2;
    
    /** For TYPE_NGRAM, what is the source for token n-grams - training document collection or supplied file. */
    public static final int SRC_DOCS = 1;
    public static final int SRC_FILE = 2;
    
    public FeatureDef(String id) {
        this.id=id;
    }
    
    public FeatureDef(String id, String type, String pos, String len, String occ, String mi, String maxCntStr, 
                      String ignoreList, String srcSpec, String book) throws ModelException {
        this.id=id;
        // type
        this.type=type2id(type);
        // zero, one or more positions
        String[] posVals=pos.trim().toLowerCase().split("[\\s,;]+");
        this.positionFlags=0;
        for(int pidx=0; pidx<posVals.length; pidx++) {
            this.positionFlags |= pos2id(posVals[pidx]);
        }
        // length
        if(len.length()>0) {
            String[] pair=len.trim().split("[\\s;,\\-]+");
            try {
                if(pair.length==2) {
                    minLen=Integer.parseInt(pair[0]);
                    maxLen=Integer.parseInt(pair[1]);
                }else if(pair.length==1) {
                    minLen=maxLen=Integer.parseInt(pair[0]);
                }
            }catch (NumberFormatException e) {
                throw new ModelException("Error reading feature's length: "+e);
            }
        }else {
            minLen=maxLen=1;
        }
        // min. occurrence count threshold
        if(occ.length()>0) {
            minOcc=Integer.parseInt(occ);
        }else {
            minOcc=2;
        }
        
        // mutual information thresholds
        if(mi.length()>0) {
            String[] pair=mi.trim().split("[\\s;,\\-]+");
            try {
                if(pair.length==2) {
                    miLow=Double.parseDouble(pair[0]);
                    miHi=Double.parseDouble(pair[1]);
                }else if(pair.length==1) {
                    double n=Double.parseDouble(pair[0]);
                    if(n<1) {
                        miLow=n;
                        miHi=-1;
                    }else {
                        miHi=n;
                        miLow=-1;
                    }
                }
            }catch (NumberFormatException e) {
                throw new ModelException("Error reading feature's MI spec: "+e);
            }
        }else {
            miHi=miLow=-1;
        }
        // max count of distinct ngrams to serve as features
        if(maxCntStr.length()>0) {
            try {
                maxCnt=Integer.parseInt(maxCntStr);
            }catch (NumberFormatException e) {
                throw new ModelException("Error reading feature's maxCnt: "+e);
            }
        }else {
            maxCnt=-1;
        }
        // ignore
        if(ignoreList.length()>0) {
            ignoreList=ignoreList.toLowerCase();
            if(ignoreList.indexOf("case")!=-1)
                ignoreFlags |= FeatureDef.IGN_CASE;
            if(ignoreList.indexOf("lemma")!=-1)
                ignoreFlags |= FeatureDef.IGN_LEMMA;
        }else {
            ignoreFlags=0;
        }
        // n-gram source (where to get ngrams)
        if(srcSpec.length()>0) {
            srcSpec=srcSpec.trim().toLowerCase();
            if(srcSpec.equals("documents") || srcSpec.equals("documents")) {
                srcType=SRC_DOCS;
            }else {
                srcType=SRC_FILE;
            }
        }else {
            srcType=SRC_DOCS;
        }
        this.bookFile=book;
    }
}

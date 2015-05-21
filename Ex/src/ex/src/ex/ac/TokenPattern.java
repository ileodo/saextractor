// $Id: TokenPattern.java 1697 2008-10-19 23:06:31Z labsky $
package ex.ac;

/** 
 *  Represents a pattern (user defined or induced) 
 *  over tokens that appear in a token sequence. 
 *  Pattern can match (possibly overlapping) 
 *  token subsequences in the examined sequence.
 *  For example, "LCD <MANUFACTURER> <ALPHANUM>+"
 *  where <MANUFACTURER> matches any phrase from a list of phrases,
 *  and <ALPHANUM> matches any phrase containing at least one digit and one letter.
 *  @author Martin Labsky labsky@vse.cz
 */

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import uep.util.*;

import ex.reader.Document;
import ex.reader.TokenAnnot;
import ex.util.pr.*;
import ex.features.TokenCapF;
import ex.model.Axiom;
import ex.model.ModelElement;
import ex.model.AttributeDef;
import ex.model.ClassDef;

public class TokenPattern {
    public static final short PAT_GEN=0; // generic
    public static final short PAT_VAL=1; // for attribute's value
    public static final short PAT_CTX_LR=2; // attribute's left and right context
    public static final short PAT_CTX_L=3;  // attribute's left context
    public static final short PAT_CTX_R=4;  // attribute's right context
    
    public static final short PAT_CLS=5; // unknown type of class pattern
    public static final short PAT_CLS_VAL=6; // ordering and context of attributes within a class, does not contain $
    public static final short PAT_CLS_VAL_LR=7; // dtto + starts and ends with a ^
    public static final short PAT_CLS_VAL_L=8; // dtto + starts with a ^
    public static final short PAT_CLS_VAL_R=9; // dtto + ends with a ^
    public static final short PAT_CLS_CTX_LR=10; // contains $ in the middle
    public static final short PAT_CLS_CTX_L=11; // contains $ on left side
    public static final short PAT_CLS_CTX_R=12; // contains $ on left side
    
    public static final short PAT_LAST_TYPE=PAT_CLS_CTX_R; // for Annotable ro start with other types
    
    // possible filter flags to restrict pattern usage:
    public static final int PATTERN_ALL = 0;
    public static final int PATTERN_WITH_ACS = 1;
    public static final int PATTERN_WITHOUT_ACS = 2;
    public static final int PATTERN_WITH_LABELS = 4;
    public static final int PATTERN_WITHOUT_LABELS = 8;

    public static final int FC_ALL = 0;
    public static final int FC_START = 1;
    public static final int FC_END = 2;
    
    // matching flags
    public static final int FLAG_LINEAR = 1;
    public static final int FLAG_GREEDY = 2;
    public static final int FLAG_AND_GROUP_MEMBER = 4;
    public static final int FLAG_OR_GROUP_MEMBER = 8;
    
    public String source;
    public String id;
    public FA fa; // pattern compiled into FA
    public int minLen; // in tokens
    public int maxLen;
    //public boolean ignoreCase;
    //public boolean ignoreLemma;
    public int[] forceCaseValues;
    public int forceCaseArea;
    // AttributeDef/ClassDef binding
    public ModelElement modelElement;
    public short type; // PAT_GEN, PAT_VAL, PAT_CTX_LR|L|R, PAT_CLS
    public int contentType; // flags indicating whether this pattern contains AC and label references
    public PR_Evidence evidence;
    public boolean useAsFeature;
    
    public int matchFlags;
    public static final int MATCH_IGNORE_CASE =1;
    public static final int MATCH_IGNORE_LEMMA=2;
    public static final int MATCH_IGNORE_ACCENT=256;
    public byte condType;
    public List<ModelElement> usedElems;
    public Set<String> usedElemNames;
    public short logLevel;
    /** various matching flags */
    public int flags;

    public TokenPattern(String id, String src) {
        this(id, src, PAT_GEN, null, 0, null, FC_ALL, Axiom.AXIOM_COND_NONE, 0, Integer.MAX_VALUE);
    }

    public TokenPattern(String id, String src, short patType, ModelElement modelElement,
            int matchMode, int[] caseValues, int fcArea) {
        this(id, src, patType, modelElement, matchMode, caseValues, fcArea, Axiom.AXIOM_COND_NONE, 0, Integer.MAX_VALUE);
    }

    public TokenPattern(String id, String src, short patType, ModelElement modelElement,
            int matchMode, int[] caseValues, int fcArea, byte condType, int minLen, int maxLen) {
        this.id=id;
        source=trimPattern(src); // trim leading and trailing whitespace
        if(source.length()==0)
            Logger.LOG(Logger.ERR,"Empty pattern '"+src+"'");
        this.type=patType;
        this.modelElement=modelElement;
        this.matchFlags=matchMode;
        this.condType=condType;
        this.forceCaseValues=caseValues;
        this.forceCaseArea=fcArea;
        this.minLen=minLen>=0? minLen: 0;
        this.maxLen=maxLen>=0? maxLen: Integer.MAX_VALUE;
        contentType=0;
        evidence=null;
        fa=null;
        useAsFeature=true;
        logLevel=0;
        usedElems=null;
        usedElemNames=null;
        flags=0;
    }
    
    // copy ctor
    public TokenPattern(TokenPattern orig) {
        id=orig.id;
        source=orig.source;
        minLen=orig.minLen;
        maxLen=orig.maxLen;
        matchFlags=orig.matchFlags;
        forceCaseValues=orig.forceCaseValues;
        modelElement=orig.modelElement;
        type=orig.type;
        contentType=orig.contentType;
        fa=orig.fa;
        useAsFeature=orig.useAsFeature;
        condType=orig.condType;
        usedElems=orig.usedElems;
        usedElemNames=null;
        evidence=null;
        flags=orig.flags;
    }

    public String toString() {
        return ((modelElement!=null)?modelElement.getName():"global")+"."+type2string(type)+"."+id+": "+source
         +(((matchFlags & MATCH_IGNORE_CASE)>0)?", ignore=case":"")
         +(((matchFlags & MATCH_IGNORE_LEMMA)>0)?", ignore=lemma":"")
         +(((matchFlags & MATCH_IGNORE_ACCENT)>0)?", ignore=accent":"")
         +((forceCaseValues!=null)?", casesCnt=["+forceCaseValues.length+"]":"");
    }

    public String trimPattern(String s) {
        int len=s.length(),i=0,j=len;
        while(i<len && Character.isWhitespace(s.charAt(i)))
            i++;
        while(j>0 && Character.isWhitespace(s.charAt(j-1)))
            j--;
        return (i==0 && j==len)? s: s.substring(i,j);
    }
    
    public AttributeDef getAttributeDef() {
        if(modelElement instanceof AttributeDef)
            return (AttributeDef) modelElement;
        return null;
    }

    public ClassDef getClassDef() {
        if(modelElement instanceof ClassDef)
            return (ClassDef) modelElement;
        return null;
    }

    public static void main(String args[]) {
        Options o=Options.getOptionsInstance();
        if ((args.length >= 2) && args[0].toLowerCase().equals("-cfg")) {
            try { o.load(new FileInputStream(args[1])); }
            catch(Exception ex) { }
        }
        o.add(0, args);

        String s;
        PatComp comp=new PatComp();
        int i=0;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in, "utf-8"));
            while((s=br.readLine())!=null) {
                TokenPattern pat=new TokenPattern("pat_"+(++i),s);
                try {
                    comp.compile(pat,null,null,null,null);
                }catch(TokenPatternSyntaxException ex) {
                    Logger.LOG(Logger.ERR,"Error compiling pattern "+pat.source+": "+ex);
                    ex.printStackTrace();
                }
            }
        }catch(IOException ex) {
            Logger.LOGERR("Error reading from stdin: "+ex.toString());
        }
    }
    
    public static String type2string(int type) {
        String s="UNK";
        switch(type) {
        case PAT_GEN: s="GEN"; break;
        case PAT_VAL: s="VAL"; break;
        case PAT_CTX_LR: s="CTX_LR"; break;
        case PAT_CTX_L: s="CTX_L"; break;
        case PAT_CTX_R: s="CTX_R"; break;
        case PAT_CLS: s="CLS"; break;
        case PAT_CLS_VAL: s="CLS_VAL"; break;
        case PAT_CLS_VAL_LR: s="CLS_VAL_LR"; break;
        case PAT_CLS_VAL_L: s="CLS_VAL_L"; break;
        case PAT_CLS_VAL_R: s="CLS_VAL_R"; break;
        case PAT_CLS_CTX_LR: s="CLS_CTX_LR"; break;
        case PAT_CLS_CTX_L: s="CLS_CTX_L"; break;
        case PAT_CLS_CTX_R: s="CLS_CTX_R"; break;
        }
        return s;
    }

    public boolean isMatchValid(TokenAnnot[] tokens, int startIdx, TNode finalNode) {
        int len=finalNode.pathLen;
        if(len==0) {
            if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.INF,"Empty match not allowed");
            return false;
        }else if(len<0) {
            if(PatMatcher.log.IFLG(Logger.WRN)) PatMatcher.log.LG(Logger.WRN,"Illegal match len="+len+"of "+this+" at "+Document.toStringDbg(tokens, startIdx, Math.min(3, tokens.length-startIdx), " "));
            return false;
        }
        // check extra constraints
        if(forceCaseValues!=null) {
            for(int i=0;i<len;i++) {
                if(forceCaseArea==0) {
                    ;
                }else if(i==0 && (forceCaseArea & FC_START)!=0) {
                    ;
                }else if((forceCaseArea & FC_END)!=0) {
                    i=len-1;
                }
                TokenAnnot t=tokens[startIdx+i];
                int caseVal=t.ti.intVals.get(TokenCapF.getSingleton().id);
                boolean ok=false;
                for(int j=0;j<forceCaseValues.length;j++) {
                    if(forceCaseValues[j]==caseVal)
                        ok=true;
                }
                if(!ok) {
                    if(PatMatcher.log.IFLG(Logger.INF)) PatMatcher.log.LG(Logger.INF,
                      "Match not allowed due to case constraints: "+Document.toString(tokens, startIdx, len, " "));
                    return false;
                }
            }
        }
        return true;
    }
    
    // filter specifies which patterns to use (is set to 0, no filtering is done) 
    static boolean usePattern(TokenPattern tp, int tokenFilter) {
        boolean rc=true;
        if((((tokenFilter & TokenPattern.PATTERN_WITH_ACS)!=0) && ((tp.contentType & TokenPattern.PATTERN_WITH_ACS)==0)) ||
         (((tokenFilter & TokenPattern.PATTERN_WITHOUT_ACS)!=0) && ((tp.contentType & TokenPattern.PATTERN_WITH_ACS)!=0)) ||
         (((tokenFilter & TokenPattern.PATTERN_WITH_LABELS)!=0) && ((tp.contentType & TokenPattern.PATTERN_WITH_LABELS)==0)) ||
         (((tokenFilter & TokenPattern.PATTERN_WITHOUT_LABELS)!=0) && ((tp.contentType & TokenPattern.PATTERN_WITH_LABELS)!=0)))
            rc=false;
        return rc;
    }

    public void addUsedElement(String elemName) {
        contentType |= PATTERN_WITH_ACS;
        if(usedElemNames==null) {
            usedElemNames=new TreeSet<String>();
        }
        usedElemNames.add(elemName);
    }
    
    public void addUsedElement(ModelElement elem) {
        contentType |= PATTERN_WITH_ACS;
        if(usedElems==null) {
            usedElems=new LinkedList<ModelElement>();
        }else if(usedElems.contains(elem)){
            return;
        }
        usedElems.add(elem);
    }
        
    public List<ModelElement> getUsedElements() {
        return usedElems;
    }
    
    public void addUsedElements(TokenPattern subPat) {
        if(subPat.usedElemNames!=null) {
            if(usedElemNames==null) {
                usedElemNames=subPat.usedElemNames;
            }else {
                usedElemNames.addAll(subPat.usedElemNames);
            }
        }
        if(subPat.usedElems!=null) {
            if(usedElems==null) {
                usedElems=subPat.usedElems;
            }else {
                for(ModelElement elem: subPat.usedElems) {
                    if(!usedElems.contains(elem)) {
                        usedElems.add(elem);                        
                    }
                }
            }
        }
    }

    protected void resolveReferences(ClassDef cls) throws TokenPatternSyntaxException {
        if(usedElemNames!=null) {
            for(String attName: usedElemNames) {
                ModelElement melem=cls.attributes.get(attName);
                if(melem==null) {
                    melem=cls.model.getElementByName(attName);
                }
                if(melem==null) {
                    throw new TokenPatternSyntaxException("Model element "+attName+" not found!");
                }else {
                    addUsedElement(melem);
                }
            }
        }
    }
}

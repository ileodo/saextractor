// $Id: DefaultPattern.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import ex.util.pr.PR_Evidence;

public class DefaultPattern {
    public PR_Evidence evidence;
    
    /* default evidence: the idea is to only apply each default evidence in case it is not fulfilled;
       in that case its recall will be used to suppress the AC's / IC's probability */
    
    // value crosses, not contains, one inline element
    public static final int EVIDENCE_NOCROSS_INLINE_TAG=0;
    // value crosses, not contains, one block element
    public static final int EVIDENCE_NOCROSS_BLOCK_TAG=1;
    // value fits exactly into parent tag
    public static final int EVIDENCE_FIT_IN_TAG=2;
    // each token of att value has the same parent
    public static final int EVIDENCE_HAS_ONE_PARENT=3;

    public static PR_Evidence[] evidenceTemplates=new PR_Evidence[] {
      new PR_Evidence("NO_CROSSED_INLINE_TAGS", -1.0, 0.5,  (byte)-1, EVIDENCE_NOCROSS_INLINE_TAG),
      new PR_Evidence("NO_CROSSED_BLOCK_TAGS",  -1.0, 0.75, (byte)-1, EVIDENCE_NOCROSS_BLOCK_TAG),
      new PR_Evidence("FITS_IN_PARENT",         -1.0, 0.05, (byte)-1, EVIDENCE_FIT_IN_TAG),
      new PR_Evidence("HAS_ONE_PARENT",         -1.0, 0.05, (byte)-1, EVIDENCE_HAS_ONE_PARENT),
    };
    
    public DefaultPattern(PR_Evidence evidence) {
        this.evidence=evidence;
    }
    
    int getType() {
        return evidence.idx;
    }
    
    public String toString() {
        return "DefPat "+evidence;
    }
        
    public static DefaultPattern create(String content, double prec, double recall) {
        PR_Evidence ev=null;
        for(int i=0;i<evidenceTemplates.length;i++) {
            if(content.equals(evidenceTemplates[i].name)) {
                ev=new PR_Evidence(evidenceTemplates[i]);
                break;
            }
        }
        if(ev==null) {
            return null;
        }
        if(prec!=-1.0)
            ev.prec=prec;
        if(recall!=-1.0)
            ev.recall=recall;
        return new DefaultPattern(ev);
    }

    public static void addAllPatterns(ModelElement def) {
        for(int i=0;i<evidenceTemplates.length;i++) {
            PR_Evidence ev=new PR_Evidence(evidenceTemplates[i]);
            DefaultPattern dp=new DefaultPattern(ev);
            def.addPattern(dp);
        }
    }
}

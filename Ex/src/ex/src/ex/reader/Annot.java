// $Id: Annot.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import ex.features.TagNameF;
import ex.features.TagTypeF;

public abstract class Annot {
    public static final int ANNOT_TOKEN     = 1;
    public static final int ANNOT_TOKEN_TAG = 2;
    public static final int ANNOT_TAG       = 3;
    public static final int ANNOT_SEM       = 4;

    /** annotation type ANNOT_TOKEN, ANNOT_TOKEN_TAG, ANNOT_TAG or ANNOT_SEM */
    public int annotType;
    /** Specific type; 
        for TokenAnnots: the type assigned by tokenizer, 
        for (Token)TagAnnots: their tag name, 
        for SemAnnots: the attribute's id */
    public int type;
    /** for TokenAnnots: character position in Document content,
        for TagAnnots: startIdx points to the start char of the start tag, endIdx points to the end char of the end tag 
        for SemAnnots: startIdx and endIdx store _token_ indices of the first and the last token in the label */
    public int startIdx;
    public int endIdx;
    /** reference to the containing Annot:
        for TokenAnnot or TokenTagAnnot: a leaf in Document's tree of TagAnnots,
        for TokenAnnots within tag attributes: the containing TagAnnot or TokenTagAnnot,
        for TagAnnots: a non-leaf node in Document's tree of TagAnnots
        for SemAnnots: the parent SemAnnot (which either denotes an instance or aggregated attribute) */
    public Annot parent;
    /** idx in parent's array of Annots (to access siblings */
    public int parentIdx;

    public Annot(int t, int start, int end, Annot par, int parIdx) {
        type=t;
        startIdx=start;
        endIdx=end;
        parent=par;
        parentIdx=parIdx;
    }
    
    public void clear() {
        parent=null;
    }
    
    /** Returns content length in characters (for TokenAnnots and TagAnnots) or in tokens (for SemAnnots). 
     *  To get token length of tags, use TagAnnot.tokenCnt(true) instead. */
    public int getLength() {
        return endIdx-startIdx+1;
    }
    
    public String getDomPath(int maxLen) {
        StringBuffer buff=new StringBuffer(128);
        Annot anc=parent;
        int len=0;
        while(anc!=null) {
            if(len==maxLen)
                break;
            if(len>0)
                buff.append(" ");
            TagAnnot tag=(TagAnnot) anc;
            buff.append(TagNameF.getSingleton().toString(tag.type));
            buff.append("{");
            buff.append(TagTypeF.getSingleton().getValueToString(tag.type));
            buff.append("}");
            len++;
            anc=anc.parent;
        }
        return buff.toString();
    }
}

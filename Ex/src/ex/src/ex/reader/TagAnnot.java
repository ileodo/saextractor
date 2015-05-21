// $Id: TagAnnot.java 1661 2008-09-20 10:35:01Z labsky $
package ex.reader;

import java.util.*;

import uep.util.Logger;
import ex.features.TagNameF;
import ex.features.TagTypeF;
import ex.model.AttributeDef;
import org.cyberneko.html.HTMLElements;

/** This represents tags other than TokenTags.
 These are not considered tokens, but contribute to token features and, 
 when parsing, to NT candidate features.
 */
public class TagAnnot extends Annot {
    // names, values and TokenAnnot[] segmentation of attributes
    public HtmlAttribute[] attributes;
    /** inner tag character-based indices (end of start tag, start of end tag) */
    public int startIdxInner;
    public int endIdxInner;
    /** child annots (some of these may be TagAnnot, some TokenAnnot) */
    public Annot[] childNodes;
    public TagAnnot altParent;
    public DOMAlternative[] alternatives; // alternatives for parsing this parent, now 2 supported for elements containing TRs
    /** the first and the last tokens contained within this tag, or null if tag contains no tokens. */
    public TokenAnnot firstToken;
    public TokenAnnot lastToken;
//    protected static final TokenAnnot UNKNOWN_TOKEN=new TokenAnnot(-1,-1,-1,null,-1,-1,null);
    
    public TagAnnot(int tagId, int start, int startInner, int endInner, int end, Annot par, int parIdx) {
        super(tagId, start, end, par, parIdx);
        annotType=ANNOT_TAG;
        startIdxInner=startInner;
        endIdxInner=endInner;
        attributes=null;
        childNodes=null;
        altParent=null;
        alternatives=null;
        firstToken=null;//UNKNOWN_TOKEN;
        lastToken=null;//UNKNOWN_TOKEN;
    }

    public void clear() {
        if(childNodes!=null) {
            Annot[] tmp=childNodes;
            childNodes=null;
            for(Annot ch: tmp)
                ch.clear();
        }
        attributes=null;
        if(altParent!=null) {
            TagAnnot tmp=altParent;
            altParent=null;
            tmp.clear();
        }
        alternatives=null;
        firstToken=null;
        lastToken=null;
        super.clear();
    }
    
    public int getSignature() {
        return type; // represents this type of tag in DomPath
    }

    public int getTagType() {
        return TagTypeF.getSingleton().getValue(type);
    }

    public boolean hasDescendant(Annot ann) {
        if(childNodes==null)
            return false;
        switch(type) {
        case TagNameF.HTML_COLUMN:
            for(int i=0;i<childNodes.length;i++) {
                if(((TagAnnot)childNodes[i]).hasDescendant(ann))
                    return true;
            }
            return false;
        }
        return (startIdx<=ann.startIdx) && (endIdx>=ann.endIdx);
    }

    public int parentBlockCnt() {
        if(parent==null)
            return 0;
        switch(type) {
        case HTMLElements.TD: // two possible parents - TR and (artificial) table column
            return 2;
        }
        return 1;
    }

    public boolean hasAncestor(TagAnnot a) {
        TagAnnot p=(TagAnnot) this.parent;
        while(p!=null) {
            if(p==a)
                return true;
            p=(TagAnnot) p.parent;
        }
        return false;
    }

//    protected int findTokenCnt(boolean recursive) {
//        if(childNodes==null)
//            return 0;
//        int cnt=0;
//        for(int i=0;i<childNodes.length;i++) {
//            switch(childNodes[i].annotType) {
//            case ANNOT_TAG:
//                if(recursive)
//                    cnt+=((TagAnnot) childNodes[i]).findTokenCnt(recursive);
//                break;
//            case ANNOT_TOKEN:
//                cnt++;
//                break;
//            }
//        }
//        return cnt;
//    }
    
    public int tokenCnt(boolean recursive) {
        return recursive? 
                ((firstToken!=null)? (lastToken.idx-firstToken.idx+1): 0):
                ((childNodes!=null)? childNodes.length: 0);
//        if(!recursive)
//            return findTokenCnt(recursive);
//        if(firstToken==UNKNOWN_TOKEN)
//            firstToken=findFirstToken(recursive);
//        if(firstToken==null)
//            return 0;
//        if(lastToken==UNKNOWN_TOKEN)
//            lastToken=findLastToken(recursive);
//        return lastToken.idx-firstToken.idx+1;
    }

//    protected TokenAnnot findFirstToken(boolean recursive) {
//        if(childNodes==null)
//            return null;
//        for(int i=0;i<childNodes.length;i++) {
//            switch(childNodes[i].annotType) {
//            case ANNOT_TAG:
//                if(recursive) {
//                    TokenAnnot ta=((TagAnnot) childNodes[i]).getFirstToken(recursive);
//                    if(ta!=null)
//                        return ta;
//                }
//                break;
//            case ANNOT_TOKEN:
//                return (TokenAnnot) childNodes[i]; 
//            }
//        }
//        return null;
//    }
//    
//    public TokenAnnot getFirstToken(boolean recursive) {
//        if(!recursive)
//            return findFirstToken(recursive); // only cache the recursive version
//        if(firstToken==UNKNOWN_TOKEN)
//            firstToken=findFirstToken(recursive);
//        return firstToken;
//    }
//
//    protected TokenAnnot findLastToken(boolean recursive) {
//        if(childNodes==null)
//            return null;
//        for(int i=childNodes.length-1;i>=0;i--) {
//            switch(childNodes[i].annotType) {
//            case ANNOT_TAG:
//                if(recursive) {
//                    TokenAnnot ta=((TagAnnot) childNodes[i]).getLastToken(recursive);
//                    if(ta!=null)
//                        return ta;
//                }
//                break;
//            case ANNOT_TOKEN:
//                return (TokenAnnot) childNodes[i];
//            }
//        }
//        return null;
//    }
//    
//    public TokenAnnot getLastToken(boolean recursive) {
//        if(!recursive)
//            return findLastToken(recursive); // only cache the recursive version
//        if(lastToken==UNKNOWN_TOKEN)
//            lastToken=findLastToken(recursive);
//        return lastToken;
//    }
    
    public SemAnnot getFirstLabel(AttributeDef attDef, boolean recursive) {
        if(childNodes==null)
            return null;
        for(int i=0;i<childNodes.length;i++) {
            switch(childNodes[i].annotType) {
            case ANNOT_SEM:
                SemAnnot sa=(SemAnnot) childNodes[i];
                if(sa.type==SemAnnot.TYPE_WRAPPER && sa.data==attDef) {
                    return ((SemAnnot) childNodes[i]);
                }
                break;
            case ANNOT_TAG:
                if(recursive) {
                    return ((TagAnnot) childNodes[i]).getFirstLabel(attDef, recursive);
                }
                break;
            case ANNOT_TOKEN:
                continue;
            }
        }
        return null;
    }

    /* allow more than 1 parent */
    public TagAnnot getParentBlock(int idx) {
        switch(type) {
        case HTMLElements.TD: // two possible parents - TR and (artificial) table column
        case HTMLElements.TH:
            if(idx==0)
                break;
            if(idx!=1)
                return null;
            return (altParent!=null)? altParent: getColumnTag();
        }
        return (TagAnnot) parent;
    }

    public TagAnnot getFirstAncestorWithType(int type) {
        TagAnnot tga=this;
        while(tga!=null && tga.type!=type) {
            tga=(TagAnnot) tga.parent;
        }
        return tga;
    }

    public TagAnnot getColumnTag() {
        int colIdx=parentIdx;
        TagAnnot tr=(TagAnnot) parent;
        if(tr==null||tr.type!=HTMLElements.TR||tr.parent==null) {
            Logger.LOG(Logger.WRN,"Table cell out of table or table row");
            return null;
        }
        switch(tr.parent.type) {
        case HTMLElements.TABLE:
        case HTMLElements.TBODY:
        case HTMLElements.THEAD:
        case HTMLElements.TFOOT:
            break;
        default:
            Logger.LOG(Logger.WRN,"Unexpected element "+tr.parent.type+" encapsulating TR");
        }
        // create artificial column tag
        TagAnnot container=(TagAnnot) tr.parent;
        TagAnnot column=new TagAnnot(TagNameF.HTML_COLUMN, -1, -1, -1, -1, container, -1);
        // set children and idxs
        ArrayList<TagAnnot> columnCells=new ArrayList<TagAnnot>(container.childNodes.length);
        int i;
        for(i=0;i<container.childNodes.length;i++) {
            if(container.childNodes[i].annotType!=ANNOT_TAG) 
                continue;
            TagAnnot tr1=(TagAnnot) container.childNodes[i];
            if(tr1.type!=HTMLElements.TR || tr1.childNodes==null || colIdx>=tr1.childNodes.length)
                continue;
            TagAnnot td=(TagAnnot) tr1.childNodes[colIdx]; // can be TD, TH... one of these instances is this
            columnCells.add(td);
        }
        int cnt=columnCells.size();

        Annot[] children=new Annot[cnt];
        for(i=0;i<cnt;i++) {
            children[i]=(TagAnnot) columnCells.get(i);
            ((TagAnnot)children[i]).altParent=column;
        }
        column.childNodes=children;
        column.startIdx=children[0].startIdx;
        column.endIdx=children[children.length-1].endIdx;
        return column;
    }

    public DOMAlternative getDOMAlternative() {
        int key;
        switch(type) {
        case HTMLElements.TR:
            key=0;
            break;
        case TagNameF.HTML_COLUMN:
            key=1;
            break;
        default:
            return null;
        }
        if(parent==null)
            return null;
        TagAnnot container=(TagAnnot) parent;
        if(container.alternatives==null) {
            container.alternatives=new DOMAlternative[2];
            for(int i=0;i<2;i++) {
                container.alternatives[i]=new DOMAlternative(container,i);
            }
        }
        return container.alternatives[key];
    }

    public String toString() {
        return "<"+TagNameF.getSingleton().toString(type)+">";
    }

    public int validate(int depth, StringBuffer dump) {
        if(dump!=null) {
            for(int i=0;i<depth;i++)
                dump.append(" ");
            dump.append(toString());
            dump.append("\n");
        }
        if(childNodes==null)
            return 0;
        int err=0;
        for(int k=0;k<childNodes.length;k++) {
            // check parentIdx
            if(childNodes[k].parentIdx!=k) {
                Logger.LOG(Logger.ERR,"childNodes["+k+"].parentIdx="+childNodes[k].parentIdx+" should be "+k+
                        " this="+toString()+" child="+childNodes[k].toString());
                err++;
            }
            // check parent<->childNodes correspondence
            if(childNodes[k].parent!=this) {
                Logger.LOG(Logger.ERR,"childNodes["+k+"].parent!=this, "+
                        " this="+toString()+" child="+childNodes[k].toString());
                err++;
            }
            if(childNodes[k].annotType==ANNOT_TAG)
                err+=((TagAnnot)childNodes[k]).validate(depth+1, dump);
        }
        return err;
    }

    public void appendChild(Annot a) {
        if(childNodes==null) {
            childNodes=new Annot[] {a};
        }else {
            Annot[] tmp=childNodes;
            childNodes=new Annot[tmp.length+1];
            System.arraycopy(tmp,0,childNodes,0,tmp.length);
            childNodes[childNodes.length-1]=a;
        }
    }

    public int getChildTags(boolean recursive, List<TagAnnot> tagList, int tagId) {
        int cnt=0;
        for(int i=0;childNodes!=null && i<childNodes.length;i++) {
            if(childNodes[i].annotType==Annot.ANNOT_TAG) {
                TagAnnot ta=(TagAnnot) childNodes[i];
                if(tagId==-1 || ta.type==tagId) {
                    if(tagList==null) {
                        return 1;
                    }else {
                        tagList.add(ta);
                        cnt++;
                    }
                }
                if(recursive)
                    cnt+=ta.getChildTags(recursive, tagList, tagId);
            }
        }
        return cnt;
    }
    
    public boolean isInline() {
        return startIdx!=-1 && startIdxInner==endIdx;
    }
}

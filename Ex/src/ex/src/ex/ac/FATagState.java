// $Id: FATagState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * State accepting 1 start, end or inline tag
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.List;

import ex.reader.TagAnnot;
import ex.reader.TokenAnnot;
// import ex.reader.Document;
import uep.util.Logger;

public class FATagState extends FAState {
    public static final int START_TAG=1;
    public static final int END_TAG=2;
    public static final int INLINE_TAG=3;
    int tagType;
    int tagId;
    int tagForm;
    
    public FATagState(int tagId, int tagType, int tagForm, Object d) {
        super(ST_TAG,d);
        this.tagType=tagType;
        this.tagId=tagId;
        this.tagForm=tagForm;
    }

    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        // PatMatcher.log.LG(Logger.TRC,startIdx+"-"+prev.pathLen);
        if(startIdx+prev.pathLen>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"TagState partial match continues beyond doc");
            return 0;
        }
        int idx=startIdx+prev.pathLen;
        TokenAnnot ta=tokens[idx];
        // a start tag must start just before this token, an end tag must end just before this token,
        // a body of a tag is line start tag but spans the whole tag
        int matchCnt=0;
        switch(tagForm) {
        case START_TAG:
        case INLINE_TAG:
            // tag=Document.getParentBlock(tokens, ta, ta, tagId, tagType, 0, -1);
            if(ta.tagStarts==null)
                break;
            for(TagAnnot tag: ta.tagStarts) {
                if((tagId==-1 || tag.type==tagId) && (tagType==-1 || tag.getTagType()==tagType)) {
                    matchCnt++;
                    if(tagForm==START_TAG) {
                        newNodes.add(PatMatcher.newNode(this, prev, 0));
                        break;
                    }else { // INLINE_TAG
                        newNodes.add(PatMatcher.newNode(this, prev, tag.tokenCnt(true)));
                    }
                }
            }
            break;
        case END_TAG:
            if(ta.idx>0) {
                TokenAnnot leftTok=tokens[ta.idx-1];
                // tag=Document.getParentBlock(tokens, leftTok, leftTok, tagId, tagType, -1, Document.LAST_IDX);
                if(leftTok.tagEnds==null)
                    break;
                for(TagAnnot tag: leftTok.tagEnds) {
                    if((tagId==-1 || tag.type==tagId) && (tagType==-1 || tag.getTagType()==tagType)) {
                        matchCnt=1;
                        newNodes.add(PatMatcher.newNode(this, prev, 0));
                        break;
                    }
                }
            }
            break;
//      case INLINE_TAG:
//          PatMatcher.log.LG(Logger.ERR,"Inline tag matching not yet supported.");
//          break;
        }
//      if(tag==null)
//          return 0;
        // newNodes.add(new TNode(this,prev,0));
        if(matchCnt==0 && neg) {
            newNodes.add(PatMatcher.newNode(this, prev, 0));
            matchCnt=1;
        }
        if(matchCnt>0 && PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"TagState form="+tagForm+" matched "+matchCnt+"x before "+ta);
        return matchCnt;
    }
}

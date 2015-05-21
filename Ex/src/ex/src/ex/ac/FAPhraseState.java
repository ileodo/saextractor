// $Id: FAPhraseState.java 1661 2008-09-20 10:35:01Z labsky $
package ex.ac;

/** 
 * State matching a phrase of a given label.
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

import ex.features.AnnotationF;
import ex.features.TagNameF;
import ex.features.TagTypeF;
import ex.model.AttributeDef;
import ex.model.ClassDef;
import ex.model.ModelElement;
import ex.reader.Document;
import ex.reader.SemAnnot;
import ex.reader.TagAnnot;
import ex.reader.TokenAnnot;
import uep.util.Logger;

/** Matches a phrase of the specified label. */
public class FAPhraseState extends FAState {
    public int[] labels; // labels (AnnotationF.labelId) which need to hold for a phrase to be matched
    public int[] tags; // tag names or types
    public Object[] acs; // String names of attributes before resolving, AttributeDefs after
// not used so far:
//    public double[] minConfs; // minimal confidence for each label
    
    public byte combType; // and | or
    public static byte LAB_CMB_AND=1;
    public static byte LAB_CMB_OR=2;
    protected static final String[] combTypes={null,"&","|"};
    
    public byte labType;
    public static final byte LAB_START=1;
    public static final byte LAB_END=2;
    public static final byte LAB_BODY=3;
    protected static final String[] labTypes={null,"start","end","body"};

    public static final int TAGNAME_BASE = -1000;
    public static final int TAGTYPE_BASE = -2000;
    
    public FAPhraseState(List<Integer> labs, List<Integer> tags, List<String> acNames, double[] minConfs, byte combType, byte labType) {
        super(ST_PHRASE, null);
        this.labels=new int[labs.size()];
        for(int i=0;i<this.labels.length;i++)
            this.labels[i]=labs.get(i);
        this.tags=new int[tags.size()];
        for(int i=0;i<this.tags.length;i++)
            this.tags[i]=tags.get(i);
        this.acs=new Object[acNames.size()];
        for(int i=0;i<this.acs.length;i++)
            this.acs[i]=acNames.get(i);
        this.combType=combType;
        this.labType=labType;
        if(minConfs!=null && minConfs.length!=labels.length)
            throw new IllegalArgumentException("Label count!=confidence count");
        if(Logger.IFLOG(Logger.TRC))
            toString(); // populates this.data so that it is available for graph plots
//        this.minConfs=minConfs;
    }

    public void resolveReferences(ClassDef cls) throws TokenPatternSyntaxException {
        if(acs!=null) {
            for(int i=0; i<acs.length; i++) {
                if(acs[i] instanceof String) {
                    ModelElement melem=cls.attributes.get((String)acs[i]);
                    if(melem==null) {
                        melem=cls.model.getElementByName((String)acs[i]);
                    }
                    if(melem==null) {
                        throw new TokenPatternSyntaxException("Model element "+acs[i]+" not found!");
                    }else {
                        acs[i]=melem;
                    }
                }
            }
        }
    }
    
    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        int offset=startIdx+prev.pathLen;
        // e.g. tokens[0+0] for the start state before document start
        if(offset>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"PhraseState partial match continues behind doc");
            return 0;
        }
        int matchCnt=0;
        List<SemAnnot> semAnnots=null;
        List<TagAnnot> tagAnnots=null;
        Collection<AC> acAnnots=null;
        TokenAnnot ta=null;
        int tokShift=0;
        if(labType==LAB_END) {
            if(offset>0) {
                tokShift=-1;
                ta=tokens[offset+tokShift];
                semAnnots=ta.semAnnotPtrs;
                tagAnnots=ta.tagEnds;
                acAnnots=ta.acPtrs;
            }
        }else {
            ta=tokens[offset+tokShift];
            semAnnots=ta.semAnnots;
            tagAnnots=ta.tagStarts;
            acAnnots=(acs!=null && ta.acs!=null)? Arrays.asList(ta.acs): null;
        }
        int saCnt=(semAnnots!=null)? semAnnots.size(): 0;
        int tgCnt=(tagAnnots!=null)? tagAnnots.size(): 0;
        int acCnt=(acAnnots!=null)? acAnnots.size(): 0;
        if(saCnt>0 || tgCnt>0 || acCnt>0) {
            
            /* A. just 1 entry: a label or a tag id/type */
            if(labels.length+tags.length==1 || acs.length==1) {
                if(labels.length>0 && labels[0]>=0) {
                    int i=0;
                    while(i<saCnt) {
                        SemAnnot sa=semAnnots.get(i);
                        if(labels[0]==sa.labelId) {
                            int labLen=0; // applies for LAB_START, LAB_END 
                            if(labType==LAB_END) { // only process labels which end at this token
                                if(sa.endIdx!=ta.idx) {
                                    i++;
                                    continue;
                                }
                            }else if(labType==LAB_BODY) {
                                labLen=sa.getLength();
                            }
                            matchCnt++;
                            if(neg)
                                break;
                            if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState "+labTypes[labType]+" matched phr="+Document.toString(tokens, offset, labLen+tokShift, " "));
                            // TNode node=new TNode(this,prev,labLen);
                            TNode node=PatMatcher.newNode(this, prev, labLen);
                            // node.annots=new TAnnots(node.annots, (Annotable)semAnnots);
                            newNodes.add(node);

                            // skip to the next longer annotated phrase
                            if(labType!=LAB_BODY) {
                                break; // 1 match is enough; all would be of length 0 anyway
                            }
                            i++;
                            while(i<saCnt && semAnnots.get(i).getLength()==labLen)
                                i++;
                            continue;
                        }
                        i++;
                    }
                }else if(tags.length>0) {
                    if(tgCnt>0) {
                        int tagName=-1;
                        int tagType=-1;
                        if(tags[0]>=TAGNAME_BASE) {
                            tagName=tags[0]-TAGNAME_BASE;
                        }else {
                            tagType=tags[0]-TAGTYPE_BASE;
                        }
                        int curLen=(labType==LAB_BODY)? -1: 0; // stays 0 for LAB_START, LAB_END
                        for(TagAnnot tag: tagAnnots) { // sorted by element length
                            if((tagName==-1 || tag.type==tagName) && (tagType==-1 || tag.getTagType()==tagType)) {
                                if(labType==LAB_BODY) {
                                    int labLenNew=tag.tokenCnt(true);
                                    if(labLenNew==curLen && matchCnt>0) // do not match twice the same segment; skip to the next longer tag
                                        continue;
                                    curLen=labLenNew;
                                }
                                matchCnt++;
                                if(neg)
                                    break;
                                if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState "+labTypes[labType]+" matched phr="+Document.toString(tokens, offset, curLen+tokShift, " "));
                                TNode node=PatMatcher.newNode(this, prev, curLen);
                                newNodes.add(node);
                                if(labType!=LAB_BODY) {
                                    break; // 1 match is enough; all would be of length 0 anyway
                                }
                            }
                        }
                    }
                }
                if(acs.length>0 && acAnnots!=null) {
                    
outer_ac:           for(AC acp: acAnnots) {
                        switch(labType) {
                        case LAB_START:
                        case LAB_BODY:
                            if(acp.startToken!=ta)
                                continue;
                            break;
                        case LAB_END:
                            if(ta!=acp.getEndToken())
                                continue;
                            break;
                        }
                        int lastAddedLen=-1;
                        for(Object adObj: acs) {
                            AttributeDef ad=(AttributeDef) adObj;
                            if(acp.getAttribute()==ad || acp.getAttribute().isDescendantOf(ad)) {
                                if(labType==LAB_BODY) {
                                    if(acp.len!=lastAddedLen) {
                                        if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState "+labTypes[labType]+" matched phr="+Document.toString(tokens, offset, acp.len+tokShift, " "));
                                        matchCnt++;
                                        if(neg)
                                            break outer_ac;
                                        TNode node=PatMatcher.newNode(this, prev, acp.len);
                                        newNodes.add(node);
                                        lastAddedLen=acp.len;
                                    }
                                }else {
                                    if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState "+labTypes[labType]+" matched phr="+Document.toString(tokens, offset, acp.len+tokShift, " "));
                                    matchCnt++;
                                    if(neg)
                                        break outer_ac;
                                    TNode node=PatMatcher.newNode(this, prev, 0);
                                    newNodes.add(node);
                                    lastAddedLen=0;
                                    break;
                                }
                            }
                        }
                        if(lastAddedLen!=-1 && (neg || labType!=LAB_BODY))
                            break;
                    }
                }
                
            // B. multiple OR'ed entries:
            }else if(combType==LAB_CMB_OR) {
                int curLen=0;
                boolean skip=false;
                // first, search semannots:
                if(labels.length>0) {
                    // ta.semAnnots is ordered ascending by annotation length, good to have it in outer loop
outer_or_lab:       for(int i=0;i<saCnt;i++) {
                        SemAnnot sa=semAnnots.get(i);
                        if(labType==LAB_END) { // only process labels which end at the token
                            if(sa.endIdx!=ta.idx) {
                                continue;
                            }
                        }
                        if(skip) {
                            if(sa.getLength()==curLen)
                                continue;
                            else
                                skip=false;
                        }
                        if(labType==LAB_BODY && sa.getLength()>curLen) {
                            curLen=sa.getLength();
                        }
                        // see if 1 of the SAs in the phrase of length=curSALen satisfies 1 of our needs
                        for(int j=0;j<labels.length;j++) {
                            if(sa.labelId == labels[j]) {
                                if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState(OR) matched phr="+Document.toString(tokens, offset, curLen+tokShift, " "));
                                matchCnt++;
                                if(neg) {
                                    tgCnt=0; // just to skip iterating through tags below
                                    break outer_or_lab;
                                }
                                // int matchLen=(labType==LAB_BODY)? curLen: 0;
                                // TNode node=new TNode(this,prev,matchLen);
                                TNode node=PatMatcher.newNode(this, prev, curLen);
                                newNodes.add(node);
                                if(labType!=LAB_BODY) { // 1 match is enough, all would be of 0 length
                                    break outer_or_lab;
                                }
                                skip=true;
                                break;
                            }else {
                                continue;
                            }
                        }
                    }
                }
                // second, search tags:
                if(tgCnt>0 && tags.length>0) {
                    curLen=0; // stays 0 for LAB_START, LAB_END
                    skip=false;
outer_or_tag:       for(TagAnnot tag: tagAnnots) { // sorted by element length
                        if(labType==LAB_BODY) {
                            int labLenNew=tag.tokenCnt(true);
                            if(skip && labLenNew==curLen) {
                                // do not match twice the same segment; skip to the next longer tag
                                continue;
                            }
                            curLen=labLenNew;
                            skip=false;
                        }
                        for(int tid: tags) {
                            int tagName=-1;
                            int tagType=-1;
                            if(tid>=TAGNAME_BASE) {
                                tagName=tid-TAGNAME_BASE;
                            }else {
                                tagType=tid-TAGTYPE_BASE;
                            }
                            if((tagName==-1 || tag.type==tagName) && (tagType==-1 || tag.getTagType()==tagType)) {
                                matchCnt++;
                                if(neg)
                                    break outer_or_tag;
                                if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState "+labTypes[labType]+" matched phr="+Document.toString(tokens, offset, curLen+tokShift, " "));
                                TNode node=PatMatcher.newNode(this, prev, curLen);
                                newNodes.add(node);
                                if(labType!=LAB_BODY) {
                                    break outer_or_tag; // 1 match is enough; all would be of length 0 anyway
                                }
                                skip=true; // for LAB_BODY, skip all further tags with the same length 
                            }                            
                        }
                    }
                }
               
            // C. multiple AND'ed entries:
            }else { // combType==LAB_CMB_AND
                
                // C.1. multiple AND'ed BODY entries (with various but sorted lengths):
                if(labType==LAB_BODY && !neg) {
                    if((labels.length==0 || saCnt>0) && (tags.length==0 || tgCnt>0)) {
                        int curLen=-1; // current length of matched phrases in tokens
                        int si=0;
                        int ti=0;
                        while(true) {
                            // get the first label and the first tag for the current length (starting with 0)
                            SemAnnot sa0=null;
                            TagAnnot tag0=null;
                            int saLen=-1;
                            int tagLen=-1;
                            if(si<saCnt) {
                                sa0=semAnnots.get(si);
                                saLen=sa0.getLength();
                            }
                            if(ti<tgCnt) {
                                tag0=tagAnnots.get(ti);
                                tagLen=tag0.tokenCnt(true);
                            }
                            // exit if no more labels and labels needed 
                            if(sa0==null && labels.length>0)
                                break;
                            // exit if no more tags and tags needed 
                            if(tag0==null && tags.length>0)
                                break;
                            // determine curLen
                            if(saLen==-1)
                                curLen=tagLen;
                            else if(tagLen==-1)
                                curLen=saLen;
                            else
                                curLen=Math.min(saLen, tagLen);
                            // exit if no more labels
                            if(curLen==-1)
                                break;
                            boolean satisfied=true;
                            // try to satisfy labels with some phrase of curLen
                            if(labels!=null) {
                                for(int lid: labels) {
                                    boolean foundPhrOfCurLen=false;
                                    boolean searching=true;
                                    for(int i=0; (si+i)<saCnt; i++) {
                                        SemAnnot sa=semAnnots.get(si+i);
                                        if(sa.getLength()!=curLen) {
                                            si+=i; // points to the first label of the next higher length (or null)
                                            break;
                                        }
                                        if(searching && sa.labelId == lid) {
                                            if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState(OR) matched phr="+Document.toString(tokens, offset, curLen+tokShift, " "));
                                            matchCnt++;
                                            TNode node=PatMatcher.newNode(this, prev, curLen);
                                            newNodes.add(node);
                                            searching=false;
                                        }
                                    }
                                    if(!foundPhrOfCurLen) {
                                        satisfied=false;
                                        break;
                                    }
                                }
                            }
                            // try to satisfy tags with some phrase of curLen
                            if(tags!=null && satisfied) {
                                for(int tid: tags) {
                                    boolean foundTagOfCurLen=false;
                                    boolean searching=true;
                                    for(int i=0; (ti+i)<tgCnt; i++) {
                                        TagAnnot tag=tagAnnots.get(ti+i);
                                        if(tag.tokenCnt(true)!=curLen) {
                                            ti+=i; // points to the first tag of the next higher length (or null)
                                            break;
                                        }
                                        // if match, add TNode and point to the next longer tag
                                        if(searching) {
                                            int tagName=-1;
                                            int tagType=-1;
                                            if(tid>=TAGNAME_BASE) {
                                                tagName=tid-TAGNAME_BASE;
                                            }else {
                                                tagType=tid-TAGTYPE_BASE;
                                            }
                                            if((tagName==-1 || tag.type==tagName) && (tagType==-1 || tag.getTagType()==tagType)) {
                                                matchCnt++;
                                                if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState "+labTypes[labType]+" matched phr="+Document.toString(tokens, offset, curLen+tokShift, " "));
                                                TNode node=PatMatcher.newNode(this, prev, curLen);
                                                newNodes.add(node);
                                                searching=false; // only proceed to the next longer tag
                                            }
                                        }
                                    }
                                    if(!foundTagOfCurLen) {
                                        satisfied=false;
                                        break;
                                    }
                                }
                            }
                        }
                    }                    
                                        
                // C.2. multiple AND'ed START/END entries (with 0 length):
                }else {
                    boolean foundAll=true;
                    if(saCnt>0) {
                        for(int lab: labels) { // see if all labels hold
                            boolean found=false;
                            for(SemAnnot sa: semAnnots) {
                                if(labType==LAB_END) { // only process labels which end at the token
                                    if(sa.endIdx!=ta.idx) {
                                        continue;
                                    }
                                }
                                if(sa.labelId == lab) {
                                    found=true;
                                    break;
                                }
                            }
                            if(!found) {
                                foundAll=false;
                                break;
                            }
                        }
                    }
                    if(tgCnt>0 && foundAll) { // see if all tags hold
                        for(int tid: tags) {
                            boolean found=false;
                            for(TagAnnot tag: tagAnnots) {
                                int tagName=-1;
                                int tagType=-1;
                                if(tid>=TAGNAME_BASE) {
                                    tagName=tid-TAGNAME_BASE;
                                }else {
                                    tagType=tid-TAGTYPE_BASE;
                                }
                                if((tagName==-1 || tag.type==tagName) && (tagType==-1 || tag.getTagType()==tagType)) {
                                    found=true;
                                    break;
                                }
                            }
                            if(!found) {
                                foundAll=false;
                                break;
                            }
                        }
                    }
                    if(foundAll) {
                        matchCnt++;
                        if(!neg) {
                            if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"PhraseState(AND,S/E) matched before token="+Document.toString(tokens, offset, 1, " "));
                            // TNode node=new TNode(this,prev,0);
                            TNode node=PatMatcher.newNode(this, prev, 0);
                            newNodes.add(node);
                        }
                    }
                }
            }
        }
        if(neg) {
            if(matchCnt==0) {
                matchCnt=1;
                TNode node=PatMatcher.newNode(this, prev, 0);
                newNodes.add(node);
            }else {
                matchCnt=0;
            }
        }
        if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.TRC,this+((matchCnt>0)?(" matched "+matchCnt+"x"):" mismatch")+" at "+Document.toString(tokens, offset, 1, " "));
        return matchCnt;
    }
    
    public String toString() {
        if(data==null) {
            StringBuffer s=new StringBuffer(32);
            Character sep=(combType==LAB_CMB_AND)? '&': '|';
            if(labType==LAB_START)
                s.append('^');
            s.append('[');
            if(labels!=null && labels.length>0) {
                int i=0;
                for(int lid: labels) {
                    if(i++>0)
                        s.append(sep);
                    AnnotationF af=AnnotationF.getAnnotation(lid);
                    s.append((af==null)? "?":af.name);
                }
            }
            s.append("]");
            s.append(sep);
            s.append("<");
            if(tags!=null && tags.length>0) {
                int i=0;
                for(int tid: tags) {
                    if(i++>0)
                        s.append(sep);
                    String tag;
                    if(tid>=TAGNAME_BASE) {
                        int tagName=tid-TAGNAME_BASE;
                        tag=TagNameF.getSingleton().toString(tagName);
                    }else {
                        int tagType=tid-TAGTYPE_BASE;
                        tag=TagTypeF.getSingleton().toString(tagType);
                    }
                    s.append(tag);
                }
            }
            s.append('>');
            if(labType==LAB_END)
                s.append('$');
            data=s.toString();
        }
        return super.toString();
    }
}

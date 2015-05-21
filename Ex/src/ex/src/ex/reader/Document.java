// $Id: Document.java 1872 2009-03-28 13:12:22Z labsky $
package ex.reader;

import java.util.*;

import org.cyberneko.html.HTMLElements;

import uep.util.*;
import ex.util.Const;
import ex.train.*;
import ex.ac.PatMatch;
import ex.ac.TokenPattern;
import ex.ac.Annotable;
import ex.ac.Extractable;
import ex.ac.AC;
import ex.dom.ExtractedObjects;
import ex.features.TagNameF;
import ex.features.TagTypeF;
import ex.parser.ACLattice;
import ex.model.AttributeDef;
import ex.parser.ICBase;
import gnu.trove.TIntArrayList;

class DocumentMetaInfo {
    public DocumentMetaInfo(String enc, int encIdx, int headIdx, int htmlIdx, String ol, int oli) { 
        encoding=enc; encStartIdx=encIdx; afterHeadStartIdx=headIdx; afterHtmlStartIdx=htmlIdx;
        onload=ol; onloadStartIdx=oli;
    }
    public String encoding; // e.g. utf-8; null if no meta encoding is found 
    public int encStartIdx; // index where the encoding string starts in document's text [-1]
    public int afterHeadStartIdx; // points 1 character after <head> to enable meta tag and script insertion [-1]
    public int afterHtmlStartIdx; // points 1 character after <html> [-1]
    public int getScriptInsertPos() { // return insert position for meta tags and scripts
        return (afterHeadStartIdx!=-1)? afterHeadStartIdx: ((afterHtmlStartIdx!=-1)? afterHtmlStartIdx: 0);
    }
    public int onloadStartIdx; // points to 1st char of the onload attribute's content [points to '>' in <body ...>]
    public String onload; // onload attribute's content [null]
}

public class Document {
    public static final int LAST_IDX=-2;
    public String id;
    public CacheItem cacheItem;
    public DocumentMetaInfo metaInfo;
    public String data; // reference to cacheItem.s
    // string created by concatenating all pcdata and attributes from document,
    // pointed to by Annots
    public String tokenString;
    public Instance[] instances; // training instances that can be extracted from this document
    public int maxDomDepth; // length of the longest path from <html> root TagAnnot to a leaf element 
    // (which can either be a TagAnnot again, such as <img>, or a TokenAnnot such as 'hello')

    // textual tokens or or token tags, in token order, length=token count
    public TokenAnnot[] tokens;
    // public TokenInfo[] tis; // dtto as tokens, but refers directly to TokenAnnot.ti
    // non-leaves of DOM tree - linked from tokens
    // public TagAnnot[] tags;
    public TagAnnot rootElement;

    // semantic annotations - from labeled document or determined by parser.
    // contains all labels, not only leafs (attributes) but also classes
    // ordered by startIdx
    public SemAnnot[] labels;
    
    /** graph of ACs used for searching for the best paths over its segments */
    public ACLattice acLattice;

    /** Document classifications set externally. Can be utilized from scripted patterns. */
    public List<DocumentClass> classifications;
    
    /** Extracted objects in IET format. */
    public List<ExtractedObjects> extractedPaths;
    
    // labeling
    public int labelCnt; // number of labels shown in the last call to getAnnotatedDocument()
    protected static String liveScript=null;
    
    protected boolean genExtraInfo; 
    
    private Logger log;

    public Document(String id, CacheItem ci) {
        this.id=id;
        this.data=ci.data;
        cacheItem=ci;
        labelCnt=-1;
        maxDomDepth=-1;
        rootElement=null;
        log=Logger.getLogger("Document");
        genExtraInfo=false;
        classifications=new ArrayList<DocumentClass>(4);
        extractedPaths=new ArrayList<ExtractedObjects>(4);
    }

    /** Frees all members to enable garbage collection. */
    public void clear() {
        data=null;
        for(TokenAnnot ta: tokens) {
            ta.clear();
        }
        tokens=null;
        if(labels!=null) {
            for(SemAnnot sa: labels) {
                sa.clear();
            }
            labels=null;
        }
        rootElement.clear(); // recursive
        rootElement=null;
        acLattice.clear();
        acLattice=null;
        instances=null;
        data=null;
        tokenString=null;
        metaInfo=null;
        cacheItem=null;
        id=null;
        classifications.clear();
        extractedPaths.clear();
    }
    
    /** Whether to generate extended data structures for this doc: AC references for each member token. */
    public void setGenerateExtraInfo(boolean on) {
        genExtraInfo=on;
    }

    /** Whether to generate extended data structures for this doc: AC references for each member token. */
    public boolean getGenerateExtraInfo() {
        return genExtraInfo;
    }

    public String getAnnotData(Annot a) {
        String token=data.substring(a.startIdx, a.endIdx);
        return token;
    }

    public int setTokenFeatures(Vocab vocab) {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Document["+cacheItem.absUrl+"].setTokenFeatures()");
        // tis=new TokenInfo[tokens.length];
        for(int i=0;i<tokens.length;i++) {
            int rc=tokens[i].setFeatures(vocab);
            if(rc!=Const.EX_OK) {
                log.LG(Logger.ERR,"Error computing features for token '"+tokens[i].toString()+"'");
                return rc;
            }
            // tis[i]=tokens[i].ti;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"SetTokenFeatures OK");
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Document tokens:\n"+tokensToString(true,1));
        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"Updated vocab:\n"+vocab.toString());
        return Const.EX_OK;
    }

    public String tokensToString(boolean verbose, int withTags) {
        StringBuffer allTokens=new StringBuffer(tokens.length*20);
        for(int i=0;i<tokens.length;i++) {
            TokenAnnot ta=tokens[i];
            if(verbose && log.IFLG(Logger.TRC))
                log.LG(Logger.TRC,i+" '"+getAnnotData(ta)+"'~"+ta.toString());
            allTokens.append(i+". "+ta.getToken());
            if(verbose)
                allTokens.append("["+ta.ti.toString()+"]");
            if(withTags>0) {
                allTokens.append("["+ta.getDomPath(-1)+"]");
            }
            allTokens.append("\n");
        }
        return allTokens.toString();
    }

    public int findMatches(PhraseBook attBook, int matchMode) {
        int hitCnt=0;
        int maxLen=attBook.getMaxLen();
        NBestResult res=new NBestResult(20);
        if(log.IFLG(Logger.INF)) { 
            log.LG(Logger.INF,"findMatches in document="+id+", attBook="+attBook.getName()+", maxLen="+maxLen);
            log.LG(Logger.INF,"attBook dump:\n"+attBook.toString());
        }
        for(int i=0;i<tokens.length;i++) {
            res.clear();
            int rc=attBook.get(tokens, i, java.lang.Math.min(maxLen, tokens.length-i), true, res);
            for(int j=0; j<res.length; j++) {
                PhraseInfo pi=(PhraseInfo) res.items[j];
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Found '"+((pi==null)? "null": pi.toPhrase())+"'");
            }
            hitCnt+=res.length;
        }
        return hitCnt;
    }

    /* deprecated */
    public int getAnnotatedDocument(StringBuffer buff, boolean showPatterns, boolean showAtts, String addMetaEnc) {
        buff.ensureCapacity(data.length()+1024);
        String[] annotEnds=new String[tokens.length+1]; // for annot ends after each token
        int pos=0;
        char[] chars=data.toCharArray();
        int cnt=0;
        int[] idxs=new int[] {0,0};
        Label lab=new Label(0,idxs,null,null);

        for(int i=0;i<tokens.length;i++) {
            TokenAnnot ta=tokens[i];

            if((showPatterns && ta.matchStarts!=null) || (showAtts && ta.acs!=null)) {
                // copy document data until this token
                if(addMetaEnc==null) {
                    buff.append(chars,pos,ta.startIdx-pos);
                    // special case when meta encoding tag needs to be added
                }else if(updateMetaEncoding(buff,chars,pos,ta.startIdx,addMetaEnc)) {
                    addMetaEnc=null;
                }
                pos=ta.startIdx;
            }

            /* label matched patterns */
            if(showPatterns && ta.matchStarts!=null) {
                // insert pattern annotation starts
                for(int j=0;j<ta.matchStarts.size();j++) {
                    cnt++;
                    PatMatch pm=ta.matchStarts.get(j);
                    TokenPattern pat=pm.pat;
                    pat.getAttributeDef().genStyle(pm,lab);
                    buff.append("<span title=\""+lab.title+"\" style=\""+lab.style+"\">");

                    int endIdx=i+pm.getLength()-1;
                    if(annotEnds[endIdx]==null)
                        annotEnds[endIdx]="</span>";
                    else
                        annotEnds[endIdx]+="</span>";
                }
            }

            /* label attribute candidates */
            if(showAtts && ta.acs!=null) {
                // insert pattern annotation starts
                for(int j=0;j<ta.acs.length;j++) {
                    cnt++;
                    AttributeDef ad=ta.acs[j].getAttribute();
                    ad.genStyle(ta.acs[j],lab);
                    buff.append("<span title=\""+lab.title+"\" style=\""+lab.style+"\">");

                    int endIdx=i+ta.acs[j].len-1;
                    if(annotEnds[endIdx]==null)
                        annotEnds[endIdx]="</span>";
                    else
                        annotEnds[endIdx]+="</span>";
                }
            }

            /* annotation ends */
            if(annotEnds[i]!=null) {
                // copy document data until this token
                buff.append(chars,pos,ta.endIdx-pos);
                pos=ta.endIdx;
                // insert pattern annotation ends
                buff.append(annotEnds[i]);
            }
        }
        buff.append(chars,pos,chars.length-pos);

        labelCnt=cnt;
        return cnt;
    }

    public static String toStringDbg(AbstractToken[] tokens, int offset, int len, String glue) {
        String ret;
        if(len==0)
            ret="(gap before "+tokens[offset].getToken()+")";
        else
            ret=toString(tokens, offset, len, glue);
        return ret;
    }
    
    public static String toString(AbstractToken[] tokens, int offset, int len, String glue) {
        StringBuffer buff=new StringBuffer(128);
//        buff.append(tokens[offset].getToken());
//        for(int i=1;i<len;i++) {
//            if(glue!=null)
//                buff.append(glue);
//            buff.append(tokens[offset+i].getToken());
//        }        
        for(int i=0;i<len;i++) {
            if(glue!=null && i!=0)
                buff.append(glue);
            buff.append(tokens[offset+i].getToken());
        }
        return buff.toString();
    }

    public static Comparator cmpLabelsByLen=new Comparator() { 
        public int compare(Object o1, Object o2) { 
            return ((Annotable)o2).getLength() - ((Annotable)o1).getLength();
        }
    };

    public void sortLabelsByLen() {
        for(int i=0;i<tokens.length;i++) {
            TokenAnnot ta=tokens[i];
            if(ta.acs==null)
                continue;
            Arrays.sort(ta.acs, cmpLabelsByLen);
        }
    }

    /** Generates an HTML annotated document with highlighted annotable objects; typically 
     *  attribute candidates or pattern matches. */
    public String getAnnotatedDocument(boolean showPatterns, boolean showAtts, String addMetaEnc, 
            boolean live, Collection<AC> acsToAnnotate) {
        LabeledDocBuilder bldr=new LabeledDocBuilder(data);
        ArrayList<LabelGroup> annotGroups=new ArrayList<LabelGroup>(32);
        LabelGroup openGroup=null;
        ArrayList<LabelRecord> openAtts=new ArrayList<LabelRecord>(16);
        ArrayList<Annotable> sortList=new ArrayList<Annotable>(8);
        TIntArrayList idxs=new TIntArrayList(16);
        int jel=0; // just ended counter
        int cnt=0;
        
        // do not annotate anything until body
        int bodyStartCharIdx=this.metaInfo.onloadStartIdx;
        int bodyStartTokIdx=tokens.length;
        for(int i=0;i<tokens.length;i++) {
            if(tokens[i].startIdx>bodyStartCharIdx) {
                bodyStartTokIdx=i;
                break;
            }
        }
        
        for(int i=bodyStartTokIdx;i<tokens.length;i++) {
            TokenAnnot ta=tokens[i];

            /* update openAtts, see how many labels end after this token */
            jel=0;
            for(int j=0;j<openAtts.size();j++) {
                LabelRecord lr=(LabelRecord) openAtts.get(j);
                if(--lr.cnt<=0) {
                    openAtts.remove(j);
                    j--;
                    lr.disposeInstance();
                    jel++;
                }
            }

            /* see how many labels start before this token */
            // int anCnt=ta.getAnnotablesCnt(showPatterns, showAtts);
            sortList.clear();
            int anCnt=ta.getAnnotables(sortList, showPatterns, showAtts);
            // if positive filter given, remove all non-matching annotables
            if(acsToAnnotate!=null && anCnt>0) {
                sortList.retainAll(acsToAnnotate);
                anCnt=sortList.size();
                /*
                int j2=0;
                for(int j=0;j<anCnt;j++) {
                    Annotable a=sortList.get(j);
                    if(acsToAnnotate.contains(a)) {
                        if(j!=j2)
                            sortList.set(j2, a);
                        j2++;
                    }
                }
                if(j2<anCnt)
                    sortList.removeAllAfter(j2);
                */
            }
            if(anCnt==0 && jel==0)
                continue;

            bldr.proceedTo(ta.startIdx);

            /* open new labels for token's Annotables (matched patterns and/or attribute candidates) */
            if(anCnt>0) {
                //sortList.clear();
                //for(int j=0;j<anCnt;j++)
                //    sortList.add(ta.getAnnotable(j, showPatterns, showAtts));
                Collections.sort(sortList, cmpLabelsByLen); // sort by increasing length
            }
            for(int j=0;j<anCnt;j++) {
                Annotable an=(Annotable) sortList.get(j);
                cnt++;
                // open new group if needed
                if(openGroup==null) {
                    openGroup=new LabelGroup(annotGroups.size()+1, ta.startIdx);
                    annotGroups.add(openGroup);
                    if(live) {
                        // insert group of labels
                        int gid=annotGroups.size();
                        bldr.insert("<span id=\"_LBG"+gid+"\" onclick=\"_grpNext("+gid+")\">");
                    }
                }
                // create new label in the group
                int len=an.getLength();
                // precaution against annotations reaching beyond the end of doc
                if(i+len>tokens.length) {
                    log.LG(Logger.ERR,"Annotation reaches beyond end of doc: "+an);
                    len=tokens.length-i;
                }
                int labId=openGroup.labels.size()+1;
                //int startIdx=ta.startIdx-openGroup.startIdx;
                //int endIdx=tokens[i+len-1].endIdx-openGroup.startIdx;
                //Label lab=new Label(labId,startIdx,endIdx,null,null);
                // we could cause crossing tags by inserting a single span across the whole label:
                // split label into consecutive sequences of tokens with the same parent
                idxs.clear();
                idxs.add(ta.startIdx-openGroup.startIdx);
                Annot par=ta.parent;
                for(int k=1;k<len;k++) {
                    TokenAnnot nta=tokens[i+k];
                    if(par!=nta.parent) {
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Parents not equal: left="+par+",right="+nta.parent);
                        idxs.add(tokens[i+k-1].endIdx-openGroup.startIdx);
                        idxs.add(nta.startIdx-openGroup.startIdx);
                        par=nta.parent;
                    }
                }
                idxs.add(tokens[i+len-1].endIdx-openGroup.startIdx);
                int[] iidxs=new int[idxs.size()];
                for(int k=0;k<iidxs.length;k++)
                    iidxs[k]=idxs.get(k);
                Label lab=new Label(labId,iidxs,null,null);
                an.getModelElement().genStyle(an,lab); // fills in lab.title and lab.style 

                openGroup.labels.add(lab);
                // insert label
                if(!live) {
                    bldr.insert("<span title=\""+lab.title+"\" style=\""+lab.style+"\">");
                }
                // update openAtts and jel
                if(len>1) {
                    LabelRecord lr=LabelRecord.getInstance();
                    lr.label=lab;
                    lr.cnt=len-1;
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"[end in "+(len-1)+" tokens]");
                    openAtts.add(lr);
                }else {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"[end immediate]");
                    jel++;
                }
            }

            /* close any labels that end after this token */
            if(jel>0) {
                bldr.proceedTo(ta.endIdx);
                if(!live) {
                    // copy document data until the end of this token
                    while(jel>0) {
                        bldr.insert("</span>");
                        jel--;
                    }
                }else {
                    if(openAtts.size()==0) {
                        bldr.insert("</span>");
                        openGroup=null;
                    }
                }
            }
        }
        bldr.proceedTo(data.length());

        ArrayList<DocumentEdit> edits=new ArrayList<DocumentEdit>(8);
        if(addMetaEnc!=null)
            editMetaEncoding(edits,addMetaEnc);

        if(live) {
            editOnLoad(edits);

            StringBuffer script=new StringBuffer(512);
            int n=annotGroups.size();
            script.append("var _LBGS=[");
            for(int i=0;i<n;i++) {
                if(i>0)
                    script.append(",\n");
                ((LabelGroup)annotGroups.get(i)).toJS(script);
            }
            script.append("];\n");
            script.append(liveScript);
            editAddScript(edits,script.toString());
        }

        int ec=edits.size();
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Generated "+cnt+" labels, performing "+ec+" document edits.");
        bldr.applyEdits(edits);

        labelCnt=cnt;
        return bldr.toString();
    }

    protected int editOnLoad(List<DocumentEdit> edits) {
        /* add live label init code to onload handler, or create it; do nothing if no body found */
        DocumentEdit de=null;
        if(metaInfo.onload==null && metaInfo.onloadStartIdx!=-1) {
            de=new DocumentEdit(metaInfo.onloadStartIdx, 0, " onload=\"_labelInit();\"");
        }else {
            de=new DocumentEdit(metaInfo.onloadStartIdx, 0, "_labelInit();");
        }
        edits.add(de);
        return 1;
    }

    protected int editMetaEncoding(List<DocumentEdit> edits, String newMetaEnc) {
        /* add or edit meta encoding tag */
        DocumentEdit de=null;

        /* meta tag with encoding was found */
        if(metaInfo.encStartIdx!=-1) {
            // requested encoding is the same as already present in meta tag
            if(metaInfo.encoding!=null && metaInfo.encoding.equalsIgnoreCase(newMetaEnc))
                return 0;
            // substitute different encoding
            de=new DocumentEdit(metaInfo.encStartIdx, metaInfo.encoding.length(), newMetaEnc);
        }

        /* meta tag with encoding not found - insert new meta tag after <head> */
        else if(metaInfo.afterHeadStartIdx!=-1) {
            de=new DocumentEdit(metaInfo.afterHeadStartIdx, 0, 
                    "\n<meta http-equiv=\"Content-Type:\" content=\"text/html; charset="+newMetaEnc+"\">\n");
        }

        /* insert new meta tag after <html> */
        else if(metaInfo.afterHtmlStartIdx!=-1) {
            de=new DocumentEdit(metaInfo.afterHtmlStartIdx, 0, 
                    "\n<head><meta http-equiv=\"Content-Type:\" content=\"text/html; charset="+newMetaEnc+"\"></head>\n");
        }

        /* document has no html and no head tag: insert at the beginning */
        else {
            de=new DocumentEdit(0, 0, "<html><head><meta http-equiv=\"Content-Type:\" content=\"text/html; charset="+newMetaEnc+"\"></head>\n");
        }

        if(de!=null) {
            edits.add(de);
            return 1;
        }
        return 0;
    }

    protected int editAddScript(List<DocumentEdit> edits, String script) {
        /* add script element to head */
        edits.add(new DocumentEdit(metaInfo.getScriptInsertPos(), 0, 
                "\n<script language=\"javascript\" type=\"text/javascript\">//<![CDATA[\n"+script+"\n//]]></script>\n"));
        return 1;
    }

    /* deprecated */
    private boolean updateMetaEncoding(StringBuffer buff, char[] chars, int pos, int nextIdx, String addMetaEnc) {

        /* meta tag with encoding was found */
        if(metaInfo.encStartIdx!=-1) {
            // requested encoding is the same as already present in meta tag
            if(metaInfo.encoding!=null && metaInfo.encoding.equalsIgnoreCase(addMetaEnc))
                return true;
            // substitute different encoding
            if(metaInfo.encStartIdx<nextIdx) {
                buff.append(chars,pos,metaInfo.encStartIdx-pos);
                buff.append(addMetaEnc);
                buff.append(chars, metaInfo.encStartIdx-pos+metaInfo.encoding.length(),
                        nextIdx-(metaInfo.encStartIdx-pos+metaInfo.encoding.length()));
                return true;
            }
            // we did not yet reach encoding's position in the doc
            return false;
        }

        /* meta tag with encoding not found - insert new meta tag after <head> */
        if(metaInfo.afterHeadStartIdx!=-1) {
            if(metaInfo.afterHeadStartIdx<nextIdx) {
                buff.append(chars,pos,metaInfo.afterHeadStartIdx-pos);
                buff.append("\n<meta http-equiv=\"Content-Type:\" content=\"text/html; charset="+addMetaEnc+"\">\n");
                buff.append(chars,metaInfo.afterHeadStartIdx-pos,nextIdx-(metaInfo.afterHeadStartIdx-pos));
                return true;
            }
            return false; // not there yet
        }

        /* insert new meta tag after <html> */
        if(metaInfo.afterHtmlStartIdx!=-1) {
            if(metaInfo.afterHtmlStartIdx<nextIdx) {
                buff.append(chars,pos,metaInfo.afterHtmlStartIdx-pos);
                buff.append("\n<head><meta http-equiv=\"Content-Type:\" content=\"text/html; charset="+addMetaEnc+"\"></head>\n");
                buff.append(chars,metaInfo.afterHtmlStartIdx-pos,nextIdx-(metaInfo.afterHtmlStartIdx-pos));
                return true;
            }
            return false; // not there yet
        }

        /* document has no html and no head tag: insert at the beginning */
        buff.append("<html><head><meta http-equiv=\"Content-Type:\" content=\"text/html; charset="+addMetaEnc+"\"></head>\n");
        buff.append(chars,pos,nextIdx-pos);
        return true;
    }

    /** Finds the smallest containing block element by start and end token indices. */
    public TagAnnot getParentBlock(int startTokenIdx, int endTokenIdx, int tagType) {
        return getParentBlock(tokens, tokens[startTokenIdx], tokens[endTokenIdx], -1, tagType, -1, -1);
    }

    /** Finds the smallest element that contains both the start and end tokens. */
    public TagAnnot getParentBlock(TokenAnnot ta1, TokenAnnot ta2, int tagType) {
        return getParentBlock(tokens, ta1, ta2, -1, tagType, -1, -1);
    }
    
    /** Finds the smallest element that contains both the start and end tokens.
     *  Climbs up the tree until it finds a tag which satisfies the given 
     *  tagId and tagType (set to -1 if you don't care).
     *  Specify startIdx and/or endIdx to require the start/end tokens 
     *  to have specific token indices within the returned tag 
     *  (set to -1 if you don't care). Set endIdx to Document.LAST_IDX 
     *  to require the end token to be the last one in the returned tag. */
    public static TagAnnot getParentBlock(TokenAnnot[] toks, 
            TokenAnnot ta1, TokenAnnot ta2, 
            int tagId, int tagType, int startIdx, int endIdx) {
        TagAnnot tga1=(TagAnnot) ta1.parent;
        TagAnnot tga2=(TagAnnot) ta2.parent;
        TagAnnot par;
        if(tga1==null || tga2==null) {
            par=null;
        }else if(tga1==tga2) {
            par=tga1;
        // one contains the other
        }else if(tga1.hasDescendant(tga2)) {
            par=(TagAnnot) tga1;
        }else if(tga2.hasDescendant(tga1)) {
            par=(TagAnnot) tga2;
        }else {
            // find the least common ancestor
            while(tga1!=null && tga1.endIdx < tga2.startIdx) {
                tga1=(TagAnnot) tga1.parent;
            }
            par=tga1;
        }
        if(tagType!=-1 || tagId!=-1 || startIdx!=-1 || endIdx!=-1) {
            while(par!=null) {
                if((tagId==-1 || par.type==tagId) &&
                   (tagType==-1 || par.getTagType()==tagType) &&
                   (startIdx==-1 || getChildTokenIdx(toks,par,ta1)==startIdx) &&
                   (endIdx==-1 || 
                    (endIdx==LAST_IDX && getChildTokenIdx(toks,par,ta2)==(par.tokenCnt(true)-1)) || 
                    (getChildTokenIdx(toks,par,ta2)==endIdx)
                   ))
                    break;
                par=(TagAnnot) par.parent;
            }
        }
        return par;
    }
    
    /** Returns n-th child token within a tag; or null if there is no such token. */
    public TokenAnnot getChildTokenByIdx(TagAnnot tga, int idx) {
        if(tga.firstToken==null)
            return null;
        if(idx==0)
            return tga.firstToken;
        int cnt=tga.tokenCnt(true);
        if(idx<cnt)
            return tokens[tga.firstToken.idx+idx];
        return null;
    }

    /** Returns the index of the given child token within a containing tag.
     * Returns -1 if the token is not inside the tag. */
    public static int getChildTokenIdx(TokenAnnot[] toks, TagAnnot tga, TokenAnnot tok) {
        if(tga.firstToken==null)
            return -1;
        if(tga.firstToken==tok)
            return 0;
        int endDocIdx=tga.firstToken.idx+tga.tokenCnt(true)-1;
        for(int i=tga.firstToken.idx+1;i<=endDocIdx;i++) {
            TokenAnnot t=toks[i];
            if(t==tok)
                return i-tga.firstToken.idx;
        }
        return -1;
    }
    
    /** Finds a tag of the desired id and type which starts after ta1 and 
     *  ends before ta2. Set tagId and tagType to -1 if you don't care. 
    public TagAnnot getBlockBetween(TokenAnnot ta1, TokenAnnot ta2, int tagId, int tagType) {
        TagAnnot ret=null;
        return ret;
    }
    */
    
    /** Populates tagList with all child elements satisfying filterName and filterType (-1 for all).
     * Searches all descendants if recursive. Returns the number of found tags. */
    int getChildTags(List<TagAnnot> tagList, TagAnnot parent, short filterName, short filterType, boolean recursive) {
        int rc=0;
        if(parent.childNodes==null)
            return rc;
        for(int i=0;i<parent.childNodes.length;i++) {
            Annot an=parent.childNodes[i];
            if(an.annotType!=Annot.ANNOT_TAG)
                continue;
            TagAnnot tga=(TagAnnot) an;
            if(recursive)
                rc+=getChildTags(tagList, tga, filterName, filterType, true);
            if(filterName!=-1 && filterName!=tga.type)
                continue;
            if(filterType!=-1 && filterType!=tga.getTagType())
                continue;
            tagList.add(tga);
            rc++;
        }
        return rc;
    }
    
    /** Populates tagList with all descendant elements which contain at least one token so that
     *  no tokens overlap. The elements returned are the lower-most ones except for those given in tagNames. 
     *  Returns the number of found tags. */
    int getChildTagsWithTokens(List<TagAnnot> tagList, TagAnnot parent, short[] tagNames) {
        int rc=0;
        if(parent.childNodes==null)
            return rc;
        for(int i=0;i<parent.childNodes.length;i++) {
            Annot an=parent.childNodes[i];
            if(an.annotType!=Annot.ANNOT_TAG)
                continue;
            TagAnnot tga=(TagAnnot) an;
            if(tga.type==TagNameF.TEXTNODE && tga.childNodes!=null && tga.childNodes.length!=0) {
                tagList.add(tga);
                rc++;
                continue;
            }
            int j=0;
            for(;j<tagNames.length;j++) {
                if(tga.type==tagNames[j]) {
                    if(tga.firstToken!=null) {
                        tagList.add(tga);
                        rc++;
                    }
                    break;
                }
            }
            if(j<tagNames.length)
                continue;
            rc+=getChildTagsWithTokens(tagList, tga, tagNames);
        }
        return rc;
    }
    
    /** Gets the first token following after the end of the given element or null if no such element exists. */ 
    TokenAnnot getTokenAfter(TagAnnot elem) {
        TokenAnnot ta=null;
        TagAnnot tga=elem;
        while(tga!=null) {
            if(tga.lastToken!=null) {
                TokenAnnot tok=tga.lastToken;
                // can be before elem; then search for the 1st after:
                if(tok.startIdx<elem.endIdx) {
                    while(tok.idx+1<tokens.length) {
                        tok=tokens[tok.idx+1];
                        if(tok.startIdx>elem.endIdx) {
                            ta=tok;
                            break;
                        }
                    }
                }
                // or after elem: then search for the 1st before and then 1 ahead
                else {
                    while(tok.idx>=1) {
                        tok=tokens[tok.idx-1];
                        if(tok.startIdx<elem.endIdx) {
                            ta=tokens[tok.idx+1];
                            break;
                        }
                    }
                    if(ta==null && tok.idx==0)
                        ta=tok;
                }
                break;
            }
            tga=(TagAnnot) tga.parent;
        }
        return ta;
    }
    
    /** Gets the first token before the start of the given element or null if no such element exists. */
    TokenAnnot getTokenBefore(TagAnnot elem) {
        TokenAnnot ta=null;
        TagAnnot tga=elem;
        while(tga!=null) {
            if(tga.firstToken!=null) {
                TokenAnnot tok=tga.firstToken;
                // can be before elem; then search for the 1st after and then 1 back:
                if(tok.startIdx<elem.startIdx) {
                    while(tok.idx+1<tokens.length) {
                        tok=tokens[tok.idx+1];
                        if(tok.startIdx>elem.startIdx) {
                            ta=tokens[tok.idx-1];
                            break;
                        }
                    }
                    if(ta==null && tok.idx==tokens.length-1)
                        ta=tok;
                }
                // or after elem: then search for the 1st before 
                else {
                    while(tok.idx>=1) {
                        tok=tokens[tok.idx-1];
                        if(tok.startIdx<elem.startIdx) {
                            ta=tok;
                            break;
                        }
                    }
                }
                break;
            }
            tga=(TagAnnot) tga.parent;
        }
        return ta;
    }

    public int validate() {
        if(rootElement==null) {
            log.LG(Logger.WRN,"Document has no root element");
            return 0;
        }
        StringBuffer dump=log.IFLG(Logger.TRC)? new StringBuffer(1024): null;
        int err=rootElement.validate(0, dump);
        if(dump!=null)
            log.LG(Logger.TRC,"Document tree:\n"+dump.toString());
        if(err>0)
            log.LG(Logger.ERR,"Found "+err+" errors validating document "+cacheItem.absUrl);
        else
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Document "+cacheItem.absUrl+" validated OK");
        return err;
    }

    public int getNextTokenWithACs(int idx) {
        int rc=-1;
        if(idx<-1)
            idx=-1;
        for(int i=idx+1; i<tokens.length; i++) {
            if(tokens[i].acs!=null) {
                rc=i;
                break;
            }
        }
        return rc;
    }

    public int getPrevTokenWithACs(int idx) {
        int rc=-1;
        if(idx>tokens.length)
            idx=tokens.length;
        for(int i=idx-1; i>=0; i--) {
            if(tokens[i].acs!=null) {
                rc=i;
                break;
            }
        }
        return rc;
    }

    public int getPrevTokenWithACEnds(int idx) {
        int rc=-1;
        if(idx>tokens.length)
            idx=tokens.length;
        for(int i=idx-1; i>=0; i--) {
            AC[] acs=tokens[i].acs;
            if(acs!=null) {
                for(int j=0;j<acs.length;j++) {
                    int ei=acs[j].getEndIdx();
                    if(ei<idx && rc<ei) {
                        rc=ei;
                    }
                }
            }
        }
        return rc;
    }

    public int getACsInRange(int startIdx, int endIdx, Collection<Extractable> acSet) {
        int cnt=0;
        for(int i=startIdx; i<=endIdx; i++) {
            AC[] acs=tokens[i].acs;
            if(acs==null)
                continue;
            for(int j=0; j<acs.length; j++) {
                if(acs[j].getEndIdx() <= endIdx) {
                    acSet.add(acs[j]);
                    cnt++;
                }
            }
        }
        return cnt;
    }

    public int getACs(Collection<Extractable> acSet) {
        return getACsInRange(0, tokens.length-1, acSet);
    }

    public String getACTable() {
        ArrayList<Extractable> acs=new ArrayList<Extractable>(16);
        int cnt=getACs(acs);
        if(cnt==0)
            return "<div>No attribute candidates found!</div>";
        StringBuffer s=new StringBuffer(32*cnt);
        s.append("<table style=\"border-color:black;border-style:solid\"><tr><th>Attribute candidates: "+cnt+"</th></tr>\n");
        for(int i=0;i<cnt;i++) {
            AC ac=(AC) acs.get(i);
            s.append("<tr><td colspan=\"2\">"+ac.toString()+"</td></tr>\n");
        }
        s.append("</table>\n");
        return s.toString();
    }
    
    public void addAC(AC ac) {
        ac.startToken.addAC(ac);
        if(!genExtraInfo)
            return;
        for(int i=0;i<ac.len;i++) {
            tokens[ac.startToken.idx+i].addACPtr(ac);
        }
    }

    public void addAC(List<AC> lst) {
        if(lst==null || lst.size()==0)
            return;
        for(AC ac: lst) {
            ac.startToken.addAC(ac);
        }
        if(!genExtraInfo)
            return;
        for(AC ac: lst) {
            for(int i=0;i<ac.len;i++) {
                tokens[ac.startToken.idx+i].addACPtr(ac);
            }            
        }
    }
    
    /** Add a new SemAnnot starting at this token to this token. */
    public void addSemAnnot(SemAnnot sa) {
        TokenAnnot tok=tokens[sa.startIdx];
        
        // add to annot start list
        if(tok.semAnnots==null)
            tok.semAnnots=new LinkedList<SemAnnot>();
        addToListOrderByLen(tok.semAnnots, sa, (byte)2);
        
        // add to annot presence lists for each containing token 
        for(int i=sa.startIdx; i<=sa.endIdx; i++) {
            if(tokens[i].semAnnotPtrs==null)
                tokens[i].semAnnotPtrs=new LinkedList<SemAnnot>();
            addToListOrderByLen(tokens[i].semAnnotPtrs, sa, (byte)2);
        }
    }

    /** Queues an Annotable into an list ordered by Annotable length (ascending). 
     * If the element is already present, returns false in case of uniq=1 
     * or throws an IllegalArgumentException in case of uniq=2. */
    protected static boolean addToListOrderByLen(List lst, Annotable ann, byte uniq) {
        boolean rc=true;
        ListIterator ait=lst.listIterator();
        int len=ann.getLength();
        while(ait.hasNext()) {
            Annotable ann2=(Annotable) ait.next();
            boolean ins;
            if(uniq!=0) {
                if(ann.equals(ann2)) {
                    if(uniq==2)
                        throw new IllegalArgumentException("Attempt to add same annotable twice; ann="+ann+", existing="+ann2);
                    ait=null;
                    break;
                }
                ins = (len<ann2.getLength());
            }else {
                ins = (len<=ann2.getLength());
            }
            if(ins) {
                ait.previous();
                ait.add(ann);
                ait=null;
                break;
            }
        }
        if(ait!=null)
            ait.add(ann);        
        return rc;
    }
    
    public PatMatch findMatch(TokenPattern pat, ICBase ic) {
        PatMatch pm=null;
        int si=ic.getStartIdx();
        int ei=ic.getEndIdx();
        switch(pat.type) {
        case TokenPattern.PAT_CLS_CTX_L:
            if(si>0) {
                pm=tokens[si-1].findMatch(pat, PatMatch.FIND_END, 1, Integer.MAX_VALUE, null);
            }
            break;
        case TokenPattern.PAT_CLS_CTX_R:
            if(ei+1<tokens.length) {
                pm=tokens[ei+1].findMatch(pat, PatMatch.FIND_START, 1, Integer.MAX_VALUE, null);
            }
            break;
        case TokenPattern.PAT_CLS_CTX_LR: {
            int len=ei-si+1;
            pm=tokens[si].findMatch(pat, PatMatch.FIND_VAL, len, len, null);
            break;
        }
        case TokenPattern.PAT_CLS_VAL:
            PatMatch cand=null;
            for(int i=si;i<=ei;i++) {
                int maxLen=ei-i+1;
                cand=tokens[i].findMatch(pat, PatMatch.FIND_START, 1, maxLen, ic);
                if(cand!=null && (pm==null || cand.getMatchLevel()>pm.getMatchLevel())) {
                    pm=cand; // search to return the best match
                    // break; // finding the first one is enough - the match is boolean
                }
            }
            break;
        case TokenPattern.PAT_CLS_VAL_L: // ^ ( $title | $organization ) ...
            pm=tokens[si].findMatch(pat, PatMatch.FIND_START, 1, ei-si+1, ic);
            break;
        case TokenPattern.PAT_CLS_VAL_R:
            pm=tokens[ei].findMatch(pat, PatMatch.FIND_END, 1, ei-si+1, ic);
            break;
        case TokenPattern.PAT_CLS_VAL_LR: {
            int len=ei-si+1;
            pm=tokens[si].findMatch(pat, PatMatch.FIND_START, len, len, ic);
            break;
        }
        default:
            log.LG(Logger.ERR,"Unknown type of pattern: "+pat.type);
            break;
        }

        return pm;
    }

    public static int addMatch(PatMatch match, TokenAnnot[] tokens, int startIdx) {
        int ei=startIdx+match.getLength()-1;
        if(startIdx>=tokens.length || ei>=tokens.length || match.getLength()==0) {
            throw new IllegalArgumentException("Cannot addMatch ["+startIdx+","+ei+"] to tokens cnt="+tokens.length);
        }
        if(tokens[startIdx].matchStarts==null) {
            tokens[startIdx].matchStarts=new ArrayList<PatMatch>(2);
        }
        tokens[startIdx].matchStarts.add(match);
        if(tokens[ei].matchEnds==null) {
            tokens[ei].matchEnds=new ArrayList<PatMatch>(2);
        }
        tokens[ei].matchEnds.add(match);
        return 1;
    }

    /** Returns all segments in document which are subordinated to the segment 
     * specified by startIdx and len. Typically this is the immediately following segment
     * or, for table headers, all segments in a column or row.
     * @param startIdx start (token index) of the master segment
     * @param len length (in tokens) of the master segment
     * @param relatedSegments list to be filled with subordinated segments
     * @return count of the added segments
     */
    public int getRelatedSegments(int startIdx, int len, ArrayList<DocSegment> relatedSegments) {
        int nextTokenIdx=startIdx+len;
        if(nextTokenIdx>=tokens.length)
            return 0;
        int cnt=0;

        // 'follower' is now created outside this function after observing whether any other related segments matched:
        // the immediately following segment of unspecified length will be added 
        // as the last one depending on what else was added
        // DocSegment follower=new DocSegment(startIdx+len, -1);

        // detect any blocks *under* commonParent of the source segment
        TagAnnot commonParent=getParentBlock(tokens[startIdx], tokens[startIdx+len-1], -1);
        while(commonParent!=null) {
            boolean found=false;
            switch(commonParent.getTagType()) {
            case TagTypeF.BLOCK:
            case TagTypeF.CONTAINER:
            case TagTypeF.HEADING:
            case TagTypeF.A:
                found=true;
            }
            if(found)
                break;
            commonParent=(TagAnnot) commonParent.parent;
        }
        LinkedList<TagAnnot> interimBlocks=new LinkedList<TagAnnot>();
        TagAnnot lastParent=null;
        for(int i=0; i<len; i++) {
            TokenAnnot ta=tokens[startIdx+i];
            TagAnnot tga=(TagAnnot) ta.parent;
            if(tga==lastParent)
                continue;
            lastParent=tga;
            TagAnnot interBlock=null;
            while(tga!=null && tga!=commonParent) {
                switch(tga.getTagType()) {
                case TagTypeF.BLOCK:
                    switch(tga.type) {
                    case HTMLElements.TD:
                    case HTMLElements.TH:
                        if(interBlock==null) {
                            interBlock=tga;
                        }else {
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Found more interim blocks for token "+ta+": previous="+interBlock+" current="+tga+"; quitting");
                            tga=null;
                        }
                        break;
                    default:
                        tga=null;
                    }
                    break;
                case TagTypeF.CONTAINER:
                    tga=null;
                    break;
                }
                if(tga!=null)
                    tga=(TagAnnot) tga.parent;
            }
            if(tga==null) {
                // relatedSegments.add(follower);
                // return 1;
                return 0;
            }
            if(interBlock!=null && !interimBlocks.contains(interBlock))
                interimBlocks.add(interBlock);
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Generating related segments interimBlocks count="+interimBlocks.size());
        
        // if the source segment comes from a TH cell or from a TD within the 1st TR in a table
        // and the segment takes up significant part of the cell, then
        // include all cells from the corresponding column in relatedSegments
        int cellCnt=0;
        TagAnnot tga=commonParent;
        while(tga!=null) {
            switch(tga.getTagType()) {
            case TagTypeF.BLOCK:
            case TagTypeF.CONTAINER:
                switch(tga.type) {
                case HTMLElements.TD:
                case HTMLElements.TH:
                    TagAnnot tr=(TagAnnot) tga.parent;
                    if(tr==null||tr.type!=HTMLElements.TR) {
                        log.LG(Logger.WRN,"Table cell out of table row: "+tga);
                        break;
                    }
                    if(tr.parentIdx!=0) { // source cell must be in 1st row
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"getRelatedSegments discarded table cell with row idx="+tr.parentIdx+" cell="+tga);
                        break;
                    }
                    TagAnnot col=tga.getParentBlock(1); // get alternative parent block - column (row would be 0)
                    for(int i=0;col!=null && i<col.childNodes.length;i++) {
                        if(i==tr.parentIdx)
                            continue; // don't include master segment itself
                        TagAnnot cell=(TagAnnot) col.childNodes[i];
                        TokenAnnot t1=cell.firstToken;
                        TokenAnnot t2=cell.lastToken;
                        if(t1==null || t2==null) {
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"getRelatedSegments not including empty column cell="+tga+" row="+tr);
                            continue;
                        }
                        relatedSegments.add(new DocSegment(t1.idx, t2.idx-t1.idx+1));
                        cellCnt++;
                    }
                    break;
                case HTMLElements.TR:
                    if(tga.parentIdx!=0) { // source cell must be in 1st row
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"getRelatedSegments discarded table multicell with row idx="+tga.parentIdx+" row="+tga);
                        break;
                    }
                    if(interimBlocks.size()<=1) { // should contain at least 2 cells which form a horizontal multicell (TR is commonParent)
                        log.LG(Logger.WRN,"getRelatedSegments discarded table multicell of size="+interimBlocks.size()+" row="+tga);
                        break;
                    }
                    TagAnnot startCol=interimBlocks.getFirst().getParentBlock(1);
                    TagAnnot endCol=interimBlocks.getLast().getParentBlock(1);
                    if(startCol==null || endCol==null) {
                        log.LG(Logger.WRN,"getRelatedSegments could not get start and end columns for table multicell of size="+interimBlocks.size()+" row="+tga);
                        break;
                    }
                    int maxLen=Math.min(startCol.childNodes.length, endCol.childNodes.length);
                    for(int i=0;i<maxLen;i++) {
                        if(i==tga.parentIdx)
                            continue; // don't include master segment itself
                        TagAnnot startCell=(TagAnnot) startCol.childNodes[i];
                        TagAnnot endCell=(TagAnnot) endCol.childNodes[i];
                        TokenAnnot t1=startCell.firstToken;
                        TokenAnnot t2=endCell.lastToken;
                        if(t1==null)
                            t1=endCell.firstToken;
                        if(t2==null)
                            t2=startCell.lastToken;
                        if(t1==null || t2==null) {
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"getRelatedSegments not including empty multicell of size="+interimBlocks.size()+" row="+tga);
                            continue;
                        }
                        relatedSegments.add(new DocSegment(t1.idx, t2.idx-t1.idx+1));
                        cellCnt++;
                    }
                    break;
                } // switch(tga.type)
                tga=null;
                break;
            } // switch(tga.getTagType())
            
            if(tga!=null)
                tga=(TagAnnot) tga.parent;
        } // while(tga!=null)

        cnt+=cellCnt;

        //if(cellCnt<=1) {
        //    relatedSegments.add(follower);
        //    cnt++;
        
        // if commonParent is a TD in the 1st column and it contains <BR>s
        // then attempt to find the same number of <BR>s in the next TD to the right.
        // If found, add the corresponding segment between matching <BR>s into relatedSegments.
        if(commonParent!=null && 
                (commonParent.type==HTMLElements.TD || commonParent.type==HTMLElements.TH)) {
            ArrayList<TagAnnot> brList=new ArrayList<TagAnnot>(16);
            int brc=getChildTags(brList, commonParent, HTMLElements.BR, (short)-1, true);
            int subCellIdx=-1; // there are brc+1 cells, find if the source segment belongs to 1
            if(brc>0) {
                int charStart=tokens[startIdx].startIdx;
                int charEnd=tokens[startIdx+len-1].endIdx;
                int lastSegStart=commonParent.startIdxInner;
                for(int i=0;i<brc;i++) {
                    if(charStart > lastSegStart && charStart < brList.get(i).startIdx) {
                        if(charEnd > lastSegStart && charEnd < brList.get(i).startIdx) {
                            subCellIdx=i;
                        }
                        break;
                    }
                    lastSegStart=brList.get(i).endIdx;
                }
                if(subCellIdx==-1) {
                    if(charStart > lastSegStart && charStart < commonParent.endIdxInner) {
                        if(charEnd > lastSegStart && charEnd < commonParent.endIdxInner) {
                            subCellIdx=brc;
                        }
                    }
                }
            }
            if(subCellIdx!=-1) {
                // find the TD to the right and search for corresponding segment
                TagAnnot row=(TagAnnot) commonParent.parent;
                if(row.type==HTMLElements.TR) {
                    if(commonParent.parentIdx+1 < row.childNodes.length) {
                        TagAnnot nextTD=(TagAnnot) row.childNodes[commonParent.parentIdx+1];
                        if(nextTD.type==HTMLElements.TD) {
                            // check if the TD also contains BRs
                            ArrayList<TagAnnot> brList2=new ArrayList<TagAnnot>(16);
                            int brc2=getChildTags(brList2, nextTD, HTMLElements.BR, (short)-1, true);
                            if(brc2>=brc) {
                                TokenAnnot ta1=(subCellIdx==0)?
                                        nextTD.firstToken: getTokenAfter(brList2.get(subCellIdx-1));
                                TokenAnnot ta2=(subCellIdx==brc)?
                                        nextTD.lastToken: getTokenBefore(brList2.get(subCellIdx));
                                if(ta1!=null && ta2!=null) {
                                    DocSegment subSeg=new DocSegment(ta1.idx, ta2.idx-ta1.idx+1);
                                    relatedSegments.add(subSeg);
                                    cnt++;
                                }
                            }
                            // otherwise, check if the TD contains another brc blocks with text
                            else {
                                brList2.clear();
                                short[] tagNames={HTMLElements.H1,HTMLElements.H2,HTMLElements.H3,
                                        HTMLElements.P,HTMLElements.DIV};
                                brc2=getChildTagsWithTokens(brList2, nextTD, tagNames);
                                if(brc2>brc) { // n BRs must be mapped to (n+1) elements
                                    TokenAnnot ta1=brList2.get(subCellIdx).firstToken;
                                    TokenAnnot ta2=brList2.get(subCellIdx).lastToken;
                                    DocSegment subSeg=new DocSegment(ta1.idx, ta2.idx-ta1.idx+1);
                                    relatedSegments.add(subSeg);
                                    cnt++;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return cnt;
    }
    
    /** Fills count array of: [
        crossed block tags{0-2},
        crossed inline tags{0-2},
        1 if all tokens have the same parent, 0 otherwise,
        1 if value fits exactly into parent tag, 0 otherwise ] */
    public void fillCrossTagCounts(int startIdx, int endIdx, int[] counts) {
        TokenAnnot startToken=tokens[startIdx];
        TokenAnnot endToken=tokens[endIdx];
        TagAnnot tga1=(TagAnnot) startToken.parent;
        TagAnnot tga2=(TagAnnot) endToken.parent;
        int idx1=(tga1.getTagType()==TagTypeF.BLOCK)? 0: 1;
        int idx2=(tga2.getTagType()==TagTypeF.BLOCK)? 0: 1;

        for(int i=0;i<counts.length;i++) {
            counts[i]=0;
        }

        // before and after
        TagAnnot tga0=(startToken.idx>0)? ((TagAnnot) tokens[startToken.idx-1].parent): null;
        TagAnnot tga3=(endToken.idx<tokens.length-1)? ((TagAnnot) tokens[endToken.idx+1].parent): null;

        // crossed tags counts
        boolean openLeft=(tga1==tga0); // tga1.hasDescendant(tga0) || tga0.hasDescendant(tga1); // 'crossed' only applies to immediate parent
        boolean openRight=(tga2==tga3);
        if(!tga1.hasDescendant(tga2) && !tga2.hasDescendant(tga1)) { // incl equality
            if(openLeft)
                counts[idx1]++;
            if(openRight)
                counts[idx2]++;
        }

        // does value fit exactly in parent?    
        TagAnnot par=getParentBlock(startToken, endToken, -1);
        if(par!=null && (tga0==null || !par.hasDescendant(tga0))
                && (tga3==null || !par.hasDescendant(tga3))) {
            counts[2]=1;
        }

        // do all tokens have the same parent (i.e. value does not interfere with tags)?
        counts[3]=1;
        for(int i=startToken.idx+1; i<=endToken.idx; i++) {
            if(tokens[i].parent!=tga1) {
                counts[3]=0;
                break;
            }
        }

        // exceptions: 
        // 1. <br> tag is considered a splitter between 2 blocks
        if(par!=null) {
            List<TagAnnot> tags=new LinkedList<TagAnnot>();
            int cnt=par.getChildTags(true, tags, HTMLElements.BR);
            if(cnt>0) {
                counts[2]=0;
                counts[3]=0;
            }
        }
    }

    public TokenAnnot getTokenByStartPos(int spos) {
        TokenAnnot tok=null;
        int i=0;
        for( ;i<tokens.length;i++) {
            if(tokens[i].startIdx>=spos)
                break;
        }
        if(i==tokens.length) 
            tok=tokens[tokens.length-1];
        else if(tokens[i].startIdx!=spos && i>0) {
            // check whether the previous token would be a better match
            tok=((spos-tokens[i-1].startIdx) < (tokens[i].startIdx-spos))?
                    tokens[i-1]: tokens[i];
        }else {
            tok=tokens[i];
        }
        return tok;
    }
    
    public TokenAnnot getTokenByEndPos(int epos) {
        TokenAnnot tok=null;
        int i=0;
        for( ;i<tokens.length;i++) {
            if(tokens[i].endIdx>=epos)
                break;
        }
        if(i==tokens.length) 
            tok=tokens[tokens.length-1];
        else if(tokens[i].startIdx!=epos && i>0) {
            // check whether the previous token would be a better match
            tok=((epos-tokens[i-1].endIdx) < (tokens[i].endIdx-epos))?
                    tokens[i-1]: tokens[i];
        }else {
            tok=tokens[i];
        }
        return tok;
    }
    
    public boolean isClassified(String clsName, double minProb) {
        boolean rc=false;
        clsName=clsName.trim().toLowerCase();
        for(DocumentClass dc: classifications) {
            if(dc.prob>=minProb && 
               (dc.name.equals(clsName) || dc.name.startsWith(clsName+"_"))) {
                // e.g. document classified as "contact_about" will match query "contact"
                rc=true;
                break;
            }
        }
        return rc;
    }
}

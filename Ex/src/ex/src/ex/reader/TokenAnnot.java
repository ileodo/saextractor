// $Id: TokenAnnot.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import java.util.*;

import uep.util.Logger;

import ex.util.Const;
import ex.util.IntTrieItem;
import ex.train.TokenInfo;
import ex.train.AbstractToken;
import ex.train.Vocab;
import ex.ac.PatMatch;
import ex.ac.PatSubMatch;
import ex.ac.AC;
import ex.ac.Annotable;
import ex.ac.TokenPattern;
import ex.model.AttributeDef;
import ex.parser.GState;
import ex.parser.ICBase;

// created on a Document by Tokenizer
public class TokenAnnot extends Annot implements AbstractToken, IntTrieItem {
    /** index into Document's array of TokenAnnots (to access siblings) */
    public int idx;

    /** token string as found in document. TODO: delete this if we confirm this is always the same
     * as ti.token. Currently yes but we keep token temporarily until ti is set. */
    public String token;
    
    /** vocabulary item that holds all token properties that describe its content */
    public TokenInfo ti;

    /** all pattern matches starting at this token */
    public List<PatMatch> matchStarts;
    /** all pattern matches ending at this token */
    public List<PatMatch> matchEnds;

    /** all attribute candidates starting at this token */
    public AC[] acs;
    /** Pointers to all ACs this token is member of; 
     * this is convenience on top of the acs array */
    public Set<AC> acPtrs;

    /** list of 3rd party annotations which start here */
    public List<SemAnnot> semAnnots;
    /** list of 3rd party annotations which this token is part of */
    public List<SemAnnot> semAnnotPtrs;

    /** states corresponding to this token in AC lattice; this is null or
     *  it contains at least the background state. */
    public List<GState> acStates;
    public GState precBgNullState; // could also be part of acStates but...
    
    /** tag list holding all formatting tags which start just before this token, e.g. 
     * 2 tags for <a><b>hello ... Is null if no such tags exist. */
    public List<TagAnnot> tagStarts;
    /** tag list holding all formatting tags which end just after this token, e.g. 
     * 2 tags for hello</b></a> ... Is null if no such tags exist. */
    public List<TagAnnot> tagEnds;
    
    /** general purpose data - used by LR parser to anchor trellis bottom (PTTs) */
    public Object userData;

    // for having acs sorted ascending by their length
    public static Comparator<Annotable> cmpLabelsByLen=new Comparator<Annotable>() { 
        public int compare(Annotable o1, Annotable o2) { 
            return o1.getLength() - o2.getLength();
        }
    };
    
    public TokenAnnot(int t, int start, int end, Annot par, int parIdx, int i, String val) {
        super(t, start, end, par, parIdx);
        annotType=ANNOT_TOKEN;
        token=val;
        idx=i;
        matchStarts=null;
        matchEnds=null;
        semAnnots=null;
        semAnnotPtrs=null;
        tagStarts=null;
        tagEnds=null;
        acs=null;
        acPtrs=null;
        ti=null;
    }

    public void clear() {
        userData=null;
        if(tagStarts!=null) { tagStarts.clear(); tagStarts=null; }
        if(tagEnds!=null) { tagEnds.clear(); tagEnds=null; }
        if(acStates!=null) { acStates.clear(); acStates=null; }
        if(semAnnotPtrs!=null) { semAnnotPtrs.clear(); semAnnotPtrs=null; }
        if(semAnnots!=null) { semAnnots.clear(); semAnnots=null; }
        if(acPtrs!=null) { acPtrs.clear(); acPtrs=null; }
        if(matchStarts!=null) { matchStarts.clear(); matchStarts=null; }
        if(matchEnds!=null) { matchEnds.clear(); matchEnds=null; }
        ti=null;
        token=null;
        precBgNullState=null;
        super.clear();
    }
    
    public TokenAnnot next(Document d) {
        if(idx+1 < d.tokens.length)
            return d.tokens[idx+1];
        return null;
    }

    public TokenAnnot prev(Document d) {
        if(idx > 0)
            return d.tokens[idx-1];
        return null;
    }

    public String getToken() {
        return (token!=null)? token: ((ti!=null)? ti.token: null);
    }

    public int setFeatures(Vocab vocab) {
        // find token in vocab and load its features
        TokenInfo tokInfo=vocab.get(token);
        if(tokInfo!=null) {
            ti=tokInfo;
            token=tokInfo.token; // dispose of the String from tokenizer
            return Const.EX_OK;
        }

        // if not found, add new TokenInfo and compute its feature values
        tokInfo=new TokenInfo(token, type);
        int rc=tokInfo.computeFeatures(vocab, true);
        ti=tokInfo;
        return rc;
    }

    public String toString() {
        String s="'"+token+"' ["+startIdx+","+endIdx+"] (";
        if(ti!=null)
            s+=ti.toString();
        s+=")";
        return s;
    }

    public AC findAC(AttributeDef ad, boolean findBest) {
        return findAC(ad, findBest, -1);
    }
    
    public AC findAC(AttributeDef ad, boolean findBest, int len) {
        if(acs==null)
            return null;
        AC ac=null;
        for(int i=0;i<acs.length;i++) {
            if(acs[i].obj==ad && (len==-1 || acs[i].len==len)) {
                if(!findBest) {
                    ac=acs[i];
                    break;
                }else {
                    if(ac==null || ac.getProb()<acs[i].getProb()) {
                        ac=acs[i];
                    }
                }
            }
        }
        return ac;
    }

    public void addAC(AC ac) {
        if(acs==null) {
            acs=new AC[1];
        }else {
            AC[] old=acs;
            acs=new AC[old.length+1];
            System.arraycopy(old,0,acs,0,old.length);
        }
        acs[acs.length-1]=ac;
        Arrays.sort(acs, cmpLabelsByLen);
    }

    public void addAC(List<AC> lst) {
        int idx=0;
        int len=lst.size();
        if(acs==null) {
            acs=new AC[len];
        }else {
            AC[] old=acs;
            acs=new AC[old.length+len];
            System.arraycopy(old,0,acs,0,old.length);
            idx=old.length;
        }
        for(int i=0;i<len;i++) {
            acs[idx+i]=lst.get(i);
        }
        Arrays.sort(acs, cmpLabelsByLen);
    }

    public void removeAC(AC ac) {
        if(acs==null)
            return;
        int idx=-1;
        for(int i=0;i<acs.length;i++) {
            if(acs[i]==ac) {
                idx=i;
                break;
            }
        }
        if(idx>0) {
            AC[] na=new AC[acs.length-1];
            if(idx>0)
                System.arraycopy(acs, 0, na, 0, idx);
            if(idx<acs.length-1)
                System.arraycopy(acs, idx+1, na, idx, acs.length-idx-1);
        }
    }
    
    
    private static final Comparator<AC> acPtrComparator = new Comparator<AC>() {
        public int compare(AC o1, AC o2) {
            // keeps ACs that start here as the first ones, references into the middle of ACs next, end references last
            int rc=o2.startToken.idx-o1.startToken.idx;
            if(rc==0)
                rc=o1.len-o2.len; // sort by length ACs with common start
            return rc;
        }
    };
    public boolean addACPtr(AC ac) {
        if(acPtrs==null) {
            acPtrs=new TreeSet<AC>(acPtrComparator);
        }
        //ACPtr ap=new ACPtr(ac, this.idx - ac.startToken.idx);
        //if(acPtrs.contains(ap))
        if(acPtrs.contains(ac))
            return false;
        //acPtrs.add(ap);
        acPtrs.add(ac);
        return true;
    }

    public int getAnnotablesCnt(boolean incPatterns, boolean incACs) {
        return (incPatterns? ((matchStarts==null)? 0: matchStarts.size()): 0) + (incACs? ((acs==null)? 0: acs.length): 0);
    }

    public Annotable getAnnotable(int idx, boolean incPatterns, boolean incACs) {
        int len1=(!incPatterns || matchStarts==null)? 0: matchStarts.size();
        if(idx<len1)
            return matchStarts.get(idx);
        return acs[idx-len1];
    }
    
    /** Adds all pattern matches and/or ACs starting at this token. */
    public int getAnnotables(Collection<Annotable> annotables, boolean incPatterns, boolean incACs) {
        int cnt=0;
        if(incPatterns && matchStarts!=null)
            for(int i=0;i<matchStarts.size();i++) {
                annotables.add(matchStarts.get(i));
                cnt++;
            }
        if(incACs && acs!=null)
            for(int i=0;i<acs.length;i++)
                if(acs[i]!=null) {
                    annotables.add(acs[i]);
                    cnt++;
                }
        return cnt;
    }

    // IntTrieItem
    public int getTrieKey() {
        return ti.getTrieKey();
    }

    // AbstractToken
    public int getTokenId() {
        return ti.getTokenId();
    }
    public int getLemmaId() {
        return ti.getLemmaId();
    }
    public int getLCId() {
        return ti.getLCId();
    }
    public int getUnaccentedId() {
        return ti.getUnaccentedId();
    }
    public int getMostGeneralId() {
        return ti.getMostGeneralId();
    }

    public PatMatch findMatch(TokenPattern pat, int findMode, int minLen, int maxLen, ICBase ic) {
        List<PatMatch> matches=(findMode==PatMatch.FIND_END)? this.matchEnds: this.matchStarts;
        if(matches==null)
            return null;
        PatMatch pm=null;
        for(int i=0;i<matches.size();i++) {
            PatMatch pmc=matches.get(i);
            TokenPattern pc=pmc.pat;
            if(findMode==PatMatch.FIND_VAL) {
                if(!(pmc instanceof PatSubMatch))
                    continue;
                pmc=((PatSubMatch) pmc).parent;
                pc=pmc.pat;
            }
            if(pmc.len<minLen || pmc.len>maxLen) {
                continue;
            }
            if(pc!=pat) {
                continue;
            }
            if(ic!=null && !ic.containsAllACs(pmc)) {
                continue;
            }
            pm=pmc;
            break;
        }
        return pm;
    }

    public void addTagStart(TagAnnot ta) {
        if(tagStarts==null) {
            tagStarts=new ArrayList<TagAnnot>(4);
        }
        tagStarts.add(ta);
    }
    
    public void addTagEnd(TagAnnot ta) {
        if(tagEnds==null) {
            tagEnds=new ArrayList<TagAnnot>(4);
        }
        tagEnds.add(ta);        
    }

    /** Returns true when this token is contained within the specified ancestor tag. */
    public boolean hasAncestor(TagAnnot ancTag) {
        TagAnnot tag=(TagAnnot) parent;
        while(tag!=null) {
            if(tag==ancTag)
                return true;
            tag=(TagAnnot) tag.parent;
        }
        return false;
    }
}

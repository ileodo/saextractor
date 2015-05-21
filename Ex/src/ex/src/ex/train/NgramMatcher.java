// $Id: NgramMatcher.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.Arrays;
import java.util.List;

import uep.util.Logger;
import ex.ac.Annotable;
import ex.features.TokenNgramF;
import ex.model.ModelElement;
import ex.reader.Document;

public class NgramMatcher {
    Document doc;
    static Logger log;
    public NgramMatcher(Document doc) {
        if(log==null) {
            log=Logger.getLogger("ngm");
        }
        this.doc=doc;
    }
    
    /** Populates NGramFeature matches for each token of the current document. */
    public NFInfo[] match(NgramFeatureBook fnBook) {
        // first, fill in starts of matches of known ngram features
        fnBook.setLookupMode(PhraseBook.MATCH_EXACT);
        NBestResult res=new NBestResult(100);
        NFInfo[] nfMatches = new NFInfo[doc.tokens.length];
        int[] endCnts=new int[doc.tokens.length];
        for(int i=0;i<doc.tokens.length;i++) {
            int rc=fnBook.get(doc.tokens, i, -1, true, res);
            switch(rc) {
            case PhraseBook.NOMATCH:
                // none of the known ngram features begins with this token
                break;
            case PhraseBook.MATCH_EXACT:
                // will not happen since we do not specify length (-1)
                break;
            case PhraseBook.MATCH_PREFIX:
                NFInfo nfi=new NFInfo(res);
                nfMatches[i]=nfi;
                for(int j=0;j<nfi.matchStarts.length;j++) {
                    NFInfoMatch match=nfi.matchStarts[j];
                    endCnts[i+match.len-1]++;
                }
                break;
            }
            if(endCnts[i]>0) {
                NFInfo endNfi=nfMatches[i];
                if(endNfi==null) {
                    endNfi=new NFInfo();
                    nfMatches[i]=endNfi;
                }
                endNfi.matchEnds=new NFInfoMatch[endCnts[i]];
            }
        }
        // fill in match ends
        Arrays.fill(endCnts, 0);
        for(int i=0;i<doc.tokens.length;i++) {
            NFInfo nfi=nfMatches[i];
            if(nfi!=null && nfi.matchStarts!=null) {
                for(int j=0;j<nfi.matchStarts.length;j++) {
                    NFInfoMatch match=nfi.matchStarts[j];
                    int endIdx=i+match.len-1;
                    nfMatches[endIdx].matchEnds[endCnts[endIdx]++]=match;
                }
            }
        }
        // logging only
        if(log.IFLG(Logger.INF)) {
            StringBuffer sb=new StringBuffer(doc.tokens.length*8);
            for(int i=0;i<doc.tokens.length;i++) {
                sb.append("\n"+doc.tokens[i].token);
                if(nfMatches[i]!=null) {
                    if(nfMatches[i].matchStarts!=null) {
                        sb.append("\n starts: ");
                        int j=0;
                        for(NFInfoMatch nfim: nfMatches[i].matchStarts) {
                            if(j>0) { sb.append(" "); }
                            sb.append(++j+":len="+nfim.len+"[");
                            int k=0;
                            for(TokenNgramF f: nfim.feats) {
                                if(k>0) { sb.append(" "); }
                                sb.append(++k+":"+f);
                            }
                            sb.append("]");
                        }                        
                    }
                    if(nfMatches[i].matchEnds!=null) {
                        sb.append("\n ends: ");
                        int j=0;
                        for(NFInfoMatch nfim: nfMatches[i].matchEnds) {
                            if(j>0) { sb.append(" "); }
                            sb.append(++j+":len="+nfim.len+"[");
                            int k=0;
                            for(TokenNgramF f: nfim.feats) {
                                if(k>0) { sb.append(" "); }
                                sb.append(++k+":"+f);
                            }
                            sb.append("]");
                        }                        
                    }
                }
            }
            log.LG(Logger.INF,"setNgramFeatures: "+sb);
        }
        return nfMatches;
    }
}

/** Holds TokenNgramF match information. */
class NFInfo {
    public NFInfoMatch[] matchStarts;
    public NFInfoMatch[] matchEnds;

    public NFInfo() {
        matchStarts=null;
        matchEnds=null;
    }
    public NFInfo(NBestResult res) {
        matchStarts=new NFInfoMatch[res.length];
        for(int i=0;i<res.length;i++) {
            matchStarts[i]=new NFInfoMatch((List<TokenNgramF>) res.data[i], ((GenericPhrase) res.items[i]).getLength());   
        }
        matchEnds=null;
    }
}

class NFInfoMatch {
    public List<TokenNgramF> feats;
    public int len; 

    public NFInfoMatch(List<TokenNgramF> feats, int len) {
        this.feats=feats;
        this.len=len;
    }    
}

class NgramLabel implements Annotable {
    int startIdx;
    int length;
    TokenNgramF feature;
    
    public NgramLabel(int startIdx, int length, TokenNgramF feature) {
        this.startIdx=startIdx;
        this.length=length;
        this.feature=feature;
    }
    
    public int getLength() {
        return length;
    }

    public ModelElement getModelElement() {
        return null;
    }

    public double getProb() {
        return 0;
    }

    public int getStartIdx() {
        return startIdx;
    }

    public int getType() {
        return TYPE_LABEL;
    }
}

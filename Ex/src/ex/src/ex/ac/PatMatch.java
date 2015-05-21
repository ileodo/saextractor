// $Id: PatMatch.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

import uep.util.Logger;
import ex.model.AttributeDef;
import ex.reader.Document;

public class PatMatch implements Annotable {
    public static final int FIND_START=1;
    public static final int FIND_END=2;
    public static final int FIND_VAL=3;
    public TokenPattern pat;
    public int startIdx;
    public int len;
    public Annotable[] children;
    public double matchLevel;

    public PatMatch(TokenPattern pat, int startIdx, int len) {
        this.pat=pat;
        this.startIdx=startIdx;
        this.len=len;
        this.matchLevel=1.0;
    }

    public void addChildren(List<Annotable> matches) {
        int cnt=matches.size();
        children=new Annotable[cnt];
        for(int i=0;i<cnt;i++) {
            Annotable an=matches.get(i);
            children[i]=an;
        }
    }
    
    public double computeMatchLevel() {
        double wsum=0.0;
        int remainLen=len;
        for(int i=0;i<children.length;i++) {
            Annotable an=children[i];
            wsum=an.getProb()*an.getLength(); // Annotable with only limited probability, length in tokens 
            remainLen-=an.getLength();
        }
        wsum+=remainLen; // assuming weight 1 for non-submatches
        matchLevel=wsum/len;
        if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC, this.toString());
        return matchLevel;
    }

    public int getLength() { return len; }

    public int getStartIdx() { return startIdx; }

    public int getType() {
        return pat.type;
    }

    public double getProb() {
        return pat.evidence.prec;
    }

    public double getMatchLevel() {
        return matchLevel;
    }

    public AttributeDef getModelElement() {
        return pat.getAttributeDef();
    }

    public String toString() {
        StringBuffer b=new StringBuffer(64);
        b.append(pat+"["+startIdx+","+len+"]");
        if(children!=null && children.length>0) {
            b.append(":");
            for(int i=0;i<children.length;i++) {
                Annotable an=children[i];
                if(i>0) b.append(",");
                if(an instanceof AC)
                    ((AC)an).toString(b);
                else
                    b.append(((PatSubMatch)an).toString());
            }
        }
        if(matchLevel!=1) {
            b.append(";ml="+matchLevel);
        }
        return b.toString();
    }
    
    public String toString(Document doc) {
        return pat+"("+Document.toString(doc.tokens, startIdx, len, " ")+")";
    }
}

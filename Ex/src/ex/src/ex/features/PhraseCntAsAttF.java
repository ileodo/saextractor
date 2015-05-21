package ex.features;

import uep.util.Logger;
import ex.model.*;
import ex.train.*;

/* This class defines features:
- for each attribute from model incl. garbage,
- for each occurance (content,left context,right context),
- for each matchLevel (exact, lemma, pattern_id)

These features store:
- cnt of described phrase/pattern seen as attribute attDef,
- cnt seen outside of any attribute (if attDef==Model.garbageAtt),
- total cnt seen (if attDef==null)
*/

public class PhraseCntAsAttF extends PhraseF implements EnumFeature {
    public AttributeDef attDef;
    public PhraseBook book;
    /* occurence:
       0 - attribute content,
       1 - att left context,
       2 - att right context
     */
    public int occurence;
    /* matchLevel:
       0 - exact match with one of attribute's training phrases,
       1 - lemma match,
       2..N - match with i-th (content/L-ctx/R-ctx) pattern in attDef
     */
    public int matchLevel;

    public PhraseCntAsAttF(int featureId, AttributeDef att) {
        super(featureId, att.name, VAL_INT);
        attDef=att;
    }

    public String toString(int val) {
        return String.valueOf(val);
    }

    public int fromString(String val) {
        try {
            return Integer.parseInt(val);
        }catch(NumberFormatException ex) {
            Logger.LOGERR("Cannot parse feature value: "+ex.toString());
        }
        return -1;
    }

    public int valueOf(TokenInfo[] toks) { 
        NBestResult nbr=new NBestResult(1);
        int rc=book.get(toks, nbr);
        if(nbr.length==0)
            return 0;
        return ((PhraseInfo) nbr.items[0]).intValues[id];
    }
}

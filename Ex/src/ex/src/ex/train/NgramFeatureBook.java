// $Id: NgramFeatureBook.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import ex.features.TokenNgramF;

public class NgramFeatureBook extends NgramBook<FeatureListForNgram, List<TokenNgramF>> {
    List<TokenNgramF> features;
    Set<TokenNgramF> fset;
    
    public NgramFeatureBook(String name) {
        super(name, new FeatureBookValueAdapter());
        ((FeatureBookValueAdapter) this.valueAdapter).setBook(this);
        features=new ArrayList<TokenNgramF>(128);
        fset=new HashSet<TokenNgramF>(128);
    }
    
    public byte getType() {
        return TYPE_NGRAM_FEATURE;
    }

    public boolean addFeature(TokenNgramF f) {
        boolean rc=false;
        if(!fset.contains(f)) {
            fset.add(f);
            features.add(f);
            rc=true;
        }
        return rc;
    }
    
    public String toString() {
        return "feat"+super.toString();
    }
}

class FeatureListForNgram extends ArrayList<TokenNgramF> {
    private static final long serialVersionUID = 2974222792464581707L;
    public FeatureListForNgram(int cap) {
        super(cap);
    }
    public FeatureListForNgram(Collection<TokenNgramF> orig) {
        super(orig);
    }
    public String toString() {
        int sz=size();
        StringBuffer sb=new StringBuffer(sz*5);
        for(int i=0;i<sz;i++) {
            if(i>0)
                sb.append(',');
            sb.append(get(i).localId);
        }
        return sb.toString();
    }
}

class FeatureBookValueAdapter implements PhraseBookValueAdapter<FeatureListForNgram, List<TokenNgramF>> {
    NgramFeatureBook book;
    
    public void setBook(NgramFeatureBook book) {
        this.book=book;
    }

    public FeatureListForNgram addValue(FeatureListForNgram oldValue, List<TokenNgramF> newValue, NgramCounts bookCounts) {
        FeatureListForNgram retVal;
        // register unknown features
        Iterator<TokenNgramF> fit=newValue.iterator();
        while(fit.hasNext()) {
            TokenNgramF f=fit.next();
            book.addFeature(f);
        }
        // merge feature lists, update counts
        if(oldValue==null) {
            if(newValue!=null) {
                bookCounts.occCnt+=1;
                retVal=new FeatureListForNgram(newValue);
            }else {
                retVal=null;
            }
        }else {
            if(newValue==null) {
                bookCounts.occCnt-=1;
                retVal=null;
            }else {
                oldValue.addAll(newValue);
                retVal=oldValue;
            }
        }
        return retVal;
    }
}

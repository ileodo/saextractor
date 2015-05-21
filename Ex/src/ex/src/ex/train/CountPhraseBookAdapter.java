// $Id: CountPhraseBookAdapter.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import ex.features.TokenNgramF;
import ex.train.NgramInfo.LLI;
import ex.train.NgramInfo.LLI2;

/** Adapter class for NgramBooks to accumulate ngram occurrence counts.
 *  Only copies values from the counts supplied as newValue. */
public class CountPhraseBookAdapter implements PhraseBookValueAdapter<NgramInfo, NgramInfo> {
    public NgramInfo addValue(NgramInfo oldValue, NgramInfo newValue, NgramCounts bookCounts) {
        if(newValue==null) {
            if(oldValue!=null) {
                bookCounts.occCnt-=oldValue.getOccCnt();
                oldValue=null; // deletes all stats for the ngram
            }
        }else {
            // update total ngram occurence count and occurence count 
            // for each attribute the value of which is equal to this ngram
            bookCounts.occCnt+=newValue.getOccCnt();
            LLI li=newValue.elems;
            while(li!=null) {
                LLI2 li2=li.counts;
                while(li2!=null) {
                    if(li2.pos==TokenNgramF.NGR_EQUALS) {
                        int elId=0;
                        if(li.elem!=null) {
                            elId=li.elem.getElementId();
                        }
                        bookCounts.incElemCount(elId, li2.count);
                    }
                    li2=li2.next;
                }
                li=li.next;
            }
            
            // update counts for this ngram
            if(oldValue==null) {
                oldValue=new NgramInfo(newValue);
                // questionable - could be moved to NgramBook but let's keep counts together:
                bookCounts.ngrCnt++;
            }else {
                oldValue.add(newValue);
            }
        }
        return oldValue;
    }
}

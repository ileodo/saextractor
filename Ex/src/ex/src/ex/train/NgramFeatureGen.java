// $Id: NgramFeatureGen.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

import uep.util.Logger;

import ex.features.ClassificationF;
import ex.features.TokenNgramF;
import ex.model.AttributeDef;
import ex.model.Model;
import ex.model.ModelElement;
import ex.train.NgramInfo.LLI;
import ex.train.NgramInfo.LLI2;

/** Induces a set of features potentially useful for phrase extraction 
 * from an input ngram book and a set of input arguments. 
 * 
 * Method 1. Large feature set
 *  Dedicated feature is created for each distinct ngram in each
 *  position with respect to the extracted phrase. E.g. a "works for"
 *  bigram, observed just before an extracted phrase in training data, 
 *  will create a new feature. Features like this will be created 
 *  for each n-gram (for n ranging 1..threshold) observed in training data
 *  and for position types selected for the classifier in the extraction model.
 *  The number of features generated can be reduced by sorting and thresholding 
 *  by the following parameters:
 *  - count of ngram seen in training data: either the ngram only or the ngram+position combination,
 *  - specifying threshold for the minimal acceptable pointwise mutual information 
 *    between any attribute (incl. bg) and the ngram+position,
 *  - max number of features.
 *  The precise meaning of "distinct ngram" can be changed by enabling 
 *  ignoreCase or ignoreLemma.
 *  
 *  
 * Method 2. Small feature set
 *  The ngrams that exhibit the same or similar behaviour in the training data
 *  are merged to produce a single feature. Conceptually this is like
 *  doing kind of a "fuzzy uniq" over the position counts in the count ngram file.
 *  The number of features generated can be reduced by sorting and thresholding:
 *  - count of ngram+position class,
 *  - the PWMI between ngram+position and any attribute (incl. bg),
 *  - max number of features.
 **/
public class NgramFeatureGen {
    protected double minMi=-1;
    protected int minNgramOccCnt=-1;
    protected int minNgramPosOccCnt=-1;
    protected int maxFCnt=-1;
    protected Logger logr;
    
    public NgramFeatureGen() {
        if(logr==null)
            logr=Logger.getLogger("fgen");
    }
    
    public void setMinMi(double minMi) {
        this.minMi=minMi;
    }
    public double getMinMi() {
        return minMi;
    }
    
    public void setMinNgramOccCnt(int minNgramOccCnt) {
        this.minNgramOccCnt=minNgramOccCnt;
    }
    public int getMinNgramOccCnt() {
        return minNgramOccCnt;
    }

    public void setMinNgramPosOccCnt(int minNgramPosOccCnt) {
        this.minNgramPosOccCnt=minNgramPosOccCnt;
    }
    public int getMinNgramPosCnt() {
        return minNgramPosOccCnt;
    }
    
    public void setMaxFcnt(int maxFCnt) {
        this.maxFCnt=maxFCnt;
    }
    public int getMaxFcnt() {
        return maxFCnt;
    }
    
    public PhraseBook<NgramInfo, NgramInfo> uniq(PhraseBook<? extends Object, ? extends Object> orig, Model model) {
        // generate unique map
        HashMap<String,List<AbstractPhrase>> umap=new HashMap<String,List<AbstractPhrase>>();
        Iterator<AbstractPhrase> it=orig.iterator();
        while(it.hasNext()) {
            AbstractPhrase phr=it.next();
            String key=phr.getData().toString();
            List<AbstractPhrase> phrases=umap.get(key);
            if(phrases==null) {
                phrases=new LinkedList<AbstractPhrase>();
                umap.put(key, phrases);
            }
            AbstractPhrase copy=new GenericPhrase(phr);
            phrases.add(copy);
        }
        // create new book
        NgramBook<NgramInfo,NgramInfo> ngrams=new NgramBook<NgramInfo,NgramInfo>("uniq");
        Iterator<Entry<String,List<AbstractPhrase>>> pairIt=umap.entrySet().iterator();
        while(pairIt.hasNext()) {
            Entry<String,List<AbstractPhrase>> pair=pairIt.next();
            String key=pair.getKey();
            System.out.println(key);
            NgramInfo ni=NgramInfo.fromString(key, 0, key.length(), model, true);
            Iterator<AbstractPhrase> pit=pair.getValue().iterator();
            while(pit.hasNext()) {
                GenericPhrase phr=(GenericPhrase) pit.next();
                System.out.print("\t"); System.out.println(phr);
                ngrams.put(phr.tokens, 0, phr.getLength(), ni, true);
            }
        }
        return ngrams;
    }
    
    private class NgramInfoStats {
        double maxMi;
        int maxPOC; // max ngram+position occurrence count
    }
    
    /** Returns true if the feature satisfies occurrence and PWMI thresholds. 
     * Populates NgramInfoStats with PWMI and the maximum position occurrence count for the feature. */
    protected boolean goodFeature(NgramInfo counts, PhraseBook<NgramInfo,NgramInfo> countBook, NgramInfoStats ret) {
        ret.maxMi=-1;
        ret.maxPOC=-1; // max ngram+position occurrence count
        if(minNgramOccCnt>0 && counts.getOccCnt()<minNgramOccCnt) {
            return false;
        }
        if(minNgramPosOccCnt>0) {
            LLI li=counts.elems;
            while(li!=null) {
                LLI2 li2=li.counts;
                while(li2!=null) {
                    if(li2.count>ret.maxPOC) {
                        ret.maxPOC=li2.count;
                    }
                    li2=li2.next;
                }
                li=li.next;
            }
            if(ret.maxPOC<minNgramPosOccCnt) {
                return false;
            }
        }
        if(true || minMi>0) {
            ret.maxMi=((NgramBook<NgramInfo,NgramInfo>) countBook).getMaxPWMI(counts, null);
            if(minMi>0 && ret.maxMi<minMi) {
                return false;
            }
        }
        return true;
    }
    
    public NgramFeatureBook createFeaturesFull(PhraseBook<NgramInfo,NgramInfo> countBook, Model model) {
        return (NgramFeatureBook) createFeaturesFullInternal(countBook, model, false);
    }
    
    @SuppressWarnings("unchecked")
    public PhraseBook<List<FeatCand>,FeatCand> createFeaturesFullCand(PhraseBook<NgramInfo,NgramInfo> countBook, Model model) {
        return (PhraseBook<List<FeatCand>,FeatCand>) createFeaturesFullInternal(countBook, model, true);
    }
    
    /* Creates a new PhraseBook with the same keys or with some of them filtered out. 
     * */
    public PhraseBook createFeaturesFullInternal(PhraseBook<NgramInfo,NgramInfo> countBook, Model model, boolean createCandBook) {
        // create feature candidates
        List<FeatCand> featCandList=new ArrayList<FeatCand>(countBook.size());
        NgramInfoStats stats=new NgramInfoStats();
        Iterator<AbstractPhrase> it=countBook.iterator();
        while(it.hasNext()) {
            AbstractPhrase phr=it.next();
            NgramInfo counts=(NgramInfo) phr.getData();
            if(goodFeature(counts, countBook, stats)) {
                AbstractPhrase copy=new GenericPhrase(phr);
                FeatCand fc=new FeatCand();
                fc.ngrams.add(copy);
                fc.maxPWMI=stats.maxMi;
                fc.occCnt=counts.getOccCnt();
                // single ngram corresponds to many positions with respect to many elements:
                fc.elem=null;
                fc.pos=(byte)0;
                featCandList.add(fc);
            }
        }

        NgramBook featBook = createCandBook? featListToCandBook(featCandList): featListToFeatBook(featCandList);
        return featBook;
    }

    private class FeatCandThrComparator implements Comparator<FeatCand> {
        public int compare(FeatCand o1, FeatCand o2) {
            if(o1==o2)
                return 0;
            double dMi=o1.maxPWMI-o2.maxPWMI;
            if(dMi<0)
                return -1;
            else if(dMi>0)
                return 1;
            int dOcc=o1.occCnt-o2.occCnt;
            if(dOcc<0)
                return -1;
            else if(dOcc>0)
                return 1;
            return 0;
        }
    }
    
    /** Identifies type of context for a specific attribute,
     *  e.g. name-NGR_OVERLAPS_LEFT or title-NGR_BEFORE */
    private class ElemPos {
        ModelElement elem;
        byte pos;
        public ElemPos(ModelElement elem, byte pos) {
            this.elem=elem;
            this.pos=pos;
        }
        public int hashCode() {
            return pos+((elem!=null)? elem.hashCode(): 0);
        }
        public boolean equals(Object other) {
            if(this==other)
                return true;
            ElemPos o=(ElemPos) other;
            if(this.elem==o.elem && this.pos==o.pos) {
                return true;
            }
            return false;
        }
        public String toString() {
            return ((elem!=null)? elem.name: ClassificationF.BG)+"-"+TokenNgramF.pos2string(pos);
        }
    }
    
    public NgramFeatureBook createFeaturesClassed(PhraseBook<NgramInfo,NgramInfo> countBook, Model model) {
        return (NgramFeatureBook) createFeaturesClassedInternal(countBook, model, false);
    }

    @SuppressWarnings("unchecked")
    public PhraseBook<List<FeatCand>,FeatCand> createFeaturesClassedCand(PhraseBook<NgramInfo,NgramInfo> countBook, Model model) {
        return (PhraseBook<List<FeatCand>,FeatCand>) createFeaturesClassedInternal(countBook, model, true);
    }
    
    public PhraseBook createFeaturesClassedInternal(PhraseBook<NgramInfo,NgramInfo> countBook, Model model, boolean createCandBook) {
//        range definitions: >=1, >=2, >=4 etc.
//        int[] occThresholds={1,2,4,7,13,26,51};
//        int[] miThresholds={2,6,11,21,51,151};
        int[] occThresholds={1,3,8,40,100};
        int[] miThresholds={2,8,15,30,100};
        
        // create attribute-position buckets
        Map<ElemPos,SortedSet<FeatCand>> apMap=new HashMap<ElemPos,SortedSet<FeatCand>>();
        int acnt=model.getAttributeCount();
        for(int a=-1;a<acnt;a++) {
            AttributeDef ad=(a==-1)? null: model.getAttribute(a);
            logr.LG(Logger.USR,"Reg att "+ad);
            for(byte p=TokenNgramF.NGR_POS_FIRST; p<=TokenNgramF.NGR_POS_LAST; p++) {
                ElemPos ap=new ElemPos(ad, p);
                SortedSet<FeatCand> centers=new TreeSet<FeatCand>(new FeatCandThrComparator());
                for(int i=0;i<occThresholds.length;i++) {
                    for(int j=0;j<miThresholds.length;j++) {
                        FeatCand fc=new FeatCand();
                        fc.occCnt=occThresholds[i];
                        fc.maxPWMI=miThresholds[j];
                        fc.elem=ad;
                        fc.pos=p;
                        // fc.name=((ad==null)? ClassificationF.BG: ad.name)+"-"+TokenNgramF.pos2string(p);
                        centers.add(fc);
                    }
                }
                apMap.put(ap, centers);
            }
        }

        // clear thresholds, will use them to store aggregated values:
        Iterator<Map.Entry<ElemPos,SortedSet<FeatCand>>> lfit=apMap.entrySet().iterator();
        while(lfit.hasNext()) {
            Map.Entry<ElemPos,SortedSet<FeatCand>> entry=lfit.next();
            Iterator<FeatCand> fit=entry.getValue().iterator();
            while(fit.hasNext()) {
                FeatCand fc=fit.next();
                //fc.occCnt=0;
                //fc.maxPWMI=0;
            }
        }
        
        ArrayList<FeatCand> usedCands=new ArrayList<FeatCand>(120);
        
        // populate centers of attribute-position buckets:
        NgramInfoStats stats=new NgramInfoStats();
        Iterator<AbstractPhrase> it=countBook.iterator();
        FeatCand lookup=new FeatCand();
        ElemPos lup=new ElemPos(null,(byte)0);
        while(it.hasNext()) {
            AbstractPhrase phr=it.next();
            NgramInfo counts=(NgramInfo) phr.getData();
            boolean isGood=goodFeature(counts, countBook, stats); // to set stats only
            // filtering done for the aggregated features
            if(true) {
                // iterate over ngram counts and set features respectively
                LLI elit=counts.elems;
                while(elit!=null) {
                    lup.elem=elit.elem;
                    LLI2 posit=elit.counts;
                    while(posit!=null) {
                        lup.pos=posit.pos;
                        SortedSet<FeatCand> centers=apMap.get(lup);
                        if(centers==null) {
                            throw new IllegalArgumentException("Feature set not found for ElemPos="+lup);
                        }
                        // find and add to the closest center:
                        lookup.occCnt=counts.getOccCnt();
                        lookup.maxPWMI=stats.maxMi;
                        FeatCand fc=findClosestFeatCand(centers, lookup);
                        if(!usedCands.contains(fc)) {
                            usedCands.add(fc);
                            logr.LG(Logger.USR,"occ="+lookup.occCnt+",mi="+lookup.maxPWMI+"->"+fc.toString());
                        }
                        // we must not touch these as they serve as thresholds for now and are 
                        // used by the sorted sets for ordering.
                        // fc.occCnt+=counts.getOccCnt();
                        // fc.maxPWMI = to be computed after aggregation
                        AbstractPhrase copy=new GenericPhrase(phr);
                        fc.ngrams.add(copy);
                        posit=posit.next;
                    }
                    elit=elit.next;
                }
            }
        }
        logr.LG(Logger.ERR,"Used features="+usedCands.size());

        // Compute occCnt and PWMI for the new aggregated features and 
        // put them into a single book.
        List<FeatCand> features=new ArrayList<FeatCand>(32); 
        lfit=apMap.entrySet().iterator();
        while(lfit.hasNext()) {
            Map.Entry<ElemPos,SortedSet<FeatCand>> entry=lfit.next();
            // features.addAll(entry.getValue());
            Iterator<FeatCand> fit=entry.getValue().iterator();
            while(fit.hasNext()) {
                FeatCand fc=fit.next();
                // Only consider observed features
                if(fc.ngrams.size()>0) {
                    // Compute occCnt and PWMI based on member ngrams
                    Iterator<AbstractPhrase> pit=fc.ngrams.iterator();
                    NgramInfo summed=new NgramInfo();
                    while(pit.hasNext()) {
                        AbstractPhrase phr=pit.next();
                        summed.add((NgramInfo) phr.getData());
                    }
                    fc.occCnt=summed.getOccCnt();
                    boolean isGood=goodFeature(summed, countBook, stats); // to set stats only
                    fc.maxPWMI=stats.maxMi;
                    features.add(fc);
                }
            }
            entry.getValue().clear();
        }
        apMap.clear();
        NgramBook featBook = createCandBook? featListToCandBook(features): featListToFeatBook(features);
        return featBook;
    }
    
    private FeatCand findClosestFeatCand(SortedSet<FeatCand> centers, FeatCand lookup) {
        FeatCand ret=null;
        SortedSet<FeatCand> greater=centers.tailSet(lookup);
        if(greater.size()==0) {
            ret=centers.last();
        }else {
            // find - within the MI same level - the FeatCand with the closest lesser occCnt
            Iterator<FeatCand> fit=greater.iterator();
            ret=fit.next();
            while(fit.hasNext()) {
                FeatCand fc=fit.next();
                if(fc.maxPWMI==ret.maxPWMI && fc.occCnt<lookup.occCnt)
                    ret=fc;
                else
                    break;
            }
        }
        return ret;
    }

    private int createFeatCandList(Collection<FeatCand> featCandSet, List<FeatCand> featCandList) {
        // copy, sort and threshold feature candidates
        Iterator<FeatCand> fit=featCandSet.iterator();
        while(fit.hasNext()) {
            FeatCand fc=fit.next();
            featCandList.add(fc);
        }
        Collections.sort(featCandList);
        int fcnt=featCandList.size();
        if(maxFCnt>-1 && fcnt>maxFCnt) {
            fcnt=maxFCnt;
        }
        logr.LG(Logger.USR, "Generated "+fcnt+"/"+featCandList.size()+" features.");
        return fcnt;
    }
    
    public NgramBook<List<FeatCand>,FeatCand> featListToCandBook(Collection<FeatCand> featCandSet) {
        ArrayList<FeatCand> flst=new ArrayList<FeatCand>(featCandSet.size());
        int fcnt = createFeatCandList(featCandSet, flst);
        
        // create phrasebook capable of mapping ngrams to features and of fast matching of the ngrams
        NgramBook<List<FeatCand>,FeatCand> featBook=
            new NgramBook<List<FeatCand>,FeatCand>("featBook", new FeatureListBookAdapter());
        for(int i=0;i<fcnt;i++) {
            FeatCand fc=flst.get(flst.size()-i-1);
            // renumber
            fc.id=i+1;
            Iterator<AbstractPhrase> pit=fc.ngrams.iterator();
            while(pit.hasNext()) {
                GenericPhrase phr=(GenericPhrase) pit.next();
                // multiple feature candidates may get listed for each book entry
                featBook.put(phr.tokens, 0, phr.tokens.length, fc, false);
            }
        }
        return featBook;
    }
    
    public NgramFeatureBook featListToFeatBook(Collection<FeatCand> featCandSet) {
        ArrayList<FeatCand> flst=new ArrayList<FeatCand>(featCandSet.size());
        int fcnt = createFeatCandList(featCandSet, flst);
        
        // create phrasebook capable of mapping ngrams to features and of fast matching of the ngrams
        NgramFeatureBook featBook=new NgramFeatureBook("featBook");
        List<TokenNgramF> insertList=new ArrayList<TokenNgramF>(1);
        insertList.add(null);
        for(int i=0;i<fcnt;i++) {
            FeatCand fc=flst.get(flst.size()-i-1);
            // renumber
            fc.id=i+1;
            TokenNgramF f=TokenNgramF.fromCandidate(fc);
            insertList.set(0, f);
            Iterator<AbstractPhrase> pit=fc.ngrams.iterator();
            while(pit.hasNext()) {
                GenericPhrase phr=(GenericPhrase) pit.next();
                // multiple feature candidates may get listed for each book entry
                featBook.put(phr.tokens, 0, phr.tokens.length, insertList, false);
            }
        }
        return featBook;
    }
}

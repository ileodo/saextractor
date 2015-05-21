// $Id: PhraseSource.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uep.util.Logger;
import uep.util.Options;

import ex.ac.PatMatch;
import ex.ac.TokenPattern;
import ex.features.AnnotationF;
import ex.features.ClassificationF;
import ex.features.Feature;
import ex.features.NumericValueConstraintF;
import ex.features.PhraseLengthConstraintF;
import ex.features.PhraseLengthF;
import ex.features.ScriptPatternF;
import ex.features.TokenNgramF;
import ex.features.TokenPatternF;
import ex.model.AttributeDef;
import ex.model.ClassDef;
import ex.model.ClassifierDef;
import ex.model.FeatureDef;
import ex.model.Model;
import ex.model.ScriptPattern;
import ex.reader.Document;
import ex.reader.SemAnnot;
import ex.reader.TokenAnnot;

/** Generates phrases suitable for input to machine learning classifiers.
 * Each phrase is a vector of binary features to be classified into one of possible classes. 
 * One class exists for each attribute plus there is one "background" class. */
public class PhraseSource implements DataSource {
    Model model;
    Document doc;
    ClassifierDef csd;
    ArrayList<Feature> features; // holds all common features and all features of the model, not filtered
    int totalFeatureCnt; // total count of byte features resulting from the features list above
    double confidenceThreshold;
    static Logger log;
    int minAttValLen;
    int maxAttValLen;
    public static final int defaultMaxAttValLen=10;
    private NFInfo[] nfMatches;
    private static final Pattern numPat=Pattern.compile("[0-9]+([.,][0-9]+)?");
    int useAllModelFeats;

    public PhraseSource() {
        if(log==null)
            log=Logger.getLogger("PhraseSource");
    }
    
    public void initialize(Model model, ClassifierDef csd) {
        features=new ArrayList<Feature>(64);
        setModel(model, csd);
    }
    
    class PhraseIterator implements SampleIterator {
        int tokIdx;
        int len;
        int maxLen;
        int minLen;
        // for set* methods to know easily what was the last next() result
        int lastTokIdx;
        int lastLen;
        // filter to populate only some of the features of Phrase
        byte filter;
        // the phrase object to be returned by next()
        PhraseSample phrase;
        StringBuffer tmp;
        
        PhraseIterator(int minPhrLen, int maxPhrLen, byte filter) {
            minLen=minPhrLen>0? minPhrLen: 1;
            maxLen=maxPhrLen>0? maxPhrLen: Integer.MAX_VALUE;
            len=minLen;
            tokIdx=0;
            this.filter=filter;
            tmp=new StringBuffer(128);
            phrase=new PhraseSample();
        }
        public boolean hasNext() {
            return tokIdx<doc.tokens.length && minLen<=doc.tokens.length;
        }
        public DataSample next() {
            if(!hasNext())
                throw new NoSuchElementException();
            // for current phrase, populate classification and featureVals
            phrase.populate(tokIdx, len);
            
            // remember what we returned for subsequent set methods
            lastTokIdx=tokIdx;
            lastLen=len;

            // advance cursor to the next phrase
            if(len==maxLen || tokIdx+len>=doc.tokens.length) {
                tokIdx++;
                len=minLen;
            }else {
                len++;
            }
            
            // return current phrase 
            return phrase;
        }
        public void remove() {
            throw new UnsupportedOperationException("Cannot remove phrase from document");
        }
        public void setClass(String className, double prob, SampleClassifier author) {
            if(prob>=confidenceThreshold) {
                createUpdatePhrase(className, prob, author);
            }
        }
        public void setClasses(List<SampleClassification> nbestItems, int nbest, SampleClassifier author) {
            double lastProb=Double.MAX_VALUE;
            int cnt=0;
            for(SampleClassification sc: nbestItems) {
                if(sc.getConfidence() > lastProb) {
                    throw new IllegalArgumentException("Classes not ordered by probability!");
                }
                if(sc.getClassName().equals(ClassificationF.BG))
                    continue;
                createUpdatePhrase(sc.getClassName(), sc.getConfidence(), author);
                cnt++;
                if(cnt>=nbest)
                    break;
            }
        }
        protected boolean createUpdatePhrase(String className, double prob, SampleClassifier author) {
            String labelName = csd.id + "." + className;
            SemAnnot sa=new SemAnnot(SemAnnot.TYPE_AV, lastTokIdx, lastTokIdx+lastLen-1, null, -1, labelName, null);
            sa.setProb(prob);
            doc.addSemAnnot(sa);
            // we could have used PatMatches instead of SemAnnots like below:
            // PatMatch phr=new PatMatch(null, lastTokIdx, lastLen);
            // phr.matchLevel=prob;
            // Document.addMatch(phr, doc.tokens, phr.startIdx);
            if(log.IFLG(Logger.INF)) {
                log.LG(Logger.INF,"Classified as "+labelName+": "+phrase.toString(SemAnnot.TYPE_ALL, DataSourceWriter.FMT_LOG));
            }
            if(log.IFLG(Logger.USR)) {
                log.LG(Logger.USR,"Classified as "+labelName+": "+Document.toString(doc.tokens, phrase.startIdx, phrase.len, " "));
            }
            return true;
        }
        
        public class PhraseSample implements DataSample {
            String cls=null;
            int weight=1;
            int startIdx=-1;
            int len=-1;
            boolean[] binFilter; // for each feature from all features, is true if not filtered out
            int[] featureVals; // values of features after filtering
            // Ngram features in the PhraseSource.features list were assigned f.localId which
            // holds their index in the PhraseSource.features list. However we need to know their index
            // in the PhraseSample.featureVals array, which may be shorter due to filtering.
            // We know all the ngram features are the last ones so it is enough to remember
            // the difference PhraseSource.features.size() - PhraseSample.featureVals.length.
            int ngramFeatIdxDiff; 
            
            protected PhraseSample() {
                binFilter=new boolean[features.size()];
                int ffc=0; // filtered feature count
                for(int i=0;i<features.size();i++) {
                    Feature f=features.get(i);
                    // ignore what has been filtered out
                    boolean keep=validFeature(f, filter);
                    binFilter[i]=keep;
                    if(keep)
                        ffc++;
                }
                featureVals=new int[ffc];
                ngramFeatIdxDiff=features.size()-ffc;
            }
            /* n-ary class */
            public String getClassification() {
                return cls;
            }
            public void setClassification(String cls) {
                this.cls=cls;
            }
            /* sample weight; typically based on frequency */
            public int getWeight() {
                return weight;
            }
            public void setWeight(int weight) {
                this.weight=weight;
            }
            /* binary features */
            public int[] getFeatures() {
                return featureVals;
            }

            /** Generates a one-line record consisting of feature=value pairs. 
             * User filter can be set to further extend iterator's filter; possible values are:
             * SemAnnot.TYPE_ALL, SemAnnot.TYPE_AV, SemAnnot.TYPE_INST, or SemAnnot.TYPE_CHUNK 
             * If set to -1, the underlying iterator's filter will be used. */
            public String toString(byte userFilter, byte format) {
                tmp.setLength(0);
                if(userFilter==-1)
                    userFilter=filter;
                if(format==DataSourceWriter.FMT_LOG) {
                    tmp.append("\"");
                    // dump prefix for debugging
                    final int ctxLen=5;
                    int idx=Math.max(0, startIdx-ctxLen);
                    if(startIdx!=idx) {
                        tmp.append("["+Document.toString(doc.tokens, idx, startIdx-idx, " ")+"]");
                    }
                    tmp.append(Document.toString(doc.tokens, startIdx, len, " "));
                    idx=startIdx+len;
                    if(idx<doc.tokens.length) {
                        tmp.append("["+Document.toString(doc.tokens, idx, Math.min(doc.tokens.length-idx, ctxLen), " ")+"]");
                    }

                    tmp.append("\" ");
                    // dump prefix for debugging
                }
                int fidx=0;
                for(int i=0;i<features.size();i++) {
                    Feature f=features.get(i);
                    // ignore what has been filtered out by the iterator's filter:
                    if(!validFeature(f, filter))
                        continue;
                    // ignore what has been filtered out by user filter, but increase fidx:
                    if(!validFeature(f, userFilter))
                        ;
                    else if((f instanceof ClassificationF)) {
                        String classVal=((ClassificationF)f).toString(featureVals[fidx]);
                        if(format==DataSourceWriter.FMT_AV || format==DataSourceWriter.FMT_LOG)
                            tmp.append("class=");
                        tmp.append(classVal); // classVal == cls
                    }else if(f.valueCnt==2 || (f instanceof PhraseLengthF)) {
                        if(format==DataSourceWriter.FMT_LOG && 
                           (f instanceof TokenNgramF) && featureVals[fidx]==0) {
                            // don't dump ngram features that are off
                        }else {
                            if(tmp.length()>0)
                                tmp.append(",");
                            if(format==DataSourceWriter.FMT_AV || format==DataSourceWriter.FMT_LOG) {
                                if(f instanceof TokenNgramF)
                                    tmp.append(f);
                                else
                                    tmp.append(f.name);
                                tmp.append("=");
                            }
                            tmp.append(featureVals[fidx]);
                        }
                    }else {
                        if(tmp.length()>0)
                            tmp.append(",");
                        tmp.append("unk:"+tmp.append(f.name)+"="+featureVals[fidx]);
                    }
                    fidx++;

                    // n-ary features will need to get converted to binary; 
                    // we don't handle these so far. 
                    // E.g. "n-gram before" feature will turn into |relevant ngrams| binary features.
                }
                return tmp.toString();
            }
            
            /** Calls toString(SemAnnot.TYPE_ALL, DataSourceWriter.FMT_AV) */
            public String toString() {
                return toString(SemAnnot.TYPE_ALL, DataSourceWriter.FMT_AV);
            }
            
            /** Populates featureVals and classification for current phrase; based on current filter */
            protected void populate(int tokIdx, int phraseLen) {
                TokenAnnot st=doc.tokens[tokIdx];

                // 1. fill in classification
                cls=ClassificationF.BG;
                startIdx=tokIdx;
                len=phraseLen;
                
                int classMode=Options.getOptionsInstance().getIntDefault("class_mode", -1);
                if(classMode<=0) { 
                    // standard: everything is bg except for phrases exactly matching attribute values
                    if(st.semAnnots!=null) {
                        for(int i=0;i<st.semAnnots.size();i++) {
                            SemAnnot sa=st.semAnnots.get(i);
                            if(csd.skipAnnotation(sa, (ClassificationF)features.get(0))) {
                                continue;
                            }
                            if(sa.type==SemAnnot.TYPE_AV && (sa.endIdx-sa.startIdx+1)==len) {
                                cls=((medieq.iet.model.AttributeValue)sa.data).getAttributeDef().getName();
                                break;
                            }
                        }
                    }
                }else {
                    // extended classes: each attribute also has its _prefix, _suffix, _cleft, _cright etc. variants
                    int endTokIdx=tokIdx+len-1;
                    if(st.semAnnots!=null) {
                        for(int i=0;i<st.semAnnots.size();i++) {
                            SemAnnot sa=st.semAnnots.get(i);
                            if(sa.type==SemAnnot.TYPE_AV && !csd.skipAnnotation(sa, (ClassificationF)features.get(0))) {
                                cls=((medieq.iet.model.AttributeValue)sa.data).getAttributeDef().getName();
                                if(sa.endIdx==endTokIdx) {
                                    ;
                                }else if(sa.endIdx<endTokIdx) {
                                    cls+=ClassificationF.PRIGHT;
                                }else if(sa.endIdx>endTokIdx) {
                                    cls+=ClassificationF.PREFIX;
                                }
                                break;
                            }
                        }
                    }else {
                        for(int t=0;t<phraseLen;t++) {
                            TokenAnnot tok=doc.tokens[tokIdx+t];
                            if(tok.semAnnotPtrs!=null) {
                                for(SemAnnot sa: tok.semAnnotPtrs) {
                                    if(sa.type!=SemAnnot.TYPE_AV || csd.skipAnnotation(sa, (ClassificationF)features.get(0))) {
                                        continue;
                                    }
                                    cls=((medieq.iet.model.AttributeValue)sa.data).getAttributeDef().getName();
                                    if(sa.startIdx<tok.startIdx) {
                                        if(sa.endIdx==endTokIdx) {
                                            cls+=ClassificationF.SUFFIX; // phrase is a suffix of att
                                        }else if(sa.endIdx<endTokIdx) {
                                            cls+=ClassificationF.CRIGHT; // phrase crosses right border of att
                                        }else if(sa.endIdx>endTokIdx) {
                                            cls+=ClassificationF.CONTAINED; // phrase is contained inside att
                                        }
                                    }else if(sa.startIdx==tok.startIdx) {
                                        if(sa.endIdx==endTokIdx) {
                                            ;
                                        }else if(sa.endIdx<endTokIdx) {
                                            cls+=ClassificationF.PRIGHT; // phrase prolongs att on the right
                                        }else if(sa.endIdx>endTokIdx) {
                                            cls+=ClassificationF.PREFIX; // phrase is a prefix of att
                                        }                                        
                                    }else if(sa.startIdx>tok.startIdx) {
                                        if(sa.endIdx==endTokIdx) {
                                            cls+=ClassificationF.PLEFT; // phrase prolongs att on the left
                                        }else if(sa.endIdx<endTokIdx) {
                                            cls+=ClassificationF.CONTAINS; // phrase contains att
                                        }else if(sa.endIdx>endTokIdx) {
                                            cls+=ClassificationF.CLEFT; // phrase crosses left border of att
                                        }
                                    }
                                    t=phraseLen; // also break outer loop
                                    break;
                                }
                            }
                        }
                    }
                }

                // 2. fill in feature values
                Arrays.fill(featureVals, 0);
                int idx=0;
                for(int i=0;i<binFilter.length;i++) {
                    if(!binFilter[i])
                        continue;
                    Feature f=features.get(i);
                    if(f instanceof ClassificationF) {
                        int val=((ClassificationF)f).toValue(cls);
                        if(val>=128) {
                            throw new IllegalArgumentException("Too many classes: "+f);
                        }
                        featureVals[idx]=val;
                    }else if(f instanceof TokenPatternF) {
                        TokenPattern pat=((TokenPatternF)f).pat;
                        if(!isFeature(pat))
                            continue;
                        PatMatch match=null;
                        switch(pat.type) {
                        case TokenPattern.PAT_VAL:
                            match=st.findMatch(pat, PatMatch.FIND_START, len, len, null);
                            break;
                        case TokenPattern.PAT_CTX_L:
                            if(tokIdx>0) {
                                match=doc.tokens[tokIdx-1].findMatch(pat, PatMatch.FIND_END, 1, Integer.MAX_VALUE, null);
                            }
                            break;
                        case TokenPattern.PAT_CTX_R:
                            if(tokIdx+len<doc.tokens.length) {
                                match=doc.tokens[tokIdx+len].findMatch(pat, PatMatch.FIND_START, 1, Integer.MAX_VALUE, null);
                            }
                            break;
                        case TokenPattern.PAT_CTX_LR:
                            log.LG(Logger.ERR,"LR TokenPattern as feature: not implemented");
                            continue;
                        default:
                        }
                        if(match!=null)
                            featureVals[idx]=1;
                    }else if(f instanceof ScriptPatternF) {
                        ScriptPattern pat=((ScriptPatternF)f).pat;
                        // TODO: find out if this matches, probably need to eval it here as 
                        // evaled during extraction
                        continue; // do not idx++
                    }else if(f instanceof PhraseLengthConstraintF) {
                        PhraseLengthConstraintF plcf=(PhraseLengthConstraintF)f;
                        if(len>=plcf.minValue && len<=plcf.maxValue) {
                            featureVals[idx]=1;
                            //log.LG(Logger.WRN,"Fired plcf "+plcf.name);
                        }else {
                            //log.LG(Logger.WRN,"Not fired plcf "+plcf.name+" for len="+len);
                        }
                    }else if(f instanceof NumericValueConstraintF) {
                        NumericValueConstraintF nvcf=(NumericValueConstraintF)f;
                        String phr=Document.toString(doc.tokens,tokIdx,len,"");
                        Matcher mat=numPat.matcher(phr);
                        if(mat.find()) {
                            try {
                                double val=new Double(mat.group(0)).doubleValue();
                                if(val>=nvcf.minValue && val<=nvcf.maxValue)
                                    featureVals[idx]=1;
                            }catch(NumberFormatException ex) {
                                ;
                            }
                        }
                    }else if(f instanceof PhraseLengthF) {
                        featureVals[idx]=len;
                        
                    }else if(f instanceof AnnotationF) {
                        AnnotationF af=(AnnotationF)f;
                        if(st.semAnnots!=null) {
                            for(int j=0;j<st.semAnnots.size();j++) {
                                SemAnnot sa=st.semAnnots.get(j);
                                if(sa.type!=af.type)
                                    continue;
                                switch(sa.type) {
                                case SemAnnot.TYPE_CHUNK: // af.data=String, sa.data=String
                                    if((sa.endIdx-sa.startIdx+1)==len) {
                                        if(af.data.equals(sa.data)) {
                                            featureVals[idx]=1;
                                        }
                                    }
                                    break;
                                case SemAnnot.TYPE_INST: // af.data=ex.model.ClassDef, sa.data=iet.model.Instance
                                    // TODO: class instances have to be treated separately as the current phrase space
                                    // is limited to phrases with length in <AttributeDef.minLen, maxLen>
                                    if((sa.endIdx-sa.startIdx+1)==len) {
                                        if(((ex.model.ClassDef)af.data).name.equals(
                                                ((medieq.iet.model.Instance)sa.data).getClassDef().getName())) {
                                            featureVals[idx]=1;
                                        }
                                    }
                                    break;
                                case SemAnnot.TYPE_AV: // af.data=ex.model.AttributeDef, sa.data=iet.model.AttributeValue
                                    if(!csd.skipAnnotation(sa, (ClassificationF)features.get(0))) {
                                        if((sa.endIdx-sa.startIdx+1)==len) {
                                            if(((ex.model.AttributeDef)af.data).name.equals(
                                                    ((medieq.iet.model.AttributeValue)sa.data).getAttributeDef().getName())) {
                                                featureVals[idx]=1;
                                                log.LG(Logger.WRN,"Set annotation feature according to label "+sa+": "+getDebugInfo());
                                            }
                                        }
                                    }
                                    break;
                                default:
                                    log.LG(Logger.WRN,"Unknown SemAnnot type="+sa.type);
                                }
                            }
                        }
                    }else if(f instanceof TokenNgramF) {
                        // ngram features are set separately all at once below
                    }else {
                        log.LG(Logger.WRN,"PhraseSource not sure about feature "+f);
                    }
                    idx++;
                } // for i=0..binFilter.length
                
                // set ngram features
                if(nfMatches!=null) {
                    final boolean useOverlaps=true;
                    final boolean useContained=false;
                    final boolean useContains=false;
                    
                    // set content ngram features based on start token
                    if(nfMatches[tokIdx]!=null) {
                        NFInfo nfi=nfMatches[tokIdx];
                        if(nfi.matchStarts!=null) {
                            for(int j=0;j<nfi.matchStarts.length;j++) {
                                NFInfoMatch match=nfi.matchStarts[j];
                                for(TokenNgramF f: match.feats) {
                                    switch(f.posType) {
                                    case TokenNgramF.NGR_OVERLAPS_RIGHT:
                                    case TokenNgramF.NGR_OVERLAPS_LEFT:
                                        if(!useOverlaps)
                                            break;
                                    case TokenNgramF.NGR_EQUALS:
                                        // current phrase is a known value of some attribute
                                        // or is known to overlap with its right resp. left border
                                        if(len==match.len)
                                            setFeature(f);
                                        break;
                                    case TokenNgramF.NGR_PREFIX:
                                        // current phrase begins with known ngram
                                        if(len>=match.len)
                                            setFeature(f);
                                        break;
                                    case TokenNgramF.NGR_BEGINS_WITH: 
                                        // known ngram begins with current phrase
                                        if(len<=match.len)
                                            setFeature(f);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // set NGR_SUFFIX, NGR_ENDS_WITH based on last token of current phrase
                    if(nfMatches[tokIdx+len-1]!=null && nfMatches[tokIdx+len-1].matchEnds!=null) {
                        NFInfoMatch[] ends=nfMatches[tokIdx+len-1].matchEnds;
                        for(int j=0;j<ends.length;j++) {
                            NFInfoMatch match=ends[j];
                            for(TokenNgramF f: match.feats) {
                                switch(f.posType) {
                                case TokenNgramF.NGR_SUFFIX:
                                    // current phrase ends with known ngram
                                    if(match.len<=len)
                                        setFeature(f);
                                    break;
                                case TokenNgramF.NGR_ENDS_WITH:
                                    // current phrase is the end of a known ngram
                                    if(match.len>=len)
                                        setFeature(f);
                                    break;
                                }
                            }
                        }
                    }
                    
                    // set context ngram features: before and after
                    if(tokIdx>0 && nfMatches[tokIdx-1]!=null && nfMatches[tokIdx-1].matchEnds!=null) {
                        NFInfoMatch[] ends=nfMatches[tokIdx-1].matchEnds;
                        for(int j=0;j<ends.length;j++) {
                            for(TokenNgramF f: ends[j].feats) {
                                if(f.posType==TokenNgramF.NGR_BEFORE) {
                                    // current phrase is preceded by an ngram that is known as NGR_BEFORE 
                                    setFeature(f);
                                }
                            }
                        }
                    }
                    if(tokIdx+len+1<doc.tokens.length && nfMatches[tokIdx+len]!=null && nfMatches[tokIdx+len].matchStarts!=null) {
                        NFInfoMatch[] starts=nfMatches[tokIdx+len].matchStarts;
                        for(int j=0;j<starts.length;j++) {
                            for(TokenNgramF f: starts[j].feats) {
                                if(f.posType==TokenNgramF.NGR_AFTER) {
                                    // current phrase is followed by an ngram that is known as NGR_AFTER
                                    setFeature(f);
                                }
                            }
                        }
                    }
                    
                    if(useContained) {
                        for(int j=tokIdx;j<tokIdx+len;j++) {
                            if(nfMatches[j]==null) {
                                continue;
                            }
//                            NFInfoMatch[] ends=nfMatches[j].matchEnds;
//                            if(ends!=null) {
//                                for(NFInfoMatch nfim: ends) {
//                                    for(TokenNgramF f: nfim.feats) {
//                                        switch(f.posType) {
//                                        case TokenNgramF.NGR_OVERLAPS_RIGHT:
//                                            // ngram's right boundary overlaps into current phrase
//                                            if(j-nfim.len+1<tokIdx)
//                                                setFeature(f);
//                                            break;
//                                        }
//                                    }
//                                }
//                            }
                            NFInfoMatch[] starts=nfMatches[j].matchStarts;
                            if(starts!=null) {
                                for(NFInfoMatch nfim: starts) {
                                    for(TokenNgramF f: nfim.feats) {
                                        switch(f.posType) {
//                                        case TokenNgramF.NGR_OVERLAPS_LEFT:
//                                            if(useOverlaps) {
//                                                // ngram's left boundary overlaps into current phrase
//                                                if(j+nfim.len>tokIdx+len)
//                                                    setFeature(f);
//                                            }
//                                            break;
                                        case TokenNgramF.NGR_CONTAINED:
                                            if(useContained) {
                                                // ngram contained in phrase
                                                if(j+nfim.len<tokIdx+len)
                                                    setFeature(f);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // so far we ignore the following feature types:
                    // case TokenNgramF.NGR_CONTAINS: // phrase contained in ngram
                    if(useContains) {
                        ;
                    }
                }
            } // populate()
            
            void setFeature(TokenNgramF f) {
                if(log.IFLG(Logger.INF)) {
                    String phr=(String) getDebugInfo();
                    log.LG(Logger.INF,"fset "+phr+": "+f.toString());
                }
                featureVals[f.localId-ngramFeatIdxDiff]=1;
            }

            public Object getDebugInfo() {
                tmp.setLength(0);
                // dump prefix for debugging
                final int ctxLen=5;
                int idx=Math.max(0, startIdx-ctxLen);
                if(startIdx!=idx) {
                    tmp.append("["+Document.toString(doc.tokens, idx, startIdx-idx, " ")+"]");
                }
                tmp.append(Document.toString(doc.tokens, startIdx, len, " "));
                idx=startIdx+len;
                if(idx<doc.tokens.length) {
                    tmp.append("["+Document.toString(doc.tokens, idx, Math.min(doc.tokens.length-idx, ctxLen), " ")+"]");
                }
                return tmp.toString();
            }
        } // class PhraseSample
    } // class PhraseIterator
    
    class FeatureIteratorImpl implements FeatureIterator {
        int fidx; // next feature to return
        byte filter;
        int cidx; // current feature idx for getIdx()
        
        FeatureIteratorImpl(byte filter) {
            fidx=0; // -1 means the next feature returned will be phrase classification
            cidx=-1;
            this.filter=filter;
            setNextWanted();
        }
        public boolean hasNext() {
            return fidx<features.size();
        }
        public Feature next() {
            if(!hasNext())
                throw new NoSuchElementException();
            Feature f=null;
            //if(fidx<0) {
            //    fidx=0;
            //}else {
            f=features.get(fidx);
            cidx=fidx;
            fidx++;
            //}
            setNextWanted();
            return f;
        }
        // goes to the next wanted feature
        private void setNextWanted() {
            while(fidx<features.size()) {
                Feature f=features.get(fidx);
                if(validFeature(f, filter)) {
                    // log.LG(Logger.TRC,"ok "+f.name+"; type="+((AnnotationF)f).type+" filter="+filter);
                    break;
                }
                // log.LG(Logger.TRC,"no F"+fidx+"; name="+f.name+" type="+((AnnotationF)f).type+" filter="+filter);
                fidx++;
            }
        }
        public void remove() {
            throw new UnsupportedOperationException("Cannot remove feature from PhraseSource");            
        }
        public int getIdx() {
            return cidx;
        }        
    }
    
    protected boolean validFeature(Feature f, byte filter) {
        boolean discard = (f instanceof AnnotationF) && ((((AnnotationF)f).type & filter) == 0);
        return !discard;
    }
    
    private int addCommonFeatures() {
        int cnt=0;

        // extra annotations provided with document (other than annotated attributes and classes)
        Options o=Options.getOptionsInstance("ex");
        String extraLabels=o.getProperty("trainer_labels");
        if(extraLabels!=null) {
            String[] el=extraLabels.replaceAll("^\\s+|\\s+$", "").split("\\s+");
            for(int i=0;i<el.length;i++) {
                AnnotationF af=AnnotationF.getAnnotation(SemAnnot.TYPE_CHUNK, el[i], true);
                features.add(af);
                cnt++;
            }
        }
        
        // phrase length (n-ary)
        PhraseLengthF plf=PhraseLengthF.getSingleton();
        if(minAttValLen<1)
            minAttValLen=1;
        if(maxAttValLen<1)
            maxAttValLen=defaultMaxAttValLen; // Integer.MAX_VALUE;
        plf.setMinValue(minAttValLen);
        plf.setMaxValue(maxAttValLen);
        features.add(plf);
        cnt++;
        
        // first/last/before/after token n-grams handled separately by addNgramFeatures
        
        // first/last/before/after TokenPatterns
        // TODO: for induced TokenPatterns frequent in given context, add features
        
        // first/last/before/after tags
        // TODO: for tags frequent in given context, add features 
        
        log.LG(Logger.USR,"Collected "+cnt+" common properties");
        return cnt;
    }
    
    private int addNgramFeatures(NgramFeatureBook nfBook) {
        int cnt=0;
        // add all ngram features from book
        // features.addAll(nfBook.features);
        Iterator<TokenNgramF> fit=nfBook.features.iterator();
        while(fit.hasNext()) {
            TokenNgramF f=fit.next();
            // to know the idx of the feature to set when we see matched ngram feature:
            f.localId=features.size(); 
            features.add(f);
        }
        cnt+=nfBook.features.size();
        return cnt;
    }

    private int addModelFeatures() {
        int cnt=0;
        minAttValLen=-1;
        maxAttValLen=-1;
        // so far we handle attributes only; first create n-ary class feature:
        ClassificationF cf=new ClassificationF(SemAnnot.TYPE_AV,"AV",null);
        features.add(cf);
        cnt++;
        // attribute names gathered when we go through the model:
        List<String> attNameList=new ArrayList<String>(16);
        attNameList.add(ClassificationF.BG);
        // create binary features based on model content; either use all patterns from all attributes 
        // or only those patterns that belong to the attributes being classified:
        useAllModelFeats=Options.getOptionsInstance().getIntDefault("use_all_model_features", 0);
        
        for(int i=0;i<model.classArray.length;i++) {
            ClassDef cd=model.classArray[i];
            // pre-annotated class value
            AnnotationF af=AnnotationF.getAnnotation(SemAnnot.TYPE_INST, cd, true);
            features.add(af);
            cnt++;
            for(int j=0;j<cd.attArray.length;j++) {
                AttributeDef ad=cd.attArray[j];
                // always create feature for pre-annotated attribute value, even though it might not get used;
                // it serves for mapping string labels to AVs
                af=AnnotationF.getAnnotation(SemAnnot.TYPE_AV, ad, true);
                // do not add features for this attribute if the classifier does not handle it
                if(csd.supportsClass(ad)) {
                    attNameList.add(ad.name);
                }else if(csd.supportsFeaturesOf(ad)) {
                    // let features get included for this ad even though 
                    // the classifier does not classify into it
                }else if(useAllModelFeats==0) {
                    continue;
                }
                // add existing attribute annotation af to model features if it is to be used
                features.add(af);
                cnt++;
                // value patterns
                for(int k=0;k<ad.valPatterns.length;k++) {
                    TokenPattern vp=ad.valPatterns[k];
                    if(isFeature(vp)) {
                        TokenPatternF f=new TokenPatternF(vp);
                        features.add(f);
                        cnt++;
                    }
                }
                // context patterns
                for(int k=0;k<ad.ctxPatterns.length;k++) {
                    TokenPattern cp=ad.ctxPatterns[k];
                    if(isFeature(cp)) {
                        TokenPatternF f=new TokenPatternF(cp);
                        features.add(f);
                        cnt++;
                    }
                }
                // script patterns
                for(int k=0;k<ad.scriptPatterns.size();k++) {
                    ScriptPattern sp=ad.scriptPatterns.get(k);
                    if(false) {
                        ScriptPatternF f=new ScriptPatternF(sp);
                        features.add(f);
                        cnt++;
                    }
                }
                // length (in tokens) constraint
                if(ad.minLength>1 || ad.maxLength>0) {
                    PhraseLengthConstraintF plcf=new PhraseLengthConstraintF(ad.minLength, ad.maxLength, ad.name+"_len_c");
                    if(Logger.IFLOG(Logger.INF)) log.LG(Logger.INF,"Adding plcf "+ad.minLength+"-"+ad.maxLength);
                    features.add(plcf);
                    cnt++;
                    if(ad.minLength==-1 || ad.minLength<minAttValLen)
                        minAttValLen=ad.minLength;
                    if(ad.maxLength>maxAttValLen && ad.maxLength<Integer.MAX_VALUE)
                        maxAttValLen=ad.maxLength;
                    else if((ad.maxLength==-1 || ad.maxLength<Integer.MAX_VALUE) && 
                            maxAttValLen<defaultMaxAttValLen)
                        maxAttValLen=defaultMaxAttValLen;
                }
                // numeric value constraint (for numeric attributes only)
                if((ad.dataType==AttributeDef.TYPE_INT || ad.dataType==AttributeDef.TYPE_INT) && 
                        ad.minValue!=-1 && ad.maxValue!=-1) {
                    NumericValueConstraintF nvcf=new NumericValueConstraintF(ad.minValue, ad.maxValue, ad.name+"_numval");
                    if(Logger.IFLOG(Logger.INF)) log.LG(Logger.INF,"Adding nvcf "+nvcf.minValue+"-"+ad.maxValue);
                    features.add(nvcf);
                    cnt++;
                }
            }
        }
        cf.setValues(attNameList);
        log.LG(Logger.USR,"Collected "+cnt+" properties from model");
        return cnt;
    }

    public SampleIterator getDataIterator(byte filter) {
        return new PhraseIterator(minAttValLen, maxAttValLen, filter);
    }

    public FeatureIterator getFeatureIterator(byte filter) {
        return new FeatureIteratorImpl(filter);
    }
    
    public void setDocument(Document doc) {
        this.doc=doc;
        nfMatches=null;
//        if(model.classifiers!=null) {
//            Iterator<ClassifierDef> cit=model.classifiers.iterator();
//            while(cit.hasNext()) {
//                ClassifierDef cssd=cit.next();
        if(csd.features!=null) {
            for(FeatureDef fd: csd.features) {
                if(fd.type==FeatureDef.TYPE_NGRAM && fd.book!=null) {
                    NgramMatcher nm = new NgramMatcher(doc);
                    nfMatches = nm.match(fd.book);
                }
            }
        }
//            }
//        }
    }
    
    public void setModel(Model model, ClassifierDef csd) {
        this.model=model;
        this.csd=csd;
        this.minAttValLen=-2;
        this.maxAttValLen=-2;
        addModelFeatures();
        addCommonFeatures();
        if(csd.features!=null) {
            for(FeatureDef fd: csd.features) {
                if(fd.type==FeatureDef.TYPE_NGRAM) {
                    if(fd.book!=null) {
                        addNgramFeatures(fd.book);
                    }
                }
            }
        }
        int fc=0;
        for(int i=0;i<features.size();i++) {
            int vc=features.get(i).valueCnt;
            if(vc==2)
                fc+=1;
            else {
                Feature f=features.get(i);
                if(f instanceof ClassificationF || f instanceof PhraseLengthF)
                    fc+=1;
                else
                    log.LG(Logger.ERR,"N-ary property other than ClassificationF and PhraseLengthF not handled: "+f);
            }
        }
        Logger.LOG(Logger.USR,"Classifier "+csd+": N-ary features="+features.size()+" converted to binary="+fc);
        totalFeatureCnt=fc;
    }
        
    protected boolean isFeature(TokenPattern pat) {
        boolean rc=false;
        if(pat.useAsFeature) {
            switch(pat.type) {
            case TokenPattern.PAT_VAL:
            case TokenPattern.PAT_CTX_L:
            case TokenPattern.PAT_CTX_R:
                if(useAllModelFeats>0 || csd.supportsFeaturesOf(pat.modelElement)) {
                    rc=true;
                }
                break;
            }
        }
        return rc;
    }

    public String getName() {
        return "phrasesource:"+((doc!=null)? doc.id: "no document");
    }

    public void setConfidenceThreshold(double confidence) {
        confidenceThreshold=confidence;
    }

    public int getFeatureCount() {
        return features.size();
    }

    public boolean supportsWeightedSamples() {
        return true;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    /** This is a no-op, since for PhraseSource, the class information is propagated 
     *  immediately to the underlying document in each call to SampleIterator.setClass() */
    public void commitClasses() {
        return;
    }
}

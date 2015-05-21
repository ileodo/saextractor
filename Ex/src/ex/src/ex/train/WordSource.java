// $Id: WordSource.java 1681 2008-10-11 10:04:38Z labsky $
package ex.train;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import uep.util.Logger;
import uep.util.Options;
import ex.ac.Annotable;
import ex.ac.PatMatch;
import ex.ac.TokenPattern;
import ex.features.AnnotationF;
import ex.features.ClassificationF;
import ex.features.EnumFeature;
import ex.features.Feature;
import ex.features.IntFeature;
import ex.features.PhraseF;
import ex.features.ScriptPatternF;
import ex.features.TokenCapF;
import ex.features.TokenIdF;
import ex.features.TokenLCF;
import ex.features.TokenLemmaF;
import ex.features.TokenNgramF;
import ex.features.TokenPatternF;
import ex.features.TokenTypeF;
import ex.features.UnaccentedF;
import ex.features.WordClassificationF;
import ex.features.WordOfPhraseF;
import ex.model.AttributeDef;
import ex.model.ClassDef;
import ex.model.ClassifierDef;
import ex.model.FeatureDef;
import ex.model.Model;
import ex.model.ScriptPattern;
import ex.reader.Document;
import ex.reader.SemAnnot;
import ex.reader.TokenAnnot;
import ex.train.DataSource.FeatureIterator;

public class WordSource implements DataSource {
    ClassifierDef csd; // ClassifierDef for which this DataSource supplies data
    Document doc; // current document that this DataSource transforms to samples
    Model model; // ex model used
    NFInfo[] nfMatches; // ngram feature matches (starts and ends)
    int useAllModelFeats; // use evidence from all attributes or just from attributes that csd classifies into
    ArrayList<Feature> features; // holds all features used by the samples in this DataSource, index = featureId
    Classification[] wordClasses; // intermediate storage for word classes before they are committed as phrase classes
    double confThr;
    protected static Logger log; 

    public WordSource() {
        if(log==null) {
            log=Logger.getLogger("WordSource");
        }        
    }
    
    public void initialize(Model model, ClassifierDef csd) {
        features=new ArrayList<Feature>(64);
        setModel(model, csd);
        wordClasses=null;
        confThr=0;
    }
    
    public SampleIterator getDataIterator(byte filter) {
        return new WordIterator(filter);
    }

    public int getFeatureCount() {
        return features.size();
    }

    public FeatureIterator getFeatureIterator(byte filter) {
        return new IdxIterator(features.iterator());
    }    

    public String getName() {
        return "wordsource:"+((doc!=null)? doc.id: "no document");
    }

    public void setConfidenceThreshold(double confidence) {
        confThr=confidence;
    }

    public void setDocument(Document doc) {
        this.doc=doc;
        wordClasses=new Classification[doc.tokens.length];
        if(csd.features!=null) {
            for(FeatureDef fd: csd.features) {
                if(fd.type==FeatureDef.TYPE_NGRAM && fd.book!=null) {
                    NgramMatcher nm = new NgramMatcher(doc);
                    nfMatches = nm.match(fd.book);
                }
            }
        }
    }

    public void setModel(Model model, ClassifierDef csd) {
        this.model=model;
        this.csd=csd;
        features.clear();
        addCommonFeatures();
        addModelFeatures();
        if(csd.features!=null) {
            for(FeatureDef fd: csd.features) {
                if(fd.type==FeatureDef.TYPE_NGRAM) {
                    if(fd.book!=null) {
                        addNgramFeatures(fd.book);
                    }
                }
            }
        }
        for(int i=0;i<features.size();i++) {
            Feature f=features.get(i);
            log.LG(Logger.USR,"f"+i+"("+f.valueCnt+"): "+f.name);
        }
        Logger.LOG(Logger.USR,"Classifier "+csd+": features="+features.size());
    }
    
    /** @return true if Pattern should be treated as a feature for the classifier 
     *  associated with this DataSource. */
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
    
    /** Adds common features of each word like the word itself or its lowercased version, its type, POS tag */
    private int addCommonFeatures() {
        int i=features.size();
        // create class feature that is suitable for individual words;
        // for one attribute there are 2 classes: beginning and inner word; 
        // values will be updated during addModelFeatures()
        WordClassificationF cf=new WordClassificationF(SemAnnot.TYPE_AV,"WC",null);
        features.add(cf);
        // common token features
        features.add(TokenIdF.getSingleton());
        features.add(TokenLCF.getSingleton());
        features.add(TokenLemmaF.getSingleton());
        features.add(UnaccentedF.getSingleton());
        features.add(TokenCapF.getSingleton());
        features.add(TokenTypeF.getSingleton());
        // features.add(TokenPOSF.getSingleton());
        int cnt=features.size()-i;
        log.LG(Logger.USR,"Collected "+cnt+" common properties");
        return cnt;
    }

    private int addNgramFeatures(NgramFeatureBook nfBook) {
        int cnt=0;
        // add all ngram features from book
        Iterator<TokenNgramF> fit=nfBook.features.iterator();
        while(fit.hasNext()) {
            TokenNgramF f=fit.next();
            switch(f.posType) {
            case TokenNgramF.NGR_BEFORE: 
            case TokenNgramF.NGR_AFTER:
            case TokenNgramF.NGR_EQUALS:
            case TokenNgramF.NGR_CONTAINED:
            case TokenNgramF.NGR_PREFIX:
            case TokenNgramF.NGR_SUFFIX:
                break; // only use the above ngram positions as separate word features
            default:
                continue;
            }
            // to know the idx of the feature to set when we see matched ngram feature:
            f.localId=features.size(); 
            features.add(new WordOfPhraseF("ngramf."+f.localId, f));
        }
        cnt+=nfBook.features.size();
        return cnt;
    }
    
    private int addModelFeatures() {
        int cnt=0;
        // attribute names gathered when we go through the model:
        List<String> attNameList=new ArrayList<String>(16);
        attNameList.add(ClassificationF.BG);
        // create features based on model content; either use all patterns from all attributes 
        // or only those patterns that belong to the attributes being classified:
        useAllModelFeats=Options.getOptionsInstance().getIntDefault("use_all_model_features", 0);
        
        for(int i=0;i<model.classArray.length;i++) {
            ClassDef cd=model.classArray[i];
            // pre-annotated class value
            AnnotationF af=AnnotationF.getAnnotation(SemAnnot.TYPE_INST, cd, true);
            features.add(new WordOfPhraseF("class.instance."+cd.name, af));
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
                    // let features get included for this attribute if the classifier classifies into it
                }else if(useAllModelFeats==0) {
                    continue;
                }
                // add existing attribute annotation af to model features if it is to be used
                features.add(new WordOfPhraseF("attribute.value."+ad.name, af));
                cnt++;
                // value patterns
                for(int k=0;k<ad.valPatterns.length;k++) {
                    TokenPattern vp=ad.valPatterns[k];
                    if(isFeature(vp)) {
                        TokenPatternF f=new TokenPatternF(vp);
                        features.add(new WordOfPhraseF("value.pattern."+vp.id, f));
                        cnt++;
                    }
                }
                // context patterns
                for(int k=0;k<ad.ctxPatterns.length;k++) {
                    TokenPattern cp=ad.ctxPatterns[k];
                    if(isFeature(cp)) {
                        TokenPatternF f=new TokenPatternF(cp);
                        features.add(new WordOfPhraseF("context.pattern."+cp.id, f));
                        cnt++;
                    }
                }
                // script patterns
                for(int k=0;k<ad.scriptPatterns.size();k++) {
                    ScriptPattern sp=ad.scriptPatterns.get(k);
                    // TODO: use it
                    if(false) {
                        ScriptPatternF f=new ScriptPatternF(sp);
                        features.add(new WordOfPhraseF("value.script."+sp.toString(), f));
                        cnt++;
                    }
                }
            }
        }
        // update values of the word class feature wrt. the model's attributes
        WordClassificationF cf=(WordClassificationF) features.get(0);
        cf.setValues(attNameList);
        log.LG(Logger.USR,"Collected "+cnt+" features from model");
        return cnt;
    }

    /** Turns single word classes like "B-NAME" and "I-NAME" that have been set 
     *  by SampleIterator into multi-word SemAnnots like "NAME". 
     *  The new SemAnnots are added to the underlying document. */
    public void commitClasses() {
        if(doc.tokens.length!=wordClasses.length) {
            throw new IllegalArgumentException("doc tokens="+doc.tokens.length+" word classes="+wordClasses.length);
        }
        Classification last=null;
        int lastStartIdx=-1;
        double logSum=0;
        for(int i=0;i<doc.tokens.length;i++) {
            Classification c=wordClasses[i];
            if(!isBG(c)) {
                if(isBG(last)) {
                    lastStartIdx=i;
                }else if(last.cls.equals(c.cls)) {
                    ;
                }else {
                    SemAnnot sa=new SemAnnot(SemAnnot.TYPE_AV, lastStartIdx, i-1, null, -1, prefixCls(last.cls), null);
                    sa.setProb(Math.exp(logSum/sa.getLength()));
                    doc.addSemAnnot(sa);
                    logSum=0;
                    lastStartIdx=i;
                }
                logSum+=Math.log(c.conf);
            }else if(!isBG(last)) {
                SemAnnot sa=new SemAnnot(SemAnnot.TYPE_AV, lastStartIdx, i-1, null, -1, prefixCls(last.cls), null);
                sa.setProb(Math.exp(logSum/sa.getLength()));
                doc.addSemAnnot(sa);
                logSum=0;
                lastStartIdx=-1;
            }
            last=c;
        }
        if(!isBG(last)) {
            SemAnnot sa=new SemAnnot(SemAnnot.TYPE_AV, lastStartIdx, doc.tokens.length-1, null, -1, prefixCls(last.cls), null);
            sa.setProb(Math.exp(logSum/sa.getLength()));
            doc.addSemAnnot(sa);
        }
    }
    
    private boolean isBG(Classification c) {
        return (c==null || (c.cls.equals(ClassificationF.BG) && c.type==null));
    }
    
    private String prefixCls(String cls) {
        return csd.id + "." + cls;
    }
    
    /** Iterator over DataSamples implemented by WordSample (one per word). */
    class WordIterator implements SampleIterator {
        int idx;
        WordSample word;
        List<AnnotableFeature> spans;
        
        public WordIterator(byte filter) {
            ; // don't care about filter, need to write thesis
            idx=-1;
            spans=new LinkedList<AnnotableFeature>();
            word=new WordSample();
        }
        
        public void setClass(String className, double prob, SampleClassifier author) {
            if(idx==-1)
                throw new NoSuchElementException(String.valueOf(idx));
            if(className.equals(ClassificationF.BG)) {
                wordClasses[idx]=null; // or create Classification that will hold prob as well
            }else {
                wordClasses[idx]=new Classification(className, prob);
            }
        }

        public void setClasses(List<SampleClassification> nbestItems, int nbest, SampleClassifier author) {
            if(nbest>1 && nbestItems.size()>1) {
                throw new UnsupportedOperationException("nbest="+nbest);
            }
            SampleClassification sc=nbestItems.get(0);
            setClass(sc.getClassName(), sc.getConfidence(), author);
        }

        public boolean hasNext() {
            return idx+1<doc.tokens.length;
        }

        public DataSample next() {
            idx++;
            word.setFeatureValues();
            return word;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
        /** Sample representing just one word in document. */
        class WordSample implements DataSample {
            int[] fvals;
            
            public WordSample() {
                fvals=new int[features.size()];
            }
            
            public void setFeatureValues() {
                TokenAnnot ta=doc.tokens[idx];
                for(int i=0;i<fvals.length;i++) {
                    Feature f=features.get(i);
                    if(f==TokenIdF.getSingleton()) {
                        fvals[i]=ta.ti.getTokenId();
                    }else if(f==TokenLCF.getSingleton()) {
                        fvals[i]=ta.ti.getLCId();
                    }else if(f==TokenLemmaF.getSingleton()) {
                        fvals[i]=ta.ti.getLemmaId();
                    }else if(f==UnaccentedF.getSingleton()) {
                        fvals[i]=ta.ti.getUnaccentedId();
                    }else if(f==TokenCapF.getSingleton()) {
                        fvals[i]=ta.ti.intVals.get(TokenCapF.getSingleton().id);
                    }else if(f==TokenTypeF.getSingleton()) {
                        fvals[i]=ta.ti.intVals.get(TokenTypeF.getSingleton().id);
                    }else if(f instanceof WordClassificationF) {
                        WordClassificationF wcf=(WordClassificationF) f;
                        fvals[i]=getWordClass(ta, wcf, i);
                    }else if(f instanceof WordOfPhraseF) {
                        WordOfPhraseF wof=(WordOfPhraseF) f;
                        PhraseF pf=wof.getPhraseFeature();
                        fvals[i]=0;
                        if(pf instanceof TokenPatternF) {
                            TokenPattern pat=((TokenPatternF)pf).pat;
                            if(!isFeature(pat))
                                continue;
                            fvals[i]=getPatternFeatureValue(ta, pat, i);
                        }else if(pf instanceof ScriptPatternF) {
                            ScriptPattern pat=((ScriptPatternF)pf).pat;
                            // TODO: implement script pattern feature
                        }else if(pf instanceof AnnotationF) {
                            AnnotationF af=(AnnotationF)pf;
                            fvals[i]=getAnnotationFeatureValue(ta, af, i);
                        }else if(pf instanceof TokenNgramF) {
                            TokenNgramF tnf=(TokenNgramF)pf;
                            fvals[i]=getNgramFeatureValue(ta, tnf, i);
                        }else {
                            throw new IllegalArgumentException("Unknown phrase feature found in WordOfPhraseF: "+pf);
                        }
                    }else {
                        throw new IllegalArgumentException("Unknown feature: "+f.toString());
                    }
                }
                // fill in values for inner words of phrase features,
                // and remove Annotables that have just terminated:
                Iterator<AnnotableFeature> ie=spans.iterator();
                while(ie.hasNext()) {
                    AnnotableFeature af=ie.next();
                    if(idx==af.annotable.getStartIdx()) {
                        continue; // start already written
                    }
                    int fidx=af.fidx;
                    Feature f=features.get(fidx);
                    int val;
                    if(f instanceof WordClassificationF) {
                        val=((WordClassificationF)f).toValue(WordClassificationF.INNER+af.annotable.getModelElement().name);
                    }else {
                        val=WordOfPhraseF.INNERVAL;
                    }
                    fvals[fidx]=val;
                    int endIdx=af.annotable.getStartIdx()+af.annotable.getLength()-1;
                    if(idx==endIdx) {
                        ie.remove();
                    }else if(idx>endIdx) {
                        throw new IllegalArgumentException("Unexpected iterator usage: idx="+idx+" endIdx="+endIdx+" annot="+af.annotable+" start="+af.annotable.getStartIdx()+" len="+af.annotable.getLength());
                    }
                }
            }

            private int getNgramFeatureValue(TokenAnnot ta, TokenNgramF tnf, int featureIdx) {
                int fval=WordOfPhraseF.NONEVAL;
                NFInfo inf=nfMatches[idx];
                if(inf==null) {
                    return fval;
                }
                // ngram feature match starts, grouped by ngram lengths
                for(NFInfoMatch m: inf.matchStarts) {
                    for(TokenNgramF f: m.feats) {
                        if(f==tnf) {
                            fval=WordOfPhraseF.BEGINVAL;
                            if(m.len>1) {
                                spans.add(new AnnotableFeature(new NgramLabel(idx, m.len, tnf), featureIdx));
                            }
                        }
                    }
                }
                return fval;
            }

            private int getAnnotationFeatureValue(TokenAnnot ta, AnnotationF af, int featureIdx) {
                int fval=WordOfPhraseF.NONEVAL;
                if(ta.semAnnots==null) {
                    return fval;
                }
                for(int j=0;j<ta.semAnnots.size();j++) {
                    SemAnnot sa=ta.semAnnots.get(j);
                    if(sa.type!=af.type)
                        continue;
                    boolean match=false;
                    switch(sa.type) {
                    case SemAnnot.TYPE_CHUNK: // af.data=String, sa.data=String
                        if(af.data.equals(sa.data)) {
                            match=true;
                        }
                        break;
                    case SemAnnot.TYPE_INST: // af.data=ex.model.ClassDef, sa.data=iet.model.Instance
                        if(((ex.model.ClassDef)af.data).name.equals(
                                ((medieq.iet.model.Instance)sa.data).getClassDef().getName())) {
                            match=true;
                        }
                        break;
                    case SemAnnot.TYPE_AV: // af.data=ex.model.AttributeDef, sa.data=iet.model.AttributeValue
                        if(!csd.skipAnnotation(sa, (ClassificationF)features.get(0))) {
                            if(((ex.model.AttributeDef)af.data).name.equals(
                                    ((medieq.iet.model.AttributeValue)sa.data).getAttributeDef().getName())) {
                                match=true;
                                log.LG(Logger.WRN,"Set annotation feature according to label "+sa+": "+getDebugInfo());
                            }
                        }
                        break;
                    default:
                        log.LG(Logger.WRN,"Unknown SemAnnot type="+sa.type);
                    }
                    if(match) {
                        fval=WordOfPhraseF.BEGINVAL;
                        if(sa.getLength()>1) {
                            spans.add(new AnnotableFeature(sa, featureIdx));
                        }
                    }
                }
                return fval;
            }

            private int getPatternFeatureValue(TokenAnnot ta, TokenPattern pat, int featureIdx) {
                int fval=WordOfPhraseF.NONEVAL;
                PatMatch match=null;
                switch(pat.type) {
                case TokenPattern.PAT_VAL:
                    match=ta.findMatch(pat, PatMatch.FIND_START, 1, Integer.MAX_VALUE, null);
                    if(match!=null && match.getLength()>1) {
                        spans.add(new AnnotableFeature(match, featureIdx));
                    }
                    break;
                case TokenPattern.PAT_CTX_L:
                    if(idx>0) {
                        match=doc.tokens[idx-1].findMatch(pat, PatMatch.FIND_END, 1, Integer.MAX_VALUE, null);
                    }
                    break;
                case TokenPattern.PAT_CTX_R:
                    if(idx+1<doc.tokens.length) {
                        match=doc.tokens[idx+1].findMatch(pat, PatMatch.FIND_START, 1, Integer.MAX_VALUE, null);
                    }
                    break;
                case TokenPattern.PAT_CTX_LR:
                    log.LG(Logger.ERR,"LR TokenPattern as feature: not implemented");
                default:
                }
                if(match!=null) {
                    fval=WordOfPhraseF.BEGINVAL;
                }
                return fval;
            }
            
            private int getWordClass(TokenAnnot ta, WordClassificationF wcf, int featureIdx) {
                int cv=0;
                if(ta.semAnnots!=null) {
                    for(SemAnnot sa: ta.semAnnots) {
                        if(csd.skipAnnotation(sa, (ClassificationF)features.get(0))) {
                            continue;
                        }
                        if(sa.type==SemAnnot.TYPE_AV) {
                            String cls=((medieq.iet.model.AttributeValue)sa.data).getAttributeDef().getName();
                            String wcls=WordClassificationF.BEGIN+cls;
                            cv=wcf.toValue(wcls);
                            if(sa.getLength()>1) {
                                spans.add(new AnnotableFeature(sa, featureIdx));
                            }
                            break;
                        }
                    }
                }
                if(cv!=0) {
                    if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"ws.gold("+ta.token+")="+wcf.toString(cv));
                }
                return cv;
            }

            public String getClassification() {
                Classification c=wordClasses[idx];
                return (c!=null)? c.cls: null;
            }

            public Object getDebugInfo() {
                return Document.toStringDbg(doc.tokens, idx, 1, " ");
            }

            public int[] getFeatures() {
                return fvals;
            }

            public int getWeight() {
                return 1;
            }

            public String toString(byte filter, byte format) {
                StringBuffer b=new StringBuffer(4*fvals.length);
                b.append("word: ");
                for(int i=0;i<fvals.length;i++) {
                    if(i>0) {
                        b.append(",");
                    }
                    Feature f=features.get(i);
                    b.append(f.name);
                    b.append("=");
                    int val = fvals[i];
                    b.append(val);
                    if(f instanceof IntFeature) {
                        b.append(((IntFeature)f).toString(val));
                    }else if(f instanceof EnumFeature) {
                        b.append(((EnumFeature)f).toString(val));
                    }
                }
                return b.toString();
            }
            
            public String toString() {
                return toString((byte)0, (byte)0);
            }
        }
    }

    public boolean supportsWeightedSamples() {
        return false;
    }

    public List<Feature> getFeatures() {
        return features;
    }
}

/** Iterator that remembers index. */
class IdxIterator implements FeatureIterator {
    Iterator<Feature> fit;
    int idx=-1;
    
    public IdxIterator(Iterator<Feature> fit) {
        this.fit=fit;
    }
    
    public int getIdx() {
        return idx;
    }

    public boolean hasNext() {
        return fit.hasNext();
    }

    public Feature next() {
        idx++;
        return fit.next();
    }

    public void remove() {
        fit.remove();
    }
}

/** Represents decision of a classifier including confidence. */
class Classification {
    String cls;
    String type;
    double conf;
    
    public Classification(String wordClass, double conf) {
        String pc=WordClassificationF.getPhraseClass(wordClass);
        if(pc==null) {
            if(wordClass.equals(ClassificationF.BG)) {
                this.cls=cls.intern();
            }else {
                throw new IllegalArgumentException("Class name="+wordClass);
            }
        }else {
            char c=wordClass.charAt(0);
            switch(c) {
            case 'B': type=WordClassificationF.BEGIN; break;
            case 'I': type=WordClassificationF.INNER; break;
            default: throw new IllegalArgumentException(pc);
            }
            this.cls=pc.intern();
        }
        this.conf=conf;
    }
    public String toString() {
        return cls+":"+((type!=null)? type: "")+conf;
    }
}

class AnnotableFeature {
    Annotable annotable;
    int fidx;
    public AnnotableFeature(Annotable annotable, int fidx) {
        this.annotable=annotable;
        this.fidx=fidx;
    }
}

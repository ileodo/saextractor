// $Id: Ex.java 2034 2009-05-17 10:57:49Z labsky $
package ex.api;

//interfaces
import medieq.iet.model.*;

//Ex internals
import ex.ac.*;
import ex.parser.*;
import ex.reader.Document;
import ex.reader.SemAnnot;
import ex.model.*;
import ex.train.*;
import ex.dom.Postprocessor;
import ex.features.*;
import ex.util.search.Path;

//utils
import java.util.*;
import java.io.*;

import uep.data.AddableDataSet;
import uep.data.SampleSet;
import uep.util.Logger;
import uep.util.Options;
import uep.util.ConfigException;
import uep.util.CacheItem;
import uep.util.Util;

public class Ex implements medieq.iet.components.Engine, 
                           medieq.iet.components.InstanceExtractor,
                           medieq.iet.components.TrainableExtractor {
    // Engine extends Configurable, AttributeExtractor
    public static final String engineName="ex";
    
    static boolean trainAndDump=true;
    public static String defaultLogFile="ex.log";
    protected Logger log;
    protected Options o;
    protected FM fm;

    // task data
    protected medieq.iet.model.Task currTask;
    protected Model currModel;
    protected KB modelKb;
    protected ex.reader.Document currDoc;
    protected medieq.iet.model.Document ietDoc;

    // ex components
    protected ModelReader modelReader;
    protected ex.reader.DocumentReader docReader;
    protected ModelMatcher modelMatcher;
    protected ACFinder acFinder;

    // parser currently at work
    Parser prs;
    
    // data source to be used by trained classifiers
    // DataSource dataSource; // moved to each classifier
    ArrayList<ClassifierDef> readyClassifiers;
    int dumpDataSrc; // sample format to dump each document
    boolean trainMode; // whether we are training or testing the classifiers
    
    // current DataModel supplied by IET
    protected medieq.iet.model.DataModel dataModel; 

    // label used to give name to current activities - used for dumping and logging
    protected String dumpName = "anonymous";
    
    public Ex() {
        o=Options.getOptionsInstance();
    }

    public boolean initialize(String cfgFile) {
        boolean rc=true;
        if(cfgFile!=null) {
            try {
                o.load(new FileInputStream(cfgFile));
            }catch(Exception ex) { 
                Logger.LOG(Logger.WRN,"Cannot find "+cfgFile+": "+ex.getMessage());
                Logger.init(defaultLogFile, Logger.WRN, 0, "iso-8859-2");
                rc=false;
            }
        }
        Logger.init(defaultLogFile, -1, -1, null); // ignored if already initialized, otherwise gets params from config
        log=Logger.getLogger("ex");
        fm=FM.getFMInstance();

        try {
            docReader=new ex.reader.DocumentReader();
            modelReader=new ModelReader();
            modelMatcher=new ModelMatcher();
            acFinder=new ACFinder();
        }catch(ConfigException ex) {
            log.LG(Logger.ERR,"Cannot instantiate DocumentReader: "+ex);
            rc=false;
        }
        readyClassifiers=new ArrayList<ClassifierDef>(8);
        dumpDataSrc=0;
        trainMode=false;
        return rc;
    }

    public void uninitialize() {
        fm.deinit();
    }

    public int loadModel(String modelFile) {
        currModel=loadGetModel(modelFile);
        if(currModel!=null && currModel.hasClassifiers()) {
            initClassifiers();
        }
        int rc=(currModel!=null)? 0: -1;
        return rc;
    }

    /** Initializes classifiers defined by the model. */
    private void initClassifiers() {
        trainMode="train".equalsIgnoreCase(o.getProperty("classifier_mode"));
        for(ClassifierDef csd: currModel.classifiers) {
            csd.canClassify=false;
            SampleClassifier samec=null;
            try {
                samec=(SampleClassifier) Class.forName(csd.name).newInstance();
                for(Map.Entry<String, String> p: csd.params.entrySet()) {
                    samec.setParam(p.getKey(), p.getValue());
                }
            }catch(Exception e) {
                coredump(e, "Cannot instantiate classifier connector "+csd.name+": "+e);
            }
            if(trainMode) {
                try {
                    samec.newClassifier();
                    log.LG(Logger.USR,"Train mode: Created new empty classifier "+csd.id+": "+csd.name);
                }catch(Exception e) {
                    coredump(e, "Error creating new empty classifier "+csd.id+": "+csd.name+": "+e);
                    samec=null;
                }
            }else {
                try {
                    samec.loadClassifier(csd.modelFile);
                    csd.canClassify=true;
                    log.LG(Logger.USR,"Test mode: Loaded trained classifier from "+csd.modelFile);
                }catch(Exception e) {
                    coredump(e, "Error loading classifier from file "+csd.modelFile+":"+e);
                }
            }

            if(samec!=null) {
                if(csd.options!=null && csd.options.length()>0) {
                    try {
                        String[] opts=csd.options.trim().split("\\s+[\r\n]\\s+");
                        samec.setClassifierOptions(opts);
                    }catch(Exception e) {
                        coredump(e, "Error setting options "+csd.options+": "+e);
                        samec=null;
                    }
                }
            }

            updateClassifierDataSource(csd);
            csd.samec=samec;
        }

        updateReadyClassifiers();
        dumpDataSrc=o.getIntDefault("dump_samples", 0);
    }
    
    private void updateReadyClassifiers() {
        readyClassifiers.clear();
        for(ClassifierDef csd: currModel.classifiers) {
            if(csd.canClassify) {
                readyClassifiers.add(csd);
            }
        }
    }
    
    /** Updates data source and weka's dataset to account for the new feature counts etc. */
    private void updateClassifierDataSource(ClassifierDef csd) {
        DataSource dataSource=null;        
//        switch(csd.representation) {
//        case ClassifierDef.REPR_PHRASE:
//            dataSource=new PhraseSource(currModel, csd);
//            break;
//        case ClassifierDef.REPR_GAP:
//            throw new UnsupportedOperationException("Gap classifier mode not implemented");
//        case ClassifierDef.REPR_WORD:
//            dataSource=new WordSource(currModel, csd);
//            break;
//        default:
//            throw new IllegalArgumentException("Illegal document representation "+csd.representation+" specified for classifier "+csd);
//        }
        try {
            dataSource=(DataSource) Class.forName(csd.representation).newInstance();
            dataSource.initialize(currModel, csd);
        }catch(Exception e) {
            coredump(e, "Cannot instantiate classifier connector "+csd.name+": "+e);
        }
        csd.setDataSource(dataSource);
        if(csd.samec!=null) {
            csd.samec.initEmptyDataSet(csd.getDataSource(), SemAnnot.TYPE_CHUNK);
        }
    }

    public String getModel() {
        return (currModel!=null)? currModel.fileName: null;
    }

    public Model loadGetModel(String modelFile) {
        // init empty KB
        modelKb=new KB("ModelKB",1000,5000);
        fm.registerKB(modelKb);

        // load new model
        if(modelFile==null) { // try to get one from options
            modelFile=o.getProperty("model_file");
            if(modelFile!=null) {
                log.LG(Logger.USR,"loadModel: Using default model "+modelFile);
            }else {
                log.LG(Logger.WRN,"loadModel: no model specified");
                return null;
            }

        }
        // ex.model.ClassDef.resetId();
        Model m=null;
        try {
            m=modelReader.read(modelFile, modelKb);
        }catch(org.xml.sax.SAXException sex) {
            log.LG(Logger.ERR,"Error XML parsing model "+modelFile+": "+sex.getMessage());
        }catch(ModelException mex) {
            log.LG(Logger.ERR,"Error reading model "+modelFile+": "+mex.getMessage());
            mex.printStackTrace();
        }catch(java.io.IOException iex) {
            log.LG(Logger.ERR,"Cannot open model "+modelFile+": "+iex.getMessage());
        }
        if(m!=null)
            m.fileName=modelFile;

        return m;
    }

    public String getName() {
        return engineName;
    }

    public boolean cancel(int cancelType) {
        boolean rc=false;
        synchronized(this) {
            if(prs!=null) {
                rc=prs.interrupt(cancelType);
            }
        }
        return rc;
    }

    /* Configurable interface */
    public Object getParam(String name) {
        return o.getString(name);
    }

    public void setParam(String name, Object value) {
        if(value!=null)
            value=value.toString();
        o.setProperty(name,(String)value);
    }

    public void configure(Properties params) {
        o.putAll(params);
    }

    public void configure(InputStream is) {
        try {
            o.load(is);
        }catch(IOException ex) {
            log.LG(Logger.WRN,"Error reading configuration: "+ex);
        }
    }

    private void checkModel() {
        if(currModel==null) {
            String msg="Extraction model not specified: cannot start extraction";
            log.LG(Logger.ERR,msg);
            throw new IllegalArgumentException(msg);
        }
    }

    /* AttributeExtractor interface */
    public int extractAttributes(medieq.iet.model.Document doc, DataModel model) {
        checkModel();
        log.LG(Logger.USR,"extractAttributes "+currModel.name+" from "+doc);
        Util.logMemStats(log, "extractAttributes");
        dataModel=model;
        // currModel has been set by loadModel
        currDoc=readDocument(doc, model);
        if(currDoc==null)
            return -1;
        int acCnt=matchPatternsCreateACs(currDoc, currModel, TokenPattern.PATTERN_ALL);
        
        int acCnt2=applyClassifiers(currDoc, currModel);
        return acCnt + acCnt2;
    }
    
    public int extractAttributes(DocumentSet docSet, DataModel model) {
        checkModel();
        log.LG(Logger.INF,"extractAttributes "+currModel.name+" from document set "+docSet);
        int cnt=0;
        for(int i=0;i<docSet.size();i++) {
            cnt+=extractAttributes(docSet.getDocuments().get(i), model);
        }
        return cnt;
    }

    /* InstanceExtractor interface */
    public int extractInstances(medieq.iet.model.Document doc, DataModel model) {
        checkModel();
        log.LG(Logger.USR,"extractInstances "+currModel.name+" from "+doc);
        Util.logMemStats(log, "extractInstances");
        dataModel=model;
        // currModel has been set by loadModel
        currDoc=readDocument(doc, model);
        if(currDoc==null)
            return -1;
        int acCnt=matchPatternsCreateACs(currDoc, currModel, 0);

        int hitCnt=applyClassifiers(currDoc, currModel);

        // classifiers might have added some labels; re-run AC generation for these
        int acCnt2=matchPatternsCreateACs(currDoc, currModel, TokenPattern.PATTERN_WITH_LABELS);

        // match class-level patterns; matches used to set IC scores when created
        int icPatMatches=modelMatcher.matchModelPatterns(currModel, currDoc, ModelMatcher.USE_INST_PATTERNS, TokenPattern.PATTERN_ALL, null);
        log.LG(Logger.INF,"Model '"+model.getName()+"' instance pattern matches: "+icPatMatches);
        
        ArrayList<Path> paths=new ArrayList<Path>(10);
        int icCnt=parseInstances(currDoc, currModel, paths);
        
        // 1. fill-in annotated source to iet document
        // 2. dump it to .lab.html file,
        // 3. add extracted objects to ex document
        // 4. invoke post-processing finalization rules on ex document
        // 5. copy post-processed extracted objects from ex document to iet document 
        Postprocessor poprc=new Postprocessor();
        poprc.updateDocuments(currModel, model, currDoc, doc, paths);
        
        // cleanup: clear parser's document-specific member variables and garbage collect
        synchronized(this) {
            prs.clear();
            prs=null;
        }
        Util.logMemStats(log, "Before GC");
        Runtime.getRuntime().gc();
        Util.logMemStats(log, "After  GC");
        return icCnt;
    }

    public int extractInstances(DocumentSet docSet, DataModel model) {
        checkModel();
        log.LG(Logger.INF,"extractInstances "+currModel.name+" from document set "+docSet);
        int cnt=0;
        for(int i=0;i<docSet.size();i++) {
            cnt+=extractInstances(docSet.getDocuments().get(i), model);
        }
        return cnt;
    }
    
    private void setDocument(medieq.iet.model.Document doc, DataModel model) {
        if(model==dataModel && doc==ietDoc) {
            log.LG(Logger.WRN, "Using cached processed version of document "+ietDoc);
        }else {
            dataModel=model;
            // currModel has been set by loadModel
            currDoc=readDocument(doc, model);
            if(currDoc==null)
                throw new IllegalArgumentException("Could not read document "+doc);
            int acCnt=matchPatternsCreateACs(currDoc, currModel, TokenPattern.PATTERN_ALL);
        }
    }

    private ClassifierDef getFIClassifierByIdx(int fiClassifierIdx) {
        int j=0;
        for(int i=0; i<currModel.classifiers.size() && i<fiClassifierIdx; i++) {
            ClassifierDef csd = currModel.classifiers.get(i);
            if(csd.supportsFeatureInduction()) {
                if(j==fiClassifierIdx) {
                    return csd;
                }else {
                    j++;
                }
            }
        }
        throw new IllegalArgumentException("FI-enabled classifier idx="+fiClassifierIdx+" not found; classifiers="+currModel.classifiers.size());
    }
    
    /** {@inheritDoc} */
    public SampleSet createEmptySampleSet(String name, int classifierIdx) {
        checkModel();
        ClassifierDef csd = currModel.classifiers.get(classifierIdx);
        SampleSet sset = SampleSetFactory.initEmptyDataSet(csd.getDataSource(), name+"_"+csd.id);
        // DataSourceWriter dsw = new DataSourceWriter(csd.getDataSource(), SemAnnot.TYPE_CHUNK);
        // SampleSet sset = dsw.createSampleSet();
        // sset.setName(name+"_"+csd.id);
        return sset;
    }
    
    /** {@inheritDoc} */
    public AddableDataSet createEmptyFISet(String name, int fiClassifierIdx) {
        checkModel();
        // empty ngram book is independent of classifier:
        NgramBook<NgramInfo, NgramInfo> ngrams = 
            new NgramBook<NgramInfo, NgramInfo>(name, new CountPhraseBookAdapter());
        return ngrams;
    }
    
    /** {@inheritDoc} */
    public void dumpSamples(medieq.iet.model.Document doc, DataModel model, List<SampleSet> sampleSets) {
        checkModel();
        String msg="dumping samples for doc "+doc;
        log.LG(Logger.USR, msg);
        Util.logMemStats(log, msg);
        if(currModel.classifiers.size()==0) {
            log.LG(Logger.ERR,"No classifiers to dump data for");
            return;
        }
        setDocument(doc, model);
        
        // dump samples for this doc, for each classifier separately
        int csdIdx=0;
        for(ClassifierDef csd: currModel.classifiers) {
            // link classifier to document, create DataSourceWriter to turn document into SampleSet
            csd.getDataSource().setDocument(currDoc);
            DataSourceWriter dsw=new DataSourceWriter(csd.getDataSource(), SemAnnot.TYPE_CHUNK);
            
            // dump samples of this document to disk
            if(o.getIntDefault("dump_separate_docs", 0) > 0) {
                byte fmt=(dumpDataSrc>0)? ((byte)dumpDataSrc): DataSourceWriter.FMT_XRFF;
                try {
                    dsw.dump(currDoc.cacheItem.absUrl+"."+DataSourceWriter.getFmtExt(fmt), (byte)dumpDataSrc);
                }catch(IOException ex) {
                    log.LG(Logger.ERR,"Error dumping data source "+currDoc.cacheItem.absUrl);
                }
            }
            
            // accumulate samples for training/testing and n-grams for feature induction,
            // store either into a remote or local SampleSet
            SampleSet accumSamples;
            if(sampleSets!=null) { // remotely: let IET handle SampleSets
                if(csdIdx < sampleSets.size()) {
                    accumSamples = sampleSets.get(csdIdx);
                }else if(csdIdx == sampleSets.size()) {
                    accumSamples = SampleSetFactory.initEmptyDataSet(csd.getDataSource(), "rcum_"+dumpName);
                    sampleSets.add(accumSamples);
                }else {
                    throw new IllegalArgumentException("Invalid SampleSet list size "+sampleSets.size()+", classifiers="+currModel.classifiers.size());
                }
            }else { // locally: accumulate samples as part of ClassifierDef
                if(csd.getSampleSet()==null) {
                    accumSamples = SampleSetFactory.initEmptyDataSet(csd.getDataSource(), "lcum_"+dumpName);
                    csd.setSampleSet(accumSamples);
                }else {
                    accumSamples = csd.getSampleSet();
                }
            }
            // adds all samples from data source to sample set
            SampleSetFactory.addToSampleSet(csd.getDataSource(), accumSamples, SemAnnot.TYPE_CHUNK);
            
            csdIdx++;
        }
    }
    
    /** {@inheritDoc} */
    public void dumpFIData(medieq.iet.model.Document doc, DataModel model, List<AddableDataSet> fiSets) {
        checkModel();
        String msg="dumping FI data for doc "+doc;
        log.LG(Logger.USR, msg);
        Util.logMemStats(log, msg);
        if(currModel.classifiers.size()==0) {
            log.LG(Logger.ERR,"No classifiers to dump FI data for");
            return;
        }
        setDocument(doc, model);
        
        // dump FI data for this doc, for each classifier separately
        int csdIdx=0;
        int fiSetIdx=0;
        for(ClassifierDef csd: currModel.classifiers) {
            // link classifier to document, create DataSourceWriter to turn document into SampleSet
            csd.getDataSource().setDocument(currDoc);
            
            // accumulate n-grams for feature induction,
            // store either into a remote or local NgramBook:
            if(csd.supportsFeatureInduction()) {
                PhraseBook<NgramInfo, NgramInfo> accumNgrams;
                if(fiSets!=null) { // remotely: let IET handle NgramBooks
                    if(fiSetIdx < fiSets.size()) {
                        accumNgrams = (PhraseBook<NgramInfo, NgramInfo>) fiSets.get(fiSetIdx);
                        accumNgrams.setName(accumNgrams.getName()+"_d"+doc.getId());
                    }else if(fiSetIdx == fiSets.size()) {
                        accumNgrams = new NgramBook<NgramInfo, NgramInfo>(csd.id+"_"+dumpName+"_d"+doc.getId(), 
                                new CountPhraseBookAdapter());
                        fiSets.add((AddableDataSet) accumNgrams);
                    }else {
                        throw new IllegalArgumentException("Invalid AddableDataSet list size "+fiSets.size()+", classifiers="+currModel.classifiers.size());
                    }
                    fiSetIdx++;
                }else { // locally: accumulate ngrams as part of ClassifierDef
                    accumNgrams = csd.getNgramBook();
                }
                modelMatcher.collectNgrams(currDoc, accumNgrams, csd);
                if(true) {
                    PhraseBookWriter w=new PhraseBookWriterImpl();
                    try {
                        w.write(accumNgrams, accumNgrams.getName()+".ngram");
                    }catch(IOException ex) {
                        log.LGERR("Error dumping FI dataset: "+ex);
                    }
                }
            }
            csdIdx++;
        }
    }    

    /** {@inheritDoc} */
    public void dumpSamples(DocumentSet docSet, DataModel model, List<SampleSet> sampleSets) {
        checkModel();
        log.LG(Logger.INF,"dumping samples document set "+docSet);
        for(int i=0;i<docSet.size();i++) {
            dumpSamples(docSet.getDocuments().get(i), model, sampleSets);
        }
        // to be called separately:
        // dumpSamplesCumulative();
        // clearClassifierSampleSets();
    }
    
    /** {@inheritDoc} */
    public void dumpFIData(DocumentSet docSet, DataModel model, List<AddableDataSet> fiSets) {
        checkModel();
        log.LG(Logger.INF,"dumping feature induction data for document set "+docSet);
        for(int i=0;i<docSet.size();i++) {
            dumpFIData(docSet.getDocuments().get(i), model, fiSets);
        }
        // to be called separately:
        // dumpFIDataCumulative();
        // clearClassifierFIData();
    }
    
    /** {@inheritDoc} */
    public void dumpSamplesCumulative(List<SampleSet> sampleSets) {
        checkModel();
        if(sampleSets!=null && sampleSets.size()!=currModel.classifiers.size()) {
            throw new IllegalArgumentException("Sample sets="+sampleSets.size()+", classifiers="+currModel.classifiers.size());
        }
        int csdIdx=0;
        // dump samples for this doc, for each classifier separately
        for(ClassifierDef csd: currModel.classifiers) {
            String fnBase="./_all_"+csd.id;
            // dump classifier samples: take from remote or local storage:
            SampleSet samples=null;
            if(sampleSets!=null) {
                samples = sampleSets.get(csdIdx);
                fnBase+= "_"+samples.getName();
            }else {
                samples = csd.getSampleSet();
            }
            if(samples!=null) {
                dumpAllSamples(samples, fnBase+".xrff");
            }else {
                throw new IllegalArgumentException("No sample set to dump for classifier idx="+csdIdx+": "+csd);
            }
            csdIdx++;
        }
    }

    /** {@inheritDoc} */
    public void dumpFIDataCumulative(List<AddableDataSet> fiSets) {
        checkModel();
        int fiSetIdx=0;
        // dump samples for this doc, for each classifier separately
        for(ClassifierDef csd: currModel.classifiers) {
            String fnBase="./_all_"+csd.id;
            // dump ngram book
            PhraseBook<NgramInfo, NgramInfo> ngrams=null;
            if(fiSets!=null) {
                if(fiSetIdx < fiSets.size()) {
                    ngrams = (PhraseBook<NgramInfo, NgramInfo>) fiSets.get(fiSetIdx);
                    fnBase+= "_"+ngrams.getName();
                }else {
                    throw new IllegalArgumentException("Invalid AddableDataSet list size "+fiSets.size()+", classifiers="+currModel.classifiers.size());                    
                }
                fiSetIdx++;
            }else {
                ngrams=csd.getNgramBook();
            }
            if(ngrams!=null) {
                dumpNgrams(ngrams, fnBase+".ngram");
            }
        }
    }
    
    /** {@inheritDoc} */
    public boolean supportsFeatureInduction() {
        checkModel();
        for(ClassifierDef csd: currModel.classifiers) {
            if(csd.supportsFeatureInduction())
                return true;
        }
        return false;
    }
    
    /** {@inheritDoc} */
    public int train(medieq.iet.model.Document doc, DataModel model) {
        checkModel();
        log.LG(Logger.USR,"training using doc "+doc);
        Util.logMemStats(log,"train doc");
        if(currModel.classifiers.size()==0) {
            log.LG(Logger.ERR,"No classifiers to dump data for");
            return -1;
        }

        dataModel=model;
        // currModel has been set by loadModel
        currDoc=readDocument(doc, model);
        if(currDoc==null)
            return -1;
        int acCnt=matchPatternsCreateACs(currDoc, currModel, TokenPattern.PATTERN_ALL);

        // assemble training samples to directly train classifiers later
        for(ClassifierDef csd: currModel.classifiers) {
            csd.getDataSource().setDocument(currDoc);
            boolean useCache=false; // FIXME: was true
            if(csd.samec.getFeatureCount()==0 && csd.samec.getSampleCount()==0) {
                // before first document, initialize classifier with model's feature set
                csd.samec.initEmptyDataSet(csd.getDataSource(), SemAnnot.TYPE_CHUNK);
            }
            csd.samec.addSamples(csd.getDataSource(), SemAnnot.TYPE_CHUNK, useCache);
            if(trainAndDump) {
                modelMatcher.collectNgrams(currDoc, csd.getNgramBook(), csd);
                
                // assemble samples to store them for the whole collection
                if(csd.getSampleSet()==null) {
                    SampleSet accumSamples = SampleSetFactory.initEmptyDataSet(csd.getDataSource(), "tcum_"+dumpName);
                    csd.setSampleSet(accumSamples);
                }
                SampleSetFactory.addToSampleSet(csd.getDataSource(), csd.getSampleSet(), (byte)-1);
            }
        }
        return 0;
    }

    /** {@inheritDoc} */
    public int train(DocumentSet docSet, DataModel model) {
        checkModel();
        log.LG(Logger.INF,"training using document set "+docSet);
        int rc=0;
        for(int i=0;i<docSet.size();i++) {
            int rc2=train(docSet.getDocuments().get(i), model);
            if(rc2!=0) {
                rc=rc2;
            }
        }
        if(rc==0) {
            rc=trainCumulative();
        }
        return rc;
    }
    
    /** {@inheritDoc} */
    public int trainCumulative() {
        checkModel();
        if(trainAndDump) {
            dumpSamplesCumulative(null);
        }
        trainClassifiers(null);
        return 0;
    }
    
    /** {@inheritDoc} */
    public void trainClassifiers(List<SampleSet> sampleSets) {
        log.LG(Logger.INF,"trainClassifiers: classifier count="+currModel.classifiers.size());
        if(sampleSets!=null && sampleSets.size()!=currModel.classifiers.size()) {
            throw new IllegalArgumentException("Sample sets="+sampleSets.size()+", classifiers="+currModel.classifiers.size());
        }
        for(int csdIdx=0; csdIdx<currModel.classifiers.size(); csdIdx++) {
            ClassifierDef csd=currModel.classifiers.get(csdIdx);
            trainClassifierInternal(csd, (sampleSets!=null)? sampleSets.get(csdIdx): null, null);
        }
    }
    
    /** {@inheritDoc} */
    public void trainSingleClassifier(int classifierIdx, SampleSet sampleSet, SampleSet optionalTestSet) {
        if(classifierIdx<0 || classifierIdx>=currModel.classifiers.size()) {
            throw new IllegalArgumentException("Classifier idx="+classifierIdx+" does not exist");
        }
        ClassifierDef csd=currModel.classifiers.get(classifierIdx);
        trainClassifierInternal(csd, sampleSet, optionalTestSet);
    }
    
    /** Trains the specified classifier using the given training set. The trained
     *  model is kept as part of the classifier (overwrites existing) and also is written to file. */
    private void trainClassifierInternal(ClassifierDef csd, SampleSet trainingSet, SampleSet optionalTestSet) {
        if(trainingSet!=null) {
            log.LG(Logger.USR, "Using training set "+trainingSet);
            int useZeroFeatures=o.getIntDefault("use_zero_features", 1);
            if(useZeroFeatures>0) {
                // load samples directly, one by one
                boolean useCache=false;
                // only deletes instances not attributes:
                csd.samec.clearDataSet();
                // for weka, replaces the previous weka dataset with a new one: 
                csd.samec.initEmptyDataSet(csd.getDataSource(), SemAnnot.TYPE_CHUNK);
                // adds all samples from trainingSet to the empty weka dataset
                csd.samec.addSamples(trainingSet, useCache);
            }else {
                // save to a XRFF file
                String dataFile=trainingSet.getName()+".xrff";
                log.LG(Logger.USR,"Dumping training set "+dataFile+" before training");
                try {
                    trainingSet.writeXrff(dataFile, true);
                }catch(IOException ex) {
                    StringWriter wr=new StringWriter(256);
                    ex.printStackTrace(new PrintWriter(wr));
                    throw new IllegalArgumentException("Can't dump "+dataFile+": "+wr.toString());
                }
                // load the XRFF file into Weka dataset using Weka's loader or using our
                // loader which turns missing values into zeros:
                boolean treatMissingFeaturesAsZero=false;
                try {
                    csd.samec.loadSamples(dataFile, treatMissingFeaturesAsZero);
                }catch(IOException ex) {
                    StringWriter wr=new StringWriter(256);
                    ex.printStackTrace(new PrintWriter(wr));
                    throw new IllegalArgumentException("Can't load dataset from "+dataFile+": "+wr.toString());
                }
            }
        }else {
            log.LG(Logger.USR, "Using classifier's internal training set");
        }
        log.LG(Logger.USR, "Going to train classifier "+csd.id+"; samples="+csd.samec.getSampleCount()+
                " weightedsamples="+csd.samec.getWeightedSampleCount()+" features="+csd.samec.getFeatureCount());
        try {
            String modelToTrain=csd.modelFile+"_"+((trainingSet!=null)?trainingSet.getName():"trainingset");
            log.LG(Logger.USR, "Training classifier "+csd.id+" ("+modelToTrain+")");
            csd.samec.newClassifier();
            csd.samec.trainClassifier();
            csd.samec.saveClassifier(modelToTrain);
            // mark this classifier as ready to use for testing:
            csd.canClassify=true;
            updateReadyClassifiers();
            int sz=0;
            File f=new File(modelToTrain);
            if(f.exists())
                sz=(int)f.length();
            log.LG(Logger.USR, "Trained classifier saved as "+modelToTrain+"; size="+sz);
        }catch(Exception e) {
            String msg="Error training classifier "+csd.id+": "+e;
            coredump(e, msg);
            throw new IllegalArgumentException(msg);
        }
        csd.samec.clearDataSet();
//        if(optionalTestSet!=null) {
//            boolean useCache=false;
//            csd.samec.addSamples(optionalTestSet, useCache);
//            //csd.samec.dumpSamples(csd.modelFile+"_"+trainingSet.getName()+"_test_set");
//            try {
//                csd.samec.classifyCurrentDataSet();
//            }catch(Exception e) {
//                String msg="Error testing classifier "+csd.id+": "+e;
//                coredump(e, msg);
//                //throw new IllegalArgumentException(msg);
//            }
//            csd.samec.clearDataSet();
//        }
    }
    
    /** Clears sample sets stored with each classifier definition. */
    public void clearClassifierSampleSets() {
        for(ClassifierDef csd: currModel.classifiers) {
            if(csd.getSampleSet()!=null) {
                csd.getSampleSet().clear();
                csd.setSampleSet(null);
            }
        }
    }
    
    /** Clears sample sets stored with each classifier definition. */
    public void clearClassifierFIData() {
        for(ClassifierDef csd: currModel.classifiers) {
            if(csd.getNgramBook()!=null) {
                csd.getNgramBook().clear();
            }
        }
    }
    
    /** {@inheritDoc} */
    public void induceFeaturesForClassifiers(List<AddableDataSet> fiSets) {
        log.LG(Logger.INF,"induceFeaturesForClassifiers: classifier count="+currModel.classifiers.size());
        if(fiSets!=null && fiSets.size()>currModel.classifiers.size()) {
            throw new IllegalArgumentException("Data sets="+fiSets.size()+", classifiers="+currModel.classifiers.size());
        }
        int fiSetIdx=0;
        for(int csdIdx=0; csdIdx<currModel.classifiers.size(); csdIdx++) {
            ClassifierDef csd=currModel.classifiers.get(csdIdx);
            if(csd.supportsFeatureInduction()) {
                induceFeaturesForClassifier(csdIdx, (fiSets!=null)? fiSets.get(fiSetIdx): null);
                fiSetIdx++;
            }
        }
    }
    
    /** {@inheritDoc} */
    public void induceFeaturesForClassifier(int classifierIdx, AddableDataSet dataSet) {
        // check that the classifier exists so we have where to store the induced feature book
        if(classifierIdx<0 || classifierIdx>=currModel.classifiers.size()) {
            throw new IllegalArgumentException("Classifier idx="+classifierIdx+" does not exist");
        }
        ClassifierDef csd=currModel.classifiers.get(classifierIdx);
        
        boolean clusterFeatures=true;
        boolean debug=false;
        FeatureDef nf=null;
        int nfCnt=0;
        for(FeatureDef f: csd.features) {
            if(f.type==FeatureDef.TYPE_NGRAM) {
                nf=f;
                if(nfCnt!=0) {
                    throw new UnsupportedOperationException("Only 1 NGRAM book per classifier supported for automated induction");
                }
                nfCnt++;

            }
        }
        if(nf==null) {
            throw new UnsupportedOperationException("Classifier "+csd+" does not support feature induction");
        }
        
        NgramFeatureGen gen=new NgramFeatureGen();
        gen.setMaxFcnt(nf.maxCnt); // 50000
        gen.setMinMi(nf.miHi); // 5
        gen.setMinNgramOccCnt(nf.minOcc); // 2
        
        PhraseBook<NgramInfo,NgramInfo> ngrams = (PhraseBook<NgramInfo,NgramInfo>) dataSet;
        PhraseBookWriter w = new PhraseBookWriterImpl();
        NgramFeatureBook featBook = null;
        
        try {
            if(clusterFeatures) {
                log.LG(Logger.USR,"Generating classed features from "+ngrams);
                featBook=gen.createFeaturesClassed(ngrams, currModel);
                String fn=ngrams.getName()+"_clustered.fgram";
                log.LG(Logger.INF,"Writing "+fn);
                w.write(featBook, fn);

                if(debug) {
                    PhraseBook<List<FeatCand>,FeatCand> featBookClassedCand =
                        gen.createFeaturesClassedCand(ngrams, fm.model);
                    fn=ngrams.getName()+"_clustered_cand.fgram";
                    log.LG(Logger.INF,"Writing "+fn);
                    w.write(featBookClassedCand, fn);
                }
            }else {
                log.LG(Logger.USR,"Generating full features from "+ngrams);
                featBook=gen.createFeaturesFull(ngrams, fm.model);
                String fn=ngrams.getName()+"_full.fgram";
                log.LG(Logger.INF,"Writing "+fn);
                w.write(featBook, fn);

                if(debug) {
                    PhraseBook<List<FeatCand>,FeatCand> featBookFullCand =
                        gen.createFeaturesFullCand(ngrams, fm.model);
                    fn=ngrams.getName()+"_full_cand.fgram";
                    log.LG(Logger.INF,"Writing "+fn);            
                    w.write(featBookFullCand, fn);
                }
            }
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error writing feature book: "+ex);
        }
        
        // set (the only) ngram feature of this classifier
        nf.book = featBook;

        // update data source of this classifier to account for the newly added features 
        updateClassifierDataSource(csd);
    }

    protected boolean dumpNgrams(PhraseBook<NgramInfo, NgramInfo> ngramBook, String file) {
        boolean rc=false;
        if(ngramBook==null) {
            // throw new Exception("Nothing to dump ngrams from.");
            log.LG(Logger.ERR,"Nothing to dump ngrams from.");
            return rc;
        }
        PhraseBookWriter pw=new PhraseBookWriterImpl();
        log.LG(Logger.USR,"Dumping all ngrams to "+file);
        try {
            pw.write(ngramBook, file);
            rc=true;
        }catch(IOException ex) {
            log.LGERR("Error writing ngram book to "+file+": "+ex);
        }
        return rc;
    }
    
    protected boolean dumpAllSamples(SampleSet sampleSet, String file) {
        if(sampleSet==null) {
            // throw new Exception("Nothing to train or dump samples from.");
            log.LG(Logger.ERR,"Nothing to train or dump samples from.");
            return false;
        }
        boolean rc=true;
        // dump xrff/arff
        byte fmt=DataSourceWriter.FMT_XRFF;
        if(file==null) {
            file=o.getProperty("tmp_doc_dir", ".");
            if(!file.endsWith("/") && !file.endsWith("\\"))
                file+="/";
            file+="all."+DataSourceWriter.getFmtExt(fmt);
        }else {
            int i=-1;
            if((i=file.lastIndexOf("."))!=-1) {
                byte fmt2=DataSourceWriter.ext2fmt(file.substring(i+1));
                if(fmt2>0) {
                    fmt=fmt2;
                }
            }
        }
        log.LG(Logger.USR,"Dumping whole collection to "+file);
        try {
            sampleSet.writeXrff(file, true);
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error dumping data source to "+file);
            rc=false;
        }
        return rc;
    }
        
    private Document readDocument(medieq.iet.model.Document doc, DataModel ietModel) {
        this.ietDoc=doc;
        CacheItem citem=null;
        // if we don't have the source, ask IET to populate it first
        if(doc.getSource()==null || doc.getSource().length()==0) {
            try {
                doc.populateSource(ietModel);
            }catch(IOException ex) {
                log.LG(Logger.USR,"Ex reading document content...");
            }
        }
        // if we have the source, save a temporary copy of it,
        // otherwise read doc from local version, if specified, or from online version
        if(doc.getSource()!=null && doc.getSource().length()>0) {
            // do we really need the temp version? to be removed.
            // below, forceEnc must be true otherwise we might save in utf-8 and read in win-1250 due to a meta tag
            // CacheItem is 'clever'
            citem=docReader.cacheItemFromString(doc.getSource(), doc.getUrl(), null, 
                    doc.getEncoding(), true, doc.getContentType());
            if(citem==null) {
                log.LG(Logger.ERR,"Error creating temporary version of "+doc);
                return null;
            }
            // replace the read source by the original anyway:
            citem.data=doc.getSource();
        }
        else if(doc.getFile()!=null && doc.getFile().length()>0) {
            citem=docReader.cacheItemFromFile(doc.getFile(), doc.getContentType(), doc.getEncoding(), doc.getForceEncoding());
            if(citem==null) {
                log.LG(Logger.ERR,"Cannot open document from file: "+doc.getFile());
                return null;
            }
        }else if(doc.getUrl()!=null && doc.getUrl().length()>0) {
            citem=docReader.cacheItemFromInternet(doc.getUrl());
            if(citem==null) {
                log.LG(Logger.ERR,"Cannot open document from url: "+doc.getFile());
                return null;
            }
            doc.setFile(citem.cachedUrl);
        }
        
        ex.reader.Document exDoc=docReader.parseHtml(citem);
        if(exDoc==null) {
            log.LG(Logger.ERR,"Error parsing "+doc);
            return null;
        }
        exDoc.setTokenFeatures(modelKb.vocab);
        // Never touch the document source that came from IET, do not even populate it if it was null.
        // It's read only for us. 
        // parseHTML could have changed source e.g. by identifying that a wrong encoding was used to get the source.
        // doc.setSource(citem.data);
        
        // add known labels from 3rd parties
        int labCnt=docReader.addLabels(exDoc, doc);
        
        // this does not really belong here but it should always be done after reading a doc:
        currModel.setCurrentDocument(exDoc, ietModel);
        
        // test KB saving
        // modelKb.save("model_kb.ser");
        return exDoc;
    }
    
    private int matchPatternsCreateACs(Document doc, Model model, int patternFilter) {
        int filter = patternFilter | TokenPattern.PATTERN_WITHOUT_ACS;
        // match all attribute value and context patterns, mark PatMatches in document.tokens
        int patMatchCnt=modelMatcher.matchModelPatterns(model, doc, ModelMatcher.USE_ATTR_PATTERNS, filter, null);
        log.LG(Logger.USR,"Model '"+model.name+"' attribute pattern matches="+patMatchCnt+" filter="+filter);

        // estimate ACs with their cond. probabilities based on PatMatches found
        int acCnt=acFinder.findScoreACs(doc, modelMatcher, model, filter);
        log.LG(Logger.USR,"Model '"+model.name+"' ACs="+acCnt+" filter="+filter);

        int patMatchCnt2=0;
        int acCnt2=0;
        if(acCnt>0) {
            int filter2 = TokenPattern.PATTERN_WITH_ACS;
            // repeat for patterns containing just induced ACs:
            patMatchCnt2=modelMatcher.matchModelPatterns(model, doc, ModelMatcher.USE_ATTR_PATTERNS, filter2, null);
            log.LG(Logger.USR,"Model '"+model.name+"' attribute AC pattern matches="+patMatchCnt2+" filter2="+filter2);

            if(patMatchCnt2>0) {
                acCnt2=acFinder.findScoreACs(doc, modelMatcher, model, filter2);
                log.LG(Logger.USR,"Model '"+model.name+"' AC-induced ACs="+acCnt2+" filter2="+filter2);
            }
        }
        return acCnt+acCnt2;
    }

    private int applyClassifiers(Document doc, Model model) {
        int hitCnt=0;
        for(ClassifierDef csd: readyClassifiers) {
            if(csd.getDataSource()==null) {
                throw new IllegalArgumentException("Datasource not present for classifier "+csd);
            }
            
            // 1. dump x/arff file per document if ordered by cfg
            if(dumpDataSrc>0) {
                csd.getDataSource().setDocument(doc);
                DataSourceWriter dsw=new DataSourceWriter(csd.getDataSource(), SemAnnot.TYPE_CHUNK);
                try {
                    dsw.dump(doc.cacheItem.absUrl+"."+DataSourceWriter.getFmtExt((byte)dumpDataSrc), (byte)dumpDataSrc);
                }catch(IOException ex) {
                    log.LG(Logger.ERR,"Error dumping data source "+doc.cacheItem.absUrl);
                }
            }

            // 2. classify samples in data source
            csd.getDataSource().setDocument(doc);
            try {
                boolean useCache=false; 
                // currently cache stores all distinct instances (not to repeat their classification)
                // however this is too simplistic as there are too many distinct instances
                // so it is off until a better cache is implemented (of fixed size, based on occurrence counts)
                csd.samec.classifyDataSet(csd.getDataSource(), SemAnnot.TYPE_CHUNK, useCache, 0);
            }catch(Exception e) {
                coredump(e, "Error testing classifier: "+e);
            }
        }
        return hitCnt;
    }

    private void coredump(Exception e, String msg) {
        StringWriter wr=new StringWriter(256);
        e.printStackTrace(new PrintWriter(wr));
        log.LG(Logger.ERR, msg+"; coredump:\n"+wr.toString());
    }

    private int parseInstances(Document doc, Model model, ArrayList<Path> paths) {
        int nbest=o.getIntDefault("parser_nbest",1);
        Util.logMemStats(log, "Before parse");
        // parse instance candidates from AC lattice
        synchronized(this) { 
            // sync to protect the cancel method which calls prs.interrupt()  
            prs=Parser.getParser(Parser.PS_LR, model);
        }
        paths.clear();
        //prs.setMaxICs(5000); // max 5000 IC candidates to be generated
        //prs.setMaxParseTime(2*60*1000); // max. 2 minutes
        int icCnt=prs.parse(doc, nbest, paths);
        Util.logMemStats(log, "Parse ended");
        log.LG(Logger.INF,"Instances for '"+model.name+"' on best path: "+icCnt);
        return icCnt;
    }
    
    public String getDumpName() {
        return dumpName;
    }

    public void setDumpName(String dumpName) {
        this.dumpName = dumpName;
    }
    
    /* cmdline */
    public static void main(String[] args) {
        Ex e1=new Ex();
        e1.initialize(null);
        e1.uninitialize();
    }
}

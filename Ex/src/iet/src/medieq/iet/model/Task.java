// $Id: Task.java 2032 2009-05-17 10:54:16Z labsky $
package medieq.iet.model;

import java.util.*;
import java.io.*;

import uep.data.AddableDataSet;
import uep.data.SampleSet;
import uep.util.Logger;
import uep.util.Options;
import medieq.iet.generic.*;
import medieq.iet.api.IETApi;
import medieq.iet.api.IETException;
import medieq.iet.components.*;

public class Task implements Runnable {
    protected int id;
	protected String name;
	protected String file;
	protected String desc;
	protected Vector<DocumentSet> documents;
	protected Date lastRun;
	protected String modelUrl;
	protected DataModel model;
	//protected int extractedItems;
	protected boolean interrupt;
	protected List<TaskListener> listeners;
	protected Thread thread;
    protected Object userData;
    protected Logger log;
    
    protected IETApi iet;
    protected String tmpDir;
    protected String outDir;
    
    protected int curProcIdx;
    protected Proc curProc;
    protected List<Proc> procedures;

    protected int state;    
    public static final int STATE_UNKNOWN=0;
    public static final int STATE_IDLE=1;
    public static final int STATE_EXECUTING=2;
    public static final int STATE_STARTING=3;
    public static final int STATE_STOPPING=4;
    public static final int STATE_MAX=STATE_STOPPING;

    private static String[] stateNames={
        "unknown",
        "idle",
        "executing",
        "starting",
        "stopping",
    };
    
    protected int mode;
    public static final int MODE_TEST_INSTANCES=0;
    public static final int MODE_TEST_ATTRIBUTES=1;
    public static final int MODE_TRAIN=2;
    public static final int MODE_DUMP=3;
    public static final int MODE_CROSSVALIDATE=4;

    protected int pipeUnit;
    public static final int PIPE_UNIT_SET=1;
    public static final int PIPE_UNIT_DOC=2;
    protected static final String[] pipeUnitNames={"set","doc"};
    
    public static int nextId=0;

    // cross-validation members:
    protected List<CrossValidationFold> folds;
    protected int foldCount;
    protected int heldoutFoldCount;
    protected int induceFeatures;
    
    public class Proc {
        protected String engineName; // class name of the engine (or its wrapper)
        protected Configurable engine;
        protected Map<String,ProcParam> params;
        protected int lastProcessedIdx; // 0-based idx of the last document processed
        protected int extractedItems;
        protected EvalResult cumEvalResult;
        
        /** used to store samples for each classifier of engine when in dump mode. */
        List<SampleSet> dumpSampleSets;
        List<AddableDataSet> dumpFISets;
        /** contains information for each cross-validation fold (0..n-1) for the Engine of this procedure. */ 
        protected List<ProcFold> procFolds;

        public Proc(String engineName, int idx) {
            this.engineName=engineName;
            this.engine=null;
            params=new HashMap<String,ProcParam>(8);
            lastProcessedIdx=-1;
            extractedItems=0;
            cumEvalResult=new EvalResult(getName()+"-"+engineName+"("+idx+")");
            dumpSampleSets=new ArrayList<SampleSet>();
            dumpFISets=new ArrayList<AddableDataSet>();
            procFolds=null;
        }
        
        public String toString() {
            return "engine="+(engine!=null? engine.getName(): engineName)+" dataModel="+((model!=null)? model.getUrl(): "(null)")
                   +" ieModel="+params.get("model");
        }
        
        public Configurable getEngine() { return engine; }
        public void setEngine(Configurable engine) { this.engine=engine; }
        
        public String getEngineName() { return engineName; }
        public void setEngineName(String engineName) { this.engineName=engineName; }
        
        public void setParam(String name, String value) {
            params.put(name, new ProcParam(name, value));
        }
        public String getParam(String name) {
            ProcParam pp=params.get(name);
            return (pp!=null)? pp.value: null;
        }

        public String toXML() {
            StringBuffer b=new StringBuffer(256);
            b.append("<proc class=\""+engineName+"\" >\n");
            Iterator<ProcParam> pit=params.values().iterator();
            while(pit.hasNext()) {
                ProcParam pp=pit.next();
                // FIXME: should escape the below: 
                b.append(" <param name=\""+pp.name+"\">"+pp.value+"</param>\n");
            }
            b.append("</proc>\n");
            return b.toString();
        }
        
        protected void setEngineParameters() {
            lastProcessedIdx=-1;
            extractedItems=0;
            cumEvalResult.clear();
            
            for(ProcParam pp: params.values()) {
                if(engine==null) {
                    log.LG(Logger.ERR,"Engine "+engineName+" not present\n");
                    return;
                }
                try {
                    if(pp.name.equalsIgnoreCase("model") && engine instanceof Engine) {
                        Engine eng=(Engine) engine;
                        int rc=0;
                        if(eng.getModel()==null || !eng.getModel().equals(pp.value))
                            rc=eng.loadModel(pp.value);
                        if(rc!=0) {
                            log.LG(Logger.ERR,"Engine "+engine.getName()+" returned "+rc+" loading model "+pp.value);
                            setState(STATE_IDLE);
                            thread=null;
                            return;
                        }
                        Logger.pause("model loaded: "+pp.value);
                    }else {
                        engine.setParam(pp.name, pp.value);
                    }
                }catch (Exception e) {
                    StringWriter wr=new StringWriter(256);
                    e.printStackTrace(new PrintWriter(wr));
                    log.LG(Logger.ERR,"Engine "+engine.getName()+" threw exception while setting "+pp.name+"="+pp.value+":\n"+wr);
                    setState(STATE_IDLE);
                    thread=null;
                    return;
                }
            }
        }

        protected void processDocument(Document doc) {
            int cnt=0;
            try {
                if(engine instanceof Engine) {
                    Engine eng=(Engine) engine;
                    switch(mode) {
                    case MODE_TEST_ATTRIBUTES:
                        cnt=eng.extractAttributes(doc, model);
                        extractedItems+=cnt;
                        break;
                    case MODE_CROSSVALIDATE:
                    case MODE_TEST_INSTANCES:
                        if(eng instanceof InstanceExtractor) {
                            cnt=((InstanceExtractor)eng).extractInstances(doc, model);
                        }else {
                            cnt=eng.extractAttributes(doc, model);
                        }
                        extractedItems+=cnt;
                        break;
                    case MODE_TRAIN:
                        if(eng instanceof TrainableExtractor) {
                            ((TrainableExtractor)eng).train(doc, model);
                        }else {
                            log.LG(Logger.ERR, eng.getName()+" is not trainable - skipping its training phase.");
                        }
                        break;
                    case MODE_DUMP:
                        if(eng instanceof TrainableExtractor) {
                            ((TrainableExtractor)eng).train(doc, model);
                            TrainableExtractor te = (TrainableExtractor) eng;
                            te.dumpSamples(doc, model, dumpSampleSets);
                            te.dumpFIData(doc, model, dumpFISets);
                        }else {
                            log.LG(Logger.ERR, eng.getName()+" is not trainable - skipping its data dump phase.");
                        }
                        break;
                    }
                }else if(engine instanceof Evaluator) {
                    if(mode==MODE_TEST_INSTANCES || mode==MODE_TEST_ATTRIBUTES || mode==MODE_CROSSVALIDATE) {
                        Evaluator ev=(Evaluator) engine;
                        EvalResult perDoc=new EvalResult(doc.getId()+"/"+engine);
                        // TODO: make configurable
                        ev.setDocumentReader(new DatDocumentReader());
                        ev.setDataModel(model);
                        ev.eval(doc, doc, perDoc);
                        doc.setEvalResult(perDoc);
                        log.LG(Logger.INF, "Document performance("+ev+"):\n"+perDoc.toString());
                        cumEvalResult.add(perDoc);
                    }
                }else {
                    throw new IllegalArgumentException("Component "+engine.getName()+" is neither engine nor evaluator.");
                }
            } catch(Exception e) {
                StringWriter wr=new StringWriter(256);
                e.printStackTrace(new PrintWriter(wr));
                log.LG(Logger.ERR,"Exception processing doc "+doc.getUrl()+"/"+doc.getFile()+" engine="+engine.getName()+" model="+model.getUrl()+":\n"+e+"; coredump:\n"+wr);
            }
            lastProcessedIdx++;
            // documentProcessed(lastProcessedIdx, doc);
        }

        public void finishDocuments() {
            if(engine instanceof Engine) {
                try {
                    // String dumpFile=((dset.getBaseDir()!=null)? dset.getBaseDir(): ".") +"/_all.xrff";
                    switch(mode) {
                    case MODE_TRAIN:
                        if(engine instanceof TrainableExtractor) {
                            ((TrainableExtractor)curProc.engine).trainCumulative();
                        }else {
                            log.LG(Logger.ERR, engine.getName()+" is not trainable - skipping its cumulative training phase.");
                        }
                        break;
                    case MODE_DUMP:
                        if(engine instanceof TrainableExtractor) {
                            TrainableExtractor te = (TrainableExtractor) curProc.engine;
                            te.dumpSamplesCumulative(dumpSampleSets);
                            te.dumpFIDataCumulative(dumpFISets);
                        }else {                        
                            log.LG(Logger.ERR, engine.getName()+" is not trainable - skipping its cumulative data dump phase.");
                        }
                        break;
                    }
                } catch(Exception e) {
                    StringWriter wr=new StringWriter(256);
                    e.printStackTrace(new PrintWriter(wr));
                    log.LG(Logger.ERR,"Exception processing cumulative docs: engine="+curProc.engine.getName()+" model="+model.getUrl()+":\n"+e+"; coredump:\n"+wr.toString());
                    return;
                }
            }
            if(engine instanceof Evaluator) {
                // 1. compute micro-averaged results (p & r averaged over documents not extractable items)
                MicroResult micro = null;
                if(EvalResult.SHOW_MICRO>0) {
                    List<Document> allDocs = new ArrayList<Document>(64);
                    for(DocumentSet ds: documents) {
                        allDocs.addAll(ds.getDocuments());
                    }
                    micro = new MicroResult(name+"-microaveraged");
                    ((Evaluator)engine).getMicroResults(allDocs, micro);
                    allDocs.clear();
                    // String msg="Total micro performance("+engine+"):\n"+micro.toString();
                    // log.LG(Logger.USR, msg);
                }
                // dump as part of macro result table:
                cumEvalResult.setMicroResult(micro);
                
                // 2. dump cumulative, macro-averaged results if evaluator produced any
                if(!cumEvalResult.isEmpty()) {
                    String msg="Total macro performance("+engine+"):\n"+cumEvalResult.toString();
                    log.LG(Logger.USR, msg);
                    int flags = EvalAttRecord.PRINT_TABULAR | EvalAttRecord.PRINT_PERCENTAGES |
                        EvalAttRecord.PRINT_EXACT | EvalAttRecord.PRINT_LOOSE | 
                        EvalAttRecord.PRINT_GOLDCOUNTS | EvalAttRecord.PRINT_AUTOCOUNTS | 
                        EvalAttRecord.PRINT_AUTOMATCHCOUNTS | EvalAttRecord.PRINT_GOLDMATCHCOUNTS;
                    if(micro!=null) {
                        flags |= EvalAttRecord.PRINT_MICRO;
                    }
                    msg="Total performance tabular("+engine+"):\n"+cumEvalResult.toString(flags, 2);
                    log.LG(Logger.USR, msg);
                }
                
                // 3. dump errors
                boolean dumpEvalDetails=true;
                if(dumpEvalDetails) {
                    Map<AttributeDef,List<DocumentInt>> errDocs=new TreeMap<AttributeDef,List<DocumentInt>>();
                    for(DocumentSet ds: documents) {
                        for(Document d: ds.getDocuments()) {
                            EvalResult er=d.getEvalResult();
                            if(er!=null && er.attRecords!=null) {
                                for(EvalAttRecord ear: er.attRecords.values()) {
                                    int errCnt = ear.getErrorCount();
                                    if(errCnt>0) {
                                        List<DocumentInt> lst=errDocs.get(ear.ad);
                                        if(lst==null) { 
                                            lst=new LinkedList<DocumentInt>(); 
                                            errDocs.put(ear.ad, lst);
                                        }
                                        lst.add(new DocumentInt(d, errCnt));
                                    }
                                }
                            }
                        }
                    }
                    for(Map.Entry<AttributeDef,List<DocumentInt>> en: errDocs.entrySet()) {
                        StringBuffer b=new StringBuffer(512);
                        b.append("Erroneous documents for "+en.getKey().getName()+":\n");
                        List<DocumentInt> docLst=en.getValue();
                        Collections.sort(docLst, new Comparator<DocumentInt>() {
                            public int compare(DocumentInt a, DocumentInt b) {
                                return b.n - a.n;
                            }
                        });
                        for(DocumentInt d: en.getValue()) {
                            b.append("  "+d.d.getFile()+" ("+d.n+")\n");
                        }
                        log.LG(Logger.USR, b.toString());
                    }
                }
            }
        }
    }
    
	public Task(String name, IETApi iet) {
		this.name=name;
		this.iet=iet;
		this.documents=new Vector<DocumentSet>(16);
		this.lastRun=new Date();
        commonInit();
	}
	
	public Task(String name, Task task) {
		this.name=name;
		this.file=task.file;
		this.desc=task.desc;
		this.documents=new Vector<DocumentSet>(task.documents.size());
		this.documents.addAll(task.documents);
		this.lastRun=task.lastRun;
		this.model=task.model;
		//this.engine=task.engine;
        //this.engineName=task.engineName;
        commonInit();
	}
    
    private void commonInit() {
        this.state=STATE_IDLE;
        this.mode=MODE_TEST_INSTANCES;
        this.pipeUnit=PIPE_UNIT_DOC;
        //this.lastProcessedIdx=-1;
        //this.extractedItems=0;
        this.interrupt=false;
        this.listeners=new LinkedList<TaskListener>();
        procedures=new LinkedList<Proc>();
        folds=null;
        foldCount=0;
        heldoutFoldCount=0;
        induceFeatures=0;
        synchronized(this.getClass()) {
            id=nextId++;
        }
        log=Logger.getLogger("TSK"+id);
    }
    
    protected void addProcedure(Proc proc) {
        procedures.add(proc);
    }
	
	public boolean equals(Object anObject) {
		Task task=(Task) anObject;
		if(name!=null && name.equals(task.name))
		  return true;
		return false;
	}
	
	public String getName() { return name; }
	public void setName(String name) { this.name=name; }

	public String getFile() { return file; }
	public void setFile(String file) { this.file=file; }

	/** Returns all document sets contained in this Task. */
	public List<DocumentSet> getDocuments() { return documents; }
	
	public void addDocument(Document doc) {
		DocumentSet set=null;
		List<DocumentSet> sets=getDocuments();
		if(getDocuments().size()==0) {
			set=new DocumentSetImpl("default");
			getDocuments().add(set);
		}else {
			set=sets.get(sets.size()-1);
		}
		set.getDocuments().add(doc);
	}
	
	public Date getLastRun() { return lastRun; }
	public void setLastRun(Date lastRun) { this.lastRun=lastRun; }
	
	public int getState() { return state; }
	public synchronized void setState(int newState) {
		int oldState=state;
		state=newState;
		if(newState!=oldState) {
			TaskListener[] tmp=new TaskListener[listeners.size()]; // tmp hack
			listeners.toArray(tmp);
			for(int i=0;i<tmp.length;i++) {
				tmp[i].onStateChange(this, newState);
			}
			/*
			Iterator<TaskListener> it=listeners.iterator();
			while(it.hasNext()) {
				it.next().onStateChange(this,newState);
			}
			*/
		}
	}
	
	public String getDesc() { return desc; }
	public void setDesc(String desc) { this.desc=desc; }

    public String getTempDir() { return tmpDir; }
	public void setTempDir(String dir) { this.tmpDir=dir; }

    public String getOutDir() { return outDir; }
    public void setOutDir(String dir) { this.outDir=dir; }
	
	public DataModel getModel() { return model; }
	public void setModel(DataModel model) { this.model=model; }
	
	public String getModelUrl() { return modelUrl; }
	public void setModelUrl(String url) { modelUrl=url; }
	
    public Object getUserData() { return userData; }
    public void setUserData(Object userData) { this.userData=userData; }

	public int getDocumentCount() {
		int cnt=0;
		for(int i=0;i<documents.size();i++) {
			cnt+=documents.get(i).size();
		}
		return cnt;
	}
	
	public int setDocuments(List<Document> docList) {
		DocumentSet newSet=new DocumentSetImpl("document set");
		newSet.getDocuments().addAll(docList);
		documents=new Vector<DocumentSet>(8);
		documents.add(newSet);
		return newSet.size();
	}

	public int populateDocuments(List<Document> docList) {
		int cnt=0;
		for(int i=0;i<documents.size();i++) {
			docList.addAll(documents.get(i).getDocuments());
			cnt+=documents.get(i).size();
		}
		return cnt;
	}
	
	public String toString() { 
		return getName()+", state="+state2string(state)+", documents="+getDocumentCount()+", last run "+lastRun;
	}
	
	public static String state2string(int state) {
		if(state<STATE_UNKNOWN||state>STATE_MAX)
			state=STATE_UNKNOWN;
		return stateNames[state];
	}
	
	public boolean start() throws IETException {
	    String err=null;
        if(model==null) {
//            if(modelUrl!=null) {
//                model=DataModelFactory.readDataModelFromFile(modelUrl);
//            }else {
//                model=DataModelFactory.createEmptyDataModel(name);
//            }
            err="Cannot start task "+name+" without datamodel";
        }

	    if(state==STATE_IDLE || state==STATE_STARTING) {
	        if(procedures.size()==0) {
	            err="No procedures defined for task "+name;
	        }else {
	            Iterator<Proc> pit=procedures.iterator();
	            int i=0;
	            while(pit.hasNext()) {
	                i++;
	                Proc p=pit.next();
	                Configurable eng=p.engine;
	                if(eng==null) {
	                    String engineName=p.getEngineName();
	                    if(engineName==null || engineName.trim().length()==0) {
	                        String msg="Engine name for task "+getFile()+" procedure n."+i+" not specified";
	                        log.LG(Logger.ERR,msg);
	                        throw new IETException(msg);
	                    }
	                    eng=iet.getEngineByName(engineName);
	                    if(eng==null) {
	                        EngineFactory factory=new EngineFactory();
	                        eng=factory.createEngine(engineName);
	                        String cfgFile=p.getParam("cfg");
	                        try {
	                            eng.initialize(cfgFile);
	                        }catch(IOException ex) {
	                            String msg="Error initializing engine "+eng.getName()+"("+engineName+") with cfg="+cfgFile+": "+ex;
	                            log.LG(Logger.ERR,msg);
	                            throw new IETException(msg);
	                        }
	                        Logger.pause("engine "+engineName+" initialized cfg="+cfgFile);
	                    }
	                    p.setEngine(eng);
	                }
	            }
	        }
	    }else {
	        err="Cannot start task "+name+" in state="+state2string(state);
	    }
	    if(err!=null) {
			log.LG(Logger.ERR,err);
            throw new IETException(err);
		}
		thread=new Thread(this);
		thread.start();
		return true;
	}
	
	public void stop() {
		if(state==STATE_EXECUTING) {
			setState(STATE_STOPPING);
			synchronized(this) {
			    if(curProc!=null) {
			        boolean rc=curProc.engine.cancel(Engine.CANCEL_CURR_DOC);
			        if(!rc)
			            log.LG(Logger.ERR,"Cannot cancel engine "+curProc.engine.getName());
			    }
			}
			interrupt=true;
		}
	}
	
	public void run() {
		// process documents
        log.LG(Logger.INF,"Task "+getName()+": starting extraction from "+getDocumentCount()+" doc(s); "+procedures.size()+" procedure(s), mode="+mode);		
		interrupt=false;
		//extractedItems=0;
        curProc=null;
		curProcIdx=0;
		setState(STATE_EXECUTING);
		
		try {
		    // model will be loaded below if different from existing
		    if(modelUrl==null) { 
		        log.LG(Logger.ERR,"Cannot start task without model");
		        setState(STATE_IDLE);
		        thread=null;
		        return;
		    }

		    // 1. prepare
		    if(mode==MODE_TRAIN || mode==MODE_CROSSVALIDATE) {
		        // to let know anyone interested we are in training mode
		        Options.getOptionsInstance().setProperty("classifier_mode", "train");
		    }
		    if(mode==MODE_CROSSVALIDATE) {
		        createFolds();
		    }

		    // 2. set parameters and load models for all procedures first
		    for(; curProcIdx<procedures.size(); curProcIdx++) {
		        curProc=procedures.get(curProcIdx);
		        curProc.setEngineParameters();
		        if(state!=STATE_EXECUTING) {
		            return; // exception encountered while setting parameter
		        }
		    }

		    // 3. process docs from all document sets together without difference (so far).
		    // either:
		    // - push through pipe whole document set, 
		    // - push separate documents one-by-one,
		    // - do one of the above for cross-validation
            if(mode==MODE_CROSSVALIDATE) {
                // if feature induction is on, cross-validation gets complicated
                if(induceFeatures!=0) {
                    checkFISupport();
                    // collect feature induction data for each classifier that supports induction (per fold)
                    collectFoldSamples(-1, false, true, false);
                    // induce features using held-out or training folds and store them with each test fold,
                    // then for each test fold collect its training sample set directly from documents using
                    // the features stored with test fold, train classifier, finally test it on the test fold. 
                    processFoldsInduceFeatures();
                }else {
                    // collect per-fold samples just once for cross-validation
                    collectFoldSamples(-1, true, false, false);
                    // for each N-1 folds together, trains utilized classifiers and evaluates 
                    // using the rest, assumes some kind of Evaluator is the last engine
                    processFolds();
                }
            }else {
                if(pipeUnit==PIPE_UNIT_SET) {
                    processDocumentSets(documents);
                }else { // pipeUnit==PIPE_UNIT_DOC
                    processSeparateDocuments(documents);
                }
            }
        }catch(InterruptedException ex) {
            log.LG(Logger.INF,"Task "+getName()+" interrupted while processing procedure "+curProc);
        }catch(Exception ex) {
            StringWriter wr=new StringWriter(256);
            ex.printStackTrace(new PrintWriter(wr));
            log.LG(Logger.ERR,"Task \""+getName()+"\" encountered coredump while processing procedure "+curProc+":\n"+wr.toString());
        }
        
        // 5. finalize
        curProc=null;
        curProcIdx=-1;
        log.LG(Logger.INF,"Task "+getName()+": ended");
		setState(STATE_IDLE);
		thread=null;
	}
	
	/** Throws IllegalArgumentException if none of the engines' classifiers support feature induction. */
	private void checkFISupport() { 
	    int fiSupport=0;
	    for(Proc p: procedures) {
	        if(p.engine instanceof TrainableExtractor) {
	            if(((TrainableExtractor)p.engine).supportsFeatureInduction()) {
	                fiSupport++;
	            }
	        }
	    }
	    if(fiSupport==0) {
	        throw new IllegalArgumentException("None of the engines supports feature induction");
	    }
	}

	/** Each procedure processes all docs then we go to next proc */
	private void processDocumentSets(List<DocumentSet> docSets) throws InterruptedException {
	    for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
	        curProc=procedures.get(curProcIdx);
	        curProc.lastProcessedIdx=-1;
	        curProc.extractedItems=0;
	        for(DocumentSet dset: docSets) {
	            for(Document doc: dset.getDocuments()) {
	                if(interrupt)
	                    throw new InterruptedException();
	                curProc.processDocument(doc);
	                if(curProcIdx==procedures.size()-1)
	                    documentProcessed(curProc.lastProcessedIdx, doc);
	            }
	            curProc.finishDocuments();
	        }
	    }
	}

    /** each procedure processes 1 doc then we go to next proc, then next document etc. */
	private void processSeparateDocuments(List<DocumentSet> docSets) throws InterruptedException {
	    for(DocumentSet dset: docSets) {
	        int di=0;
	        for(Document doc: dset.getDocuments()) {
	            for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
	                curProc=procedures.get(curProcIdx);
	                if(interrupt)
	                    throw new InterruptedException();
	                curProc.processDocument(doc);
	            }
	            documentProcessed(di++, doc);
	        }
	    }
	    for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
	        curProc=procedures.get(curProcIdx);
	        if(interrupt)
	            throw new InterruptedException();
	        curProc.finishDocuments();
	    }
	}
	
    public synchronized void addListener(TaskListener l) {
		if(!listeners.contains(l))
			listeners.add(l);		
	}

	public synchronized void removeListener(TaskListener l) {
		listeners.remove(l);
	}
	
	protected synchronized void documentProcessed(int idx, Document doc) {
		TaskListener[] tmp=new TaskListener[listeners.size()]; // tmp hack
		listeners.toArray(tmp);
		for(int i=0;i<tmp.length;i++) {
			tmp[i].onDocumentProcessed(this, idx, doc);
		}
		/*
		Iterator<TaskListener> it=listeners.iterator();
		while(it.hasNext()) {
			it.next().onDocumentProcessed(this, idx, doc);
		}
		*/
	}

    public int getId() {
        return id;
    }

    public void setMode(int mode) {
        this.mode=mode;
    }
    
    public int getMode() {
        return mode;
    }

    public void setPipelineUnit(String value) throws IETException {
        if(value==null)
            return;
        value=value.trim().toLowerCase();
        if(value.equals("doc")||value.equals("document")) {
            pipeUnit=PIPE_UNIT_DOC;
        }else if(value.equals("set")) {
            pipeUnit=PIPE_UNIT_SET;
        }else {
            throw new IETException("Invalid pipeline unit="+value);
        }
    }
    
    public String getPipelineUnit() {
        return pipeUnitNames[pipeUnit-1];
    }
    
    public Proc addNewProc(String className) {
        Proc p=new Proc(className, procedures.size());
        procedures.add(p);
        return p;
    }
    
    public Proc getProc(int idx) {
        return procedures.get(idx);
    }
    
    public int getProcCount() {
        return procedures.size();
    }
    
    /** Populates cross-validation folds and creates engine-specific fold information. */
    protected void createFolds() {
        if(this.documents.size()==foldCount) {
            // use provided folds
            log.LG(Logger.USR, "Using "+foldCount+" provided document sets as folds ("+heldoutFoldCount+" held-out)");
            for(int i=0;i<foldCount;i++) {
                DocumentSet set=documents.get(i);
                if(set.getDocuments().size()==0) {
                    throw new IllegalArgumentException("Fold"+i+" is empty");                    
                }
                CrossValidationFold f=new CrossValidationFold(i, set);
                folds.add(f);
            }
        }else {
            // spill documents together and create own folds
            log.LG(Logger.USR, "Creating "+foldCount+" folds ("+heldoutFoldCount+" held-out)");
            ArrayList<Document> allDocs=new ArrayList<Document>();
            for(DocumentSet set: documents) {
                allDocs.addAll(set.getDocuments());
            }
            if(allDocs.size()<foldCount) {
                throw new IllegalArgumentException(foldCount+" is too many folds for "+documents.size()+" documents");
            }
            int docCntPerFold=allDocs.size()/foldCount;
            int docCntBonus=allDocs.size()%foldCount;
            folds=new ArrayList<CrossValidationFold>(foldCount);
            int offset=0;
            for(int i=0;i<foldCount;i++) {
                int endOffset=offset+docCntPerFold;
                if(docCntBonus>0) {
                    endOffset++;
                    docCntBonus--;
                }
                DocumentSet set=new DocumentSetImpl("fold_"+i);
                for(int j=offset; j<endOffset; j++) {
                    set.getDocuments().add(allDocs.get(j));
                }
                CrossValidationFold f=new CrossValidationFold(i, set);
                folds.add(f);
                offset=endOffset;
            }
        }
        
        // create engine-specific fold information
        for(int i=0; i<procedures.size(); i++) {
            Proc proc=procedures.get(i);
            if(proc.engine instanceof Engine &&
               proc.engine instanceof TrainableExtractor) {
                if(proc.procFolds==null) {
                    proc.procFolds=new ArrayList<ProcFold>(folds.size());
                    for(CrossValidationFold f: folds) {
                        proc.procFolds.add(new ProcFold(f));
                    }
                }
            }
        }
    }
    
    /** Accumulates samples for each cross-validation fold and stores them
     *  as engine-specific fold information. If feature induction is enabled 
     *  and held-out fold count > 0, this also collects feature induction statistics
     *  such as n-grams and stores them as engine-specific fold information.
     *  @param foldRole filter to process only e.g. training folds; -1 means process all folds.
     *  @param train whether to collect training samples 
     *  @param induce whether to collect feature induction samples 
     *  @param accum for each procedure, whether to store data separately for each fold or together across folds.
     *         Collected data will either be stored under engine-specific info, or under fold+engine-specific info. */
    protected void collectFoldSamples(int foldRole, boolean train, boolean induce, boolean accum) throws InterruptedException {
        String tstFoldId;
        SampleSet trainSetForTestFold=null;
        if(true) {
            tstFoldId="none";
            for(int i=0;i<folds.size();i++) {
                if(folds.get(i).getRole()==CrossValidationFold.TEST) {
                    tstFoldId=String.valueOf(i);
                }
            }
            trainSetForTestFold = new SampleSet("ss_fortestfold"+tstFoldId+"_h"+heldoutFoldCount, true);
        }

        
        for(CrossValidationFold f: folds) {
            if(foldRole!=-1 && f.getRole()!=foldRole) {
                continue;
            }
            for(Document doc: f.documents.getDocuments()) {
                for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
                    curProc=procedures.get(curProcIdx);
                    if(interrupt)
                        throw new InterruptedException();
                    if(curProc.engine instanceof Engine &&
                       curProc.engine instanceof TrainableExtractor) {
                        Engine eng = (Engine) curProc.engine;
                        TrainableExtractor te = (TrainableExtractor) eng;
                        String backup = te.getDumpName();
                        if(!accum)
                            te.setDumpName(f.toString());
                        try {
                            if(train) {
                                List<SampleSet> sampleSetList = accum? 
                                        curProc.dumpSampleSets: 
                                        curProc.procFolds.get(f.idx).sampleSets;
                                te.dumpSamples(doc, model, sampleSetList);
                                if(true) {
                                    String fn="doc"+doc.getId()+"_fortestfold"+tstFoldId+"_h"+heldoutFoldCount;
                                    log.LG(Logger.WRN, "Debug-dumping document "+doc+" to "+fn);
                                    List<SampleSet> sets=new ArrayList<SampleSet>();
                                    te.dumpSamples(doc, model, sets);
                                    for(int i=0;i<sets.size();i++) {
                                       sets.get(i).writeXrff(fn+"_ci"+i, true);
                                       if(trainSetForTestFold.getFeatures().size()==0)
                                           trainSetForTestFold.setFeatures(sets.get(i).getFeatures());
                                       trainSetForTestFold.addAll(sets.get(i));
                                    }
                                }
                            }
                            if(induce) {
                                List<AddableDataSet> fiSetList = accum?
                                        curProc.dumpFISets: 
                                        curProc.procFolds.get(f.idx).fiSets;
                                te.dumpFIData(doc, model, fiSetList);
                            }
                        }catch(Exception e) {
                            StringWriter wr=new StringWriter(256);
                            e.printStackTrace(new PrintWriter(wr));
                            log.LG(Logger.ERR,"Exception collecting samples from docs: engine="+curProc.engine.getName()+" model="+model.getUrl()+":\n"+e+"; coredump:\n"+wr.toString());
                            te.setDumpName(backup);
                            return;
                        }
                        te.setDumpName(backup);
                    }
                }
            }
        }
        
        if(trainSetForTestFold!=null) {
            try {
                trainSetForTestFold.writeXrff(trainSetForTestFold.getName()+".xrff", true);
            }catch(IOException e) { throw new IllegalArgumentException(e.toString()); }
            trainSetForTestFold.clear();
        }
    }
    
    private void dumpFoldSets(CrossValidationFold fold, boolean dumpTrainSets, boolean dumpFISets) throws IOException {
        for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
            Proc p=procedures.get(curProcIdx);
            if(p.engine instanceof Engine &&
               p.engine instanceof TrainableExtractor) {
                if(dumpTrainSets) {
                    List<SampleSet> sampleSetList = p.procFolds.get(fold.idx).sampleSets;
                    for(SampleSet sset: sampleSetList) {
                        BufferedWriter wr=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(sset.getName()+"_"+fold+".xrff")), "utf-8"));
                        sset.writeTo(wr);
                    }
                }
                if(dumpFISets) {
                    List<AddableDataSet> fiSetList = p.procFolds.get(fold.idx).fiSets;
                    for(AddableDataSet aset: fiSetList ) {
                        BufferedWriter wr=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(aset.getName()+"_"+fold+".ngram")), "utf-8"));
                        aset.writeTo(wr);
                    }
                }                
            }
        }        
    }

    private boolean isHeldout(int foldIdx, int firstHeldout, int lastHeldout) {
        if(firstHeldout<0 || lastHeldout<0)
            return false;
        return (firstHeldout<lastHeldout && foldIdx>=firstHeldout && foldIdx<=lastHeldout) 
            || (lastHeldout<firstHeldout && (foldIdx<=lastHeldout || foldIdx>=firstHeldout));
    }
    
    /** Sets the role of each fold, depending on settings and the specified test fold. */
    private void setFoldRoles(int testFoldIdx) {
        CrossValidationFold testFold=folds.get(testFoldIdx);
        testFold.setRole(CrossValidationFold.TEST);
        int firstHeldout=-1, lastHeldout=-1;
        if(heldoutFoldCount>0) {
            firstHeldout=testFoldIdx+1;
            lastHeldout=(testFoldIdx+heldoutFoldCount) % folds.size();
        }
        // determine training and held-out folds
        for(int foldIdx=0; foldIdx<folds.size(); foldIdx++) {
            if(foldIdx==testFoldIdx)
                continue;
            CrossValidationFold fold=folds.get(foldIdx);
            // is this a held-out or training fold
            if(isHeldout(foldIdx, firstHeldout, lastHeldout)) {
                fold.setRole(CrossValidationFold.HELDOUT);
            }else {
                fold.setRole(CrossValidationFold.TRAIN);
            }
        }
        log.LG(Logger.USR, "Test fold idx="+testFoldIdx);
        if(true) {
            for(int foldIdx=0; foldIdx<folds.size(); foldIdx++) {
                CrossValidationFold fold=folds.get(foldIdx);
                log.LG(Logger.USR, "setFoldRoles: "+fold+"="+CrossValidationFold.modeName(fold.role));
            }
        }
    }
    
    /** For each N-1-heldout folds together, trains utilized classifiers and evaluates 
        using the remaining fold, assumes some kind of Evaluator is the last engine. */
    protected void processFolds() throws InterruptedException {
        // let each fold be the test fold at a time: 
        for(int testFoldIdx=0; testFoldIdx<folds.size(); testFoldIdx++) {
            CrossValidationFold testFold=folds.get(testFoldIdx);
            setFoldRoles(testFoldIdx);
            
            // for each trainable procedure (engine), add samples from training folds together and train it
            for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
                curProc=procedures.get(curProcIdx);
                if(!(curProc.engine instanceof Engine) || !(curProc.engine instanceof TrainableExtractor)) {
                    continue;
                }
                // for each trainable classifier used by the engine,
                // add training sample sets of the training folds together, train it, 
                // and keep it in the engine
                for(int classifierIdx=0; classifierIdx < curProc.procFolds.get(0).sampleSets.size(); classifierIdx++) {
                    trainSingleClassifier(curProc, classifierIdx, null);
                }
            }
            
            // all trainable classifiers used by each engine have been trained using training folds,
            // now let's evaluate against the test fold
            // TODO: evaluate pure performance of the classifier as well, if possible using weka
            List<DocumentSet> testDocs=new ArrayList<DocumentSet>(1);
            testDocs.add(testFold.documents);
            if(pipeUnit==PIPE_UNIT_SET) {
                processDocumentSets(testDocs);
            }else { // pipeUnit==PIPE_UNIT_DOC
                processSeparateDocuments(testDocs);
            }
        }
    }
    
    
    /** Handles cross-validation when feature induction is used. <ul> 
     *  <li> induce features using held-out or training folds and store them with each test fold,
     *  <li> for each test fold, collect its training sample set directly from documents using
             the features stored with test fold, train classifier, finally test it on the test fold.
        </ul> */ 
    protected void processFoldsInduceFeatures() throws InterruptedException {
        // let each fold be the test fold at a time: 
        for(int testFoldIdx=0; testFoldIdx<folds.size(); testFoldIdx++) {
            CrossValidationFold testFold=folds.get(testFoldIdx);
            setFoldRoles(testFoldIdx);
            
            // for each classifier, add feature induction data from training or held-out folds,
            // induce features and keep them as part of the engines' classifiers
            for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
                Proc p=procedures.get(curProcIdx);
                if(!(p.engine instanceof Engine) || !(p.engine instanceof TrainableExtractor)) {
                    continue;
                }
                // for each trainable classifier that supports feature induction used by the engine,
                // add feature induction data together, induce features, keep them in the engine 
                for(int ficIdx=0; ficIdx<p.procFolds.get(0).fiSets.size(); ficIdx++) {
                    collectInduceSingleClassifierFeatures(p, ficIdx);
                }
                // just to be sure that collectFoldSamples creates new accumulated sample sets:
                p.dumpSampleSets.clear();
                ((TrainableExtractor)p.engine).setDumpName("trainset_for_"+testFold);
            }
            
            // for each classifier, collect a single training sample set from all training documents 
            // using the induced features, train the classifier
            collectFoldSamples(CrossValidationFold.TRAIN, true, false, true);
            for(curProcIdx=0; curProcIdx<procedures.size(); curProcIdx++) {
                Proc p=procedures.get(curProcIdx);
                if(!(p.engine instanceof Engine) || !(p.engine instanceof TrainableExtractor)) {
                    continue;
                }
                for(int classifierIdx=0; classifierIdx<p.dumpSampleSets.size(); classifierIdx++) {
                    SampleSet accumSampleSet = p.dumpSampleSets.get(classifierIdx);
                    trainSingleClassifier(p, classifierIdx, accumSampleSet);
                    // clear accumulated data set
                    accumSampleSet.clear();
                }
                // remove the cleared data sets so that new ones with different features are created during next fold
                p.dumpSampleSets.clear();
                ((TrainableExtractor)p.engine).setDumpName("testing_"+testFold);
            }
            
            // all classifiers are now trained using the new features: test everything against the test fold
            // TODO: evaluate pure performance of the classifier as well, if possible using weka
            List<DocumentSet> testDocs=new ArrayList<DocumentSet>(1);
            testDocs.add(testFold.documents);
            if(pipeUnit==PIPE_UNIT_SET) {
                processDocumentSets(testDocs);
            }else { // pipeUnit==PIPE_UNIT_DOC
                processSeparateDocuments(testDocs);
            }
            
            // clear accumulated feature sets and sample sets
        }
    }
    
    /** Trains a single classifier given by classifierIdx, used by the Engine identified by proc. 
     * @param procedure IE engine
     * @param classifierIdx id of the classifier within IE engine
     * @param trainSet if null, it is constructed from all training folds. */
    protected void trainSingleClassifier(Proc proc, int classifierIdx, SampleSet trainSet) throws InterruptedException {
        Engine eng=(Engine) proc.engine;
        TrainableExtractor teng=(TrainableExtractor) eng;
        boolean clear=false;
        SampleSet testSet = null; 
        
        if(trainSet==null) {
            // prepare new sample set to accumulate training data
            clear=true;
            SampleSet cumTrainSet=teng.createEmptySampleSet(eng.getName()+"_trainset", classifierIdx);
//          SampleSet aSampleSet=proc.procFolds.get(0).sampleSets.get(classifierIdx);
//          SampleSet cumTrainSet=new SampleSet("train_"+eng.getName(), true);
//          cumTrainSet.setFeatures(aSampleSet.getFeatures());

            if(interrupt) {
                throw new InterruptedException();
            }

            // add training folds for the current classifier only
            for(int f=0; f<folds.size(); f++) {
                CrossValidationFold trainFold=folds.get(f);
                switch(trainFold.getRole()) {
                case CrossValidationFold.TEST:
                    // name the accumulated training data by the test fold idx 
                    cumTrainSet.setName(cumTrainSet.getName()+"_for_testfold"+f);
                    log.LG(Logger.USR, "Going to train "+cumTrainSet.getName());
                    // also supply the test set to the trainer so it can report 
                    // accuracy on the test set if it wants to
                    testSet = proc.procFolds.get(f).sampleSets.get(classifierIdx);;
                    continue;
                case CrossValidationFold.HELDOUT:
                    continue;
                case CrossValidationFold.TRAIN:
                    break;
                }
                SampleSet foldTrainSet=proc.procFolds.get(f).sampleSets.get(classifierIdx);
                log.LG(Logger.USR,"Adding sampleset for "+trainFold+": "+foldTrainSet+"\n\tto "+cumTrainSet);
                cumTrainSet.addAll(foldTrainSet);
            }
            trainSet = cumTrainSet;
        }
        
        // train the classifier using the accumulated dataset
        teng.trainSingleClassifier(classifierIdx, trainSet, testSet);
        // dispose
        if(clear) {
            trainSet.clear();
        }
    }
    
    /** For the current TEST fold, for the specified classifier, sums feature induction data 
     *  assembled for other folds and induces a feature set for the classifier to use for the TEST fold. */
    protected void collectInduceSingleClassifierFeatures(Proc proc, int fiClassifierIdx) {
        Engine eng=(Engine) proc.engine;
        TrainableExtractor teng=(TrainableExtractor) eng;
        
        // create an empty FI data set, add all heldout/training folds to it 
        AddableDataSet cumFISet=teng.createEmptyFISet("fi_"+eng.getName(), fiClassifierIdx);
        for(int f=0; f<folds.size(); f++) {
            CrossValidationFold trainFold=folds.get(f);
            switch(trainFold.getRole()) {
            case CrossValidationFold.TEST:
                // name the accumulated training data by the test fold idx and skip the fold 
                cumFISet.setName(cumFISet.getName()+"_"+f);
                continue;
            case CrossValidationFold.HELDOUT:
                // train using held-out folds if they exist
                break;
            case CrossValidationFold.TRAIN:
                if(heldoutFoldCount>0)
                    continue; // only induce features using dedicated held-out folds if they exist
                break;
            }
            ProcFold pf=proc.procFolds.get(f);
            AddableDataSet foldFISet=pf.fiSets.get(fiClassifierIdx);
            log.LG(Logger.USR, pf+": adding its FI set "+foldFISet+" to "+cumFISet);
            cumFISet.addAll(foldFISet);
            log.LG(Logger.USR, pf+": FI set added to "+cumFISet);
        }
        
        // induce features using the accumulated FI data set
        teng.induceFeaturesForClassifier(fiClassifierIdx, cumFISet);

        // dispose
        cumFISet.clear();
    }
}

class ProcParam {
    ProcParam(String name, String value) {
        this.name=name;
        this.value=value;
    }
    String name;
    String value;
}

/** Holds IE engine and classifier independent information for a cross-validation fold. */
class CrossValidationFold {
    int idx;
    DocumentSet documents;
    int role=TRAIN;
    final static int TRAIN=1;
    final static int TEST=2;
    final static int HELDOUT=3;
    final static String modeNames[]={"train","test","heldout"};
    
    public static String modeName(int mode) {
        if(mode<TRAIN || mode>HELDOUT)
            return null;
        return modeNames[mode-1];
    }

    public CrossValidationFold(int idx, DocumentSet documents) {
        this.idx=idx;
        this.documents=documents;
    }
    
    public String toString() {
        return "fold_"+idx+"_"+documents.size();
    }

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }
}

/** Holds information for a cross-validation fold specific to IE engine. */
class ProcFold {
    CrossValidationFold fold;
    /** one SampleSet for each classifier used by the Engine's current extraction model,
        used to assemble samples for cross-validation, for training, for dumping. */
    protected ArrayList<SampleSet> sampleSets;
    /** one AddableDataSet for each classifier to be used for feature induction. 
     *  Whether these are the same as the sampleSets above, depends on implementation. */
    protected ArrayList<AddableDataSet> fiSets;
    /** evaluation result for this fold */
    protected EvalResult evalResult;
    
    public ProcFold(CrossValidationFold fold) {
        this.fold=fold;
        sampleSets=new ArrayList<SampleSet>();
        fiSets=new ArrayList<AddableDataSet>();
        evalResult=new EvalResult(toString());
    }
    
    public String toString() {
        return fold.toString()+", sampleSets="+sampleSets.size()+", fiSets="+fiSets.size();
    }
}

class DocumentInt {
    Document d;
    int n;
    public DocumentInt(Document d, int n) {
        this.d=d;
        this.n=n;
    }
}

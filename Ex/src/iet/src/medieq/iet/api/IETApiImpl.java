// $Id: IETApiImpl.java 1989 2009-04-22 18:48:45Z labsky $
package medieq.iet.api;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import uep.util.Options;
import uep.util.Logger;

import medieq.iet.components.Configurable;
import medieq.iet.components.DataModelManager;
import medieq.iet.components.DataModelManagerImpl;
import medieq.iet.components.Engine;
import medieq.iet.components.EngineFactory;
import medieq.iet.components.Evaluator;
import medieq.iet.components.EvaluatorImpl;
import medieq.iet.components.TaskManager;
import medieq.iet.components.TaskManagerImpl;

public class IETApiImpl implements IETApi {
    protected Options cfg;
    protected String cfgFile;
    protected Logger log;
    protected static int instCnt;
    protected List<Configurable> engines;
    protected DataModelManager dmm;
    protected TaskManager tm;
    protected Evaluator evaluator;
    protected int id;
    
    /** Constructs an uninitialized IETApi. */
    public IETApiImpl() {
        id = ++instCnt;
        cfg=Options.getOptionsInstance("iet");
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.api.IETApi#initialize(java.lang.String)
     */
    public int initialize(String cfgFile) throws IETException {
        this.cfgFile=cfgFile;
        try {
            cfg.load(new FileInputStream(new File(cfgFile)));
        }catch(IOException ex) {
            String msg="Cannot read IET cfg file: "+cfgFile;
            // log.LG(Logger.ERR, msg);
            System.err.println(msg);
            throw new IETException(msg, ex);
        }
        Logger.init(cfg.getProperty("log_file", "iet.log"), -1, -1, null);
        log=Logger.getLogger("iet"+id);

        int cnt=initEngines();
        dmm=new DataModelManagerImpl(this);
        tm=new TaskManagerImpl(this);
        log.LG(Logger.TRC,"Initialized with "+cnt+" engines");
        return cnt;
    }

    /** Create engines for use in IET based on cfg file; 
     * classes of used engines must be on classpath. 
     * In addition, new engines may be created based on task definitions. */
    protected int initEngines() throws IETException {
        String[] engSpecs={};
        String s=cfg.getProperty("iet_engines");
        if(s!=null) {
            engSpecs=s.split("\\s*,\\s*");
        }
        EngineFactory factory=new EngineFactory();
        engines=new LinkedList<Configurable>();
        for(int i=0;i<engSpecs.length;i++) {
            String[] clsCfg=engSpecs[i].trim().split("\\s+", 2);
            String ecls=clsCfg[0].trim();
            String ecfg=(clsCfg.length>1)? clsCfg[1].trim(): null;
            if(ecls.length()==0)
                continue;
            Configurable eng=factory.createEngine(ecls);
            try {
                eng.initialize(ecfg);
            }catch(IOException ex) {
                String msg="Error initializing engine "+eng.getName()+"("+ecls+") with cfg="+ecfg+": "+ex;
                log.LG(Logger.ERR,msg);
                throw new IETException(msg);
            }
            engines.add(eng);
        }
        log.LG(Logger.INF,"Initialized "+engines.size()+" engines at startup");
//        if(engines.size()==0) {
//            String msg="Could not initialize IET, no IE engine found in cfg "+cfgFile;
//            log.LG(Logger.ERR,msg);
//            throw new IETException(msg);
//        }
        return engines.size();
    }

    public Configurable getEngineByName(String engineName) {
        engineName=engineName.trim();
        Iterator<Configurable> eit=engines.iterator();
        Configurable eng=null;
        while(eit.hasNext()) {
            Configurable e=eit.next();
            if(engineName.equalsIgnoreCase(e.getClass().getName()) || 
               engineName.equalsIgnoreCase(e.getName())) {
                eng=e;
                break;
            }
        }
        return eng;
    }
    
    public String getEngineNames() {
        Iterator<Configurable> eit=engines.iterator();
        StringBuffer b=new StringBuffer(128);
        while(eit.hasNext()) {
            Configurable e=eit.next();
            b.append(e.getClass().getName()+"");
        }
        return null;
    }
    
    public List<Configurable> getEngines() {
        return engines;
    }

    /* (non-Javadoc)
     * @see medieq.iet.api.IETApi#uninitialize()
     */
    public void uninitialize() throws IETException {
        tm.uninitialize();
        dmm.uninitialize();
        uninitializeEngines();
    }

    /** Unitializes all IE engines. */
    protected void uninitializeEngines() {
        Iterator<Configurable> eit=engines.iterator();
        while(eit.hasNext()) {
            Configurable eng=eit.next();
            eng.uninitialize();
        }
        engines.clear();
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.api.IETApi#getDataModelManager()
     */
    public DataModelManager getDataModelManager() {
        return dmm;
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.api.IETApi#getTaskManager()
     */
    public TaskManager getTaskManager() {
        return tm;
    }

    /* (non-Javadoc)
     * @see medieq.iet.api.IETApi#getEvaluator()
     */
    public Evaluator getEvaluator() {
        if(evaluator==null)
            evaluator=new EvaluatorImpl();
        return evaluator;
    }
}

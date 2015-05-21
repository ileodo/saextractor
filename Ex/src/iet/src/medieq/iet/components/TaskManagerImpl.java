// $Id: TaskManagerImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import uep.util.Logger;

import medieq.iet.api.IETApi;
import medieq.iet.api.IETException;
import medieq.iet.model.Annotation;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.Document;
import medieq.iet.model.Instance;
import medieq.iet.model.Task;
import medieq.iet.model.TaskFactory;
import medieq.iet.model.TaskListener;

class IETListenerRecord {
    IETListenerRecord(int taskHandle, TaskListener listener) {
        this.taskHandle=taskHandle;
        this.listener=listener;
    }
    int taskHandle;
    TaskListener listener;
}

class IETTaskRecord {
    IETTaskRecord(List<Map<String, Object>> annotationList, List<Document> processedDocumentList) {
        this.annotationList=annotationList;
        this.processedDocumentList=processedDocumentList;
    }
    List<Map<String, Object>> annotationList;
    List<Document> processedDocumentList;
}

public class TaskManagerImpl implements TaskManager, TaskListener {
    protected Logger log;
    protected List<Task> runningTasks;
    protected List<IETListenerRecord> listeners;
    protected IETApi iet;

    public TaskManagerImpl(IETApi iet) {
        log=Logger.getLogger("TM");
        this.iet=iet;
        runningTasks=new LinkedList<Task>();
        listeners=new LinkedList<IETListenerRecord>();
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.components.TaskManager#processTask(java.lang.String, java.util.List, java.util.List, java.lang.String, java.util.List, boolean)
     */
    public int processTask(String taskName,
            List<Map<String, String>> procedures, 
            List<Map<String, Object>> inputDocumentList,
            List<Map<String, Object>> annotationList, String dataModelUrl,
            List<Document> processedDocumentList, boolean sync)
            throws IETException {
        log.LG(Logger.TRC,"Processing task "+taskName+" from document object array");
        if(procedures==null || procedures.size()==0)
            throw new IllegalArgumentException("Cannot define task without procedures");
        TaskFactory fact=new TaskFactory(iet);
        Task task=fact.createTask(taskName, inputDocumentList, dataModelUrl);
        for(Map<String, String> procParams: procedures) {
            String engName=procParams.get("engine");
            Task.Proc prc=task.addNewProc(engName);
            for(Map.Entry<String, String> par: procParams.entrySet()) {
                prc.setParam(par.getKey(), par.getValue());
            }
        }
        task.setUserData(new IETTaskRecord(annotationList, processedDocumentList));
        // for logging only:
        String taskFileName="dynamic.task";
        try {
            StringWriter sw=new StringWriter(256);
            BufferedWriter bw=new BufferedWriter(sw);
            fact.writeTask(task, bw);
            log.LG(Logger.USR, sw.toString());
        }catch(IOException ex) {
            log.LGERR("Could not write "+taskFileName);
        }
        return processTaskInternal(task, sync);
    }

    /* (non-Javadoc)
     * @see medieq.iet.components.TaskManager#processTask(java.lang.String, java.util.List, boolean)
     */
    public int processTask(String taskFile, List<Document> processedDocumentList, boolean sync)
            throws IETException {
        log.LG(Logger.TRC,"Processing task from file "+taskFile);
        TaskFactory fact=new TaskFactory(iet);
        Task task=fact.readTask(taskFile);
        return processTaskInternal(task, sync);
    }

    private int processTaskInternal(Task task, boolean sync) throws IETException {
        // first check we have/create all required engines
        int pc=task.getProcCount();
        for(int i=0;i<pc;i++) {
            Task.Proc proc=task.getProc(i);
            Configurable eng=proc.getEngine();
            if(eng!=null)
                continue;
            String engineName=proc.getEngineName();
            if(engineName==null || engineName.trim().length()==0) {
                String msg="Engine name for task "+task.getFile()+" procedure n."+(i+1)+" not specified";
                log.LG(Logger.ERR,msg);
                throw new IETException(msg);
            }            
            eng=iet.getEngineByName(engineName);
            if(eng==null) {
                EngineFactory factory=new EngineFactory();
                eng=factory.createEngine(engineName);
                String cfgFile=proc.getParam("cfg");
                try {
                    eng.initialize(cfgFile);
                }catch(IOException ex) {
                    String msg="Error initializing engine "+eng.getName()+"("+engineName+") with cfg="+cfgFile+": "+ex;
                    log.LG(Logger.ERR,msg);
                    throw new IETException(msg);
                }

            }
            proc.setEngine(eng);
//            if(eng==null) {
//                String msg="Cannot find engine named '"+task.getEngineName()+
//                "'. Available engines:\n"+((IETApiImpl)iet).getEngineNames();
//                log.LG(Logger.ERR,msg);
//                throw new IETException(msg);
//            }
//            task.setEngine(eng);
        }
        task.addListener(this);
        runningTasks.add(task);
        task.setState(Task.STATE_STARTING);
        task.start();
        if(sync) {
            try {
                synchronized(task) {
                    while(task.getState()!=Task.STATE_IDLE) {
                        task.wait();
                    }
                }
            }catch(InterruptedException ex) {
                log.LG(Logger.ERR,"Interrupted waiting for task completion: "+ex);
            }
        }
        return task.getId();
    }

    /* (non-Javadoc)
     * @see medieq.iet.components.TaskManager#registerListener(medieq.iet.model.TaskListener)
     */
    public void registerListener(int taskHandle, TaskListener taskListener) {
        synchronized(listeners) {
            listeners.add(new IETListenerRecord(taskHandle, taskListener));
        }
    }

    /* (non-Javadoc)
     * @see medieq.iet.components.TaskManager#stopTask(int, boolean)
     */
    public boolean stopTask(int taskHandle, boolean sync) throws IETException {
        Task tsk=getTaskById(taskHandle);
        boolean rc=false;
        if(tsk!=null && tsk.getState()!=Task.STATE_IDLE) {
            rc=true;
            tsk.stop();
            if(sync) {
                try {
                    synchronized(tsk) {
                        while(tsk.getState()!=Task.STATE_IDLE) {
                            tsk.wait();
                        }
                    }
                }catch(InterruptedException ex) {
                    log.LG(Logger.ERR,"Interrupted waiting for task completion: "+ex);
                }
            }
        }
        return rc;
    }

    /** Finds a running task by handle. */
    protected Task getTaskById(int taskHandle) {
        Task tsk=null;
        synchronized(runningTasks) {
            Iterator<Task> tit=runningTasks.iterator();
            while(tit.hasNext()) {
                Task t=tit.next();
                if(t.getId()==taskHandle) {
                    tsk=t;
                    break;
                }
            }
        }
        return tsk;
    }

    /* (non-Javadoc)
     * @see medieq.iet.components.TaskManager#stopAllTasks(boolean)
     */
    public boolean stopAllTasks(boolean sync) throws IETException {
        boolean rc=false;
        synchronized(runningTasks) {
            Iterator<Task> tit=runningTasks.iterator();
            while(tit.hasNext()) {
                Task tsk=tit.next();
                if(stopTask(tsk.getId(), sync))
                    rc=true;
            }
        }
        return rc;
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.components.TaskManager#unregisterListener(medieq.iet.model.TaskListener)
     */
    public void unregisterListener(TaskListener taskListener) {
        synchronized(listeners) {
            ListIterator<IETListenerRecord> lit=listeners.listIterator();
            while(lit.hasNext()) {
                IETListenerRecord lr=lit.next();
                if(lr.listener==taskListener) {
                    lit.remove();
                }
            }
        }
    }

    /* TaskListener implementation */
    
    /** Adds extracted information to annotationList (if given in processTask) and 
     * then calls any relevant user-defined listeners. */
    public void onDocumentProcessed(Task task, int idx, Document doc) {
        log.LG(Logger.TRC, "Document "+doc.getFile()+" processed by task id="+task.getId()+", name="+task.getName());
        IETTaskRecord tr=(IETTaskRecord) task.getUserData();
        if(tr!=null) {
            if(tr.processedDocumentList!=null) {
                synchronized(tr.processedDocumentList) {
                    tr.processedDocumentList.add(doc);
                }
            }
            if(tr.annotationList!=null) {
                synchronized(tr.annotationList) {
                    addDocumentAnnots(doc, tr.annotationList);
                }
            }
        }
        synchronized(listeners) {
            ListIterator<IETListenerRecord> lit=listeners.listIterator();
            while(lit.hasNext()) {
                IETListenerRecord lr=lit.next();
                if(lr.taskHandle==task.getId() || lr.taskHandle==-1) {
                    lr.listener.onDocumentProcessed(task, idx, doc);
                }
            }
        }
    }

    /** Translates extracted AttributeValue objects to Map representation used by Aqua 
     * and adds them to annotationList supplied to the processTask method */
    protected int addDocumentAnnots(Document doc, List<Map<String, Object>> annotationList) {
        int cnt=0;
        Iterator<AttributeValue> avit=doc.getAttributeValues().iterator();
        Date now=new Date();
        // standalone attribute values
        while(avit.hasNext()) {
            AttributeValue av=avit.next();
            Map<String,Object> anobj=av2annotObject(av, doc, now, null);
            if(anobj==null)
                continue;
            annotationList.add(anobj);
            cnt++;
        }
        // instance attribute values
        Iterator<Instance> init=doc.getInstances().iterator();
        while(init.hasNext()) {
            Instance inst=init.next();
            avit=inst.getAttributes().iterator();
            while(avit.hasNext()) {
                AttributeValue av=avit.next();
                Map<String,Object> anObj=av2annotObject(av, doc, now, inst.getId());
                if(anObj==null)
                    continue;
                annotationList.add(anObj);
                cnt++;
            }
            Map<String,Object> instObj=inst2annotObject(inst, doc, now);
            if(instObj==null)
                continue;
            annotationList.add(instObj);
        }
        return cnt;
    }
    
    /** Translates an extracted AttributeValue object to Map representation used by Aqua */
    protected Map<String,Object> av2annotObject(AttributeValue av, Document doc, Date date, String instId) {
        HashMap<String,Object> map=new HashMap<String,Object>();
        map.put("XFile", doc.getId());
        map.put("XType", av.getAttributeDef().getName());
        map.put("XData", av.getText());
        map.put("XDate", date);
        if(av.getScore()!=-1)
            map.put("XScore", av.getScore());
        // a single attribute value may have many coreferences, we only output the first one here
        Annotation a=av.getAnnotations().get(0);
        int si=a.getStartOffset();
        int ei=si+a.getLength();
        map.put("XStart", new Integer(si));
        map.put("XEnd", new Integer(ei));
        map.put("XAuthor", a.getAuthor());
        map.put("XInstance", instId);
        return map;
    }

    /** Translates an extracted Instance object to Map representation used by Aqua */
    protected Map<String,Object> inst2annotObject(Instance inst, Document doc, Date date) {
        HashMap<String,Object> map=new HashMap<String,Object>();
        map.put("XFile", doc.getId());
        map.put("XType", "class:"+inst.getClass().getName());
        map.put("XInstId", inst.getId());
        map.put("XData", inst.toXML());
        map.put("XDate", date);
        if(inst.getScore()!=-1)
            map.put("XScore", inst.getScore());
        // return the span of this class
        Annotation a1=inst.getAttributes().get(0).getAnnotations().get(0);
        int si=a1.getStartOffset();
        Annotation a2=inst.getAttributes().get(inst.getAttributes().size()-1).getAnnotations().get(0);
        int ei=a2.getStartOffset()+a2.getLength();
        map.put("XStart", new Integer(si));
        map.put("XEnd", new Integer(ei));
        map.put("XAuthor", a1.getAuthor());
        return map;
    }

    /** Notifies all sync calls (processTask or stopTask) blocked on this task,
     * then calls any relevant user-defined listeners. */
    public void onStateChange(Task task, int state) {
        switch(state) {
        case Task.STATE_IDLE:
            synchronized(task) {
                task.notifyAll();
            }
            synchronized(runningTasks) {
                runningTasks.remove(task);
            }
            synchronized(listeners) {
                ListIterator<IETListenerRecord> lit=listeners.listIterator();
                while(lit.hasNext()) {
                    IETListenerRecord lr=lit.next();
                    if(lr.taskHandle==task.getId() || lr.taskHandle==-1) {
                        lr.listener.onStateChange(task, state);
                    }
                }
            }
            break;
        }
    }

    public void uninitialize() throws IETException {
        stopAllTasks(true);
        listeners.clear();
    }
}

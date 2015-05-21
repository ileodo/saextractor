//$Id: RunTask.java 1934 2009-04-12 09:16:14Z labsky $
package medieq.iet.test;

import java.io.*;

import uep.util.Logger;
import medieq.iet.model.*;
import medieq.iet.api.IETApi;
import medieq.iet.api.IETApiImpl;
import medieq.iet.api.IETException;
import medieq.iet.components.*;

public class RunTask implements TaskListener {

//    Engine engine;
    DocumentLabeler dlab;
    static boolean waitForProfiler=true;
    static IETApi iet;

    public RunTask() {
        dlab=null; // create later when more settings are loaded
    }
    
    // now engines are defined as part of the task procedures;
    // some may be inited by IETAPi on startup basef on initial cfg
//    public int initEngines() throws IETException {
//        System.err.println("Initializing engines...");
//        EngineFactory factory=new EngineFactory();
//
//        /* Create engines for use in IET;
//         * classes of used engines must be on classpath */
//        Engine e1=factory.createEngine("ex.api.Ex");
//        boolean rc=e1.initialize("config.cfg");
//        if(!rc) {
//            throw new IETException("Could not initialize engine from cfg");
//        }
//        engine=e1;
//        return 1;
//    }

    public int runTasks(String[] taskFiles) throws IETException {
        for(int i=0; i<taskFiles.length; i++) {
            System.err.println("Running task "+taskFiles[i]);
            TaskFactory fact=new TaskFactory(iet);
            Task task=fact.readTask(taskFiles[i]);
            // task.setEngine(engine);
            task.addListener(this);
            task.start();
            // wait till task finishes
            try {
                synchronized(this) {
                    this.wait();
                }
            }catch(InterruptedException ex) {
                System.err.println("Interrupted waiting for task completion: "+ex);
            }
        }
        return 0;
    }

    public static void main(String[] args) throws IETException {
        if(args.length==0) {
            System.err.println("Usage: RunTask <taskfile1> [<taskfile2> ...]");
            System.exit(-1);
        }
        System.err.println("IET sample client starting");
        String cfg="iet.cfg";
        iet=new IETApiImpl();
        iet.initialize(cfg);
        Logger.pause("after init");
        
//        Options o=Options.getOptionsInstance();
//        try {
//            o.load(new FileInputStream(cfg));
//        }catch(Exception ex) {
//            System.err.println("Cannot find "+cfg+": "+ex.getMessage());
//        }
//        Logger.init("iet.log", -1, 0, null);
        
//        int engCnt=ietClient.initEngines();
//        if(engCnt<=0)
//            return;

        RunTask ietClient=new RunTask();
        ietClient.runTasks(args);

        System.err.println("IET sample client ended");
        Logger.pause("before deinit");
        iet.uninitialize();
        Logger.pause("after deinit");
        return;
    }

    /* TaskListener impl */
    public void onDocumentProcessed(Task task, int idx, Document doc) {
        System.out.println("Document["+idx+"] "+doc+" done");
        System.out.println("Instances:");
        for(int i=0; i<doc.getInstances().size(); i++) {
            Instance inst=doc.getInstances().get(i);
            System.out.println(inst.toXML());
        }
        System.out.println("Attributes:");
        for(int i=0; i<doc.getAttributeValues().size(); i++) {
            AttributeValue av=doc.getAttributeValues().get(i);
            System.out.println(av.toXML());
        }
        if(doc.getEvalResult()!=null) {
            System.out.println();
            System.out.println("Evaluation:");
            System.out.println(doc.getEvalResult());
        }
        System.out.println();
        if(doc.getSource()==null) {
            return;
        }
        boolean verbose=false;
        if(verbose) {
            System.out.println("Annotated doc source:\n"+doc.getAnnotatedSource()+"\n");
        }
        boolean genAnnFmt=true;
        if(genAnnFmt) {
            if(dlab==null) {
                dlab=new DatDocumentLabeler();
            }
            dlab.annotateDocument(doc);
            String fn=doc.getFile();
            String[] parts=fn.split("\\|");
            String annFile = parts[0].trim()+".atf";
            try {
                dlab.writeAnnotatedDocument(annFile, "utf-8");
            }catch(IOException ex) {
                Logger.LOG(Logger.ERR,"Could not write "+annFile+": "+ex);
            }
            dlab.clear();
        }
        // we will not need the original and annotated doc sources anymore,
        // but we still keep the filled-in annotations for some hypothetical later processing
        if(task.getMode()!=Task.MODE_CROSSVALIDATE) {
            doc.setSource(null); // keep all document sources during cross-validation
        }
        doc.setAnnotatedSource(null);
        Logger.pause("onDocumentProcessed");
    }

    public void onStateChange(Task task, int state) {
        switch(state) {
        case Task.STATE_IDLE:
            synchronized(this) {
                this.notify();
            }
        }
    }
}

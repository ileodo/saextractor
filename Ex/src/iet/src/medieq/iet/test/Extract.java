// $Id: Extract.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import medieq.iet.api.*;
import medieq.iet.components.*;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.Document;
import medieq.iet.model.Instance;
import medieq.iet.model.Task;
import medieq.iet.model.TaskListener;

public class Extract {
    
    public static void main(String[] args) throws IETException {
        /* Read cmdline parameters */
        if(args.length<3) {
            System.err.println("Usage: Extract <iet.cfg> <model> [<file1.html> ...]");
            System.exit(-1);
        }
        String ietCfg=args[0];
        // now model is directly the Ex extraction model to use; 
        // will be replaced by general IET data model pointing to extraction models of IE engines
        String[] models=args[1].trim().split("\\s+");
        System.err.println("Extract started cfg="+ietCfg+" models="+Arrays.toString(models));
        
        /* Create and initialize IET: reads cfg, starts logging, creates engines from cfg */
        IETApi iet=new IETApiImpl();
        try {
            iet.initialize(ietCfg);
        }catch(IETException ex) {
            System.err.println("Error initializing IET: "+ex);
            System.exit(-1);
        }
        
        /* Create a list of Document objects */
        List<Map<String,Object>> inputDocs=new ArrayList<Map<String,Object>>(args.length-2);
        for(int i=2;i<args.length;i++) {
            String fileName=args[i].trim();
            String docId="doc"+String.valueOf(i-1);
            System.err.println("Adding to doc queue: "+docId+"="+fileName);
            Map<String,Object> doc=new HashMap<String,Object>();
            doc.put("FileName", fileName);
            doc.put("FileLocation", null);
            doc.put("WebUri", "local");
            doc.put("FileID", docId);
            doc.put("FileContentType", "text/html");
            inputDocs.add(doc);
        }
        /* Prepare output structures */
        List<Map<String,Object>> annots=new LinkedList<Map<String,Object>>();
        List<medieq.iet.model.Document> ietDocs=new ArrayList<medieq.iet.model.Document>(args.length-2);
        
        /* Start extraction (sync) */
        TaskManager tm=iet.getTaskManager();
        tm.registerListener(-1, new ProgressReporter());
        
        System.err.println("Defining "+models.length+" sample procedures for task");
        List<Map<String,String>> procedures=new LinkedList<Map<String,String>>();
        
        for(String model: models) {
            Map<String,String> proc=new HashMap<String,String>();
            proc.put("engine", "ex.api.Ex"); // mandatory - class name of the engine
            proc.put("model", model); // the extraction ontology file for Ex 
            proc.put("cfg", "../ex/config.cfg"); // Ex configuration: nbest, parser settings, log levels... 
            procedures.add(proc);
        }
        
        try {
            tm.processTask("task1", procedures, inputDocs, annots, "iet_model_dummy_name", ietDocs, true);
        }catch(IETException ex) {
            System.err.println("Error processing documents: "+ex);
            System.exit(-1);
        }
        
        System.err.println("Dumping annotations:\n");
        
        /* Dump extracted annotations to stdout */
        for(Map<String,Object> an: annots) {
            System.out.println(annotToString(an));
        }
        
        System.err.println("Extract ended");
    }
    
    static StringBuffer buff=new StringBuffer(128); 
    static String annotToString(Map<String,Object> an) {
        buff.setLength(0);
        Iterator<Entry<String,Object>> anit=an.entrySet().iterator();
        while(anit.hasNext()) {
            Entry<String,Object> entry=anit.next();
            buff.append(entry.getKey()+"="+entry.getValue()+"\n");
        }
        String s=buff.toString();
        buff.setLength(0);
        return s;
    }
}

class ProgressReporter implements TaskListener {
    public void onDocumentProcessed(Task task, int idx, Document doc) {
        System.err.println("Task "+task.getId()+": processed doc["+idx+"]="+doc.getFile());
        String src=doc.getAnnotatedSource();
        if(src!=null) {
            String annotatedFile="~extract_"+idx+".html";
            System.err.println("Storing annotated doc to "+annotatedFile);
            writeFile(annotatedFile, src);
        }
        // attribute and instance details can be found in the following lists;
        // but most of them are copied to the annotation name-value objects (annots above)
        List<AttributeValue> attVals=doc.getAttributeValues();
        List<Instance> instances=doc.getInstances();
    }

    public void onStateChange(Task task, int state) {
        ;
    }
    
    protected void writeFile(String file, String content) {
        try {
            File f=new File(file);
            f.createNewFile();
            FileWriter fw=new FileWriter(f, false);
            fw.write(content,0,content.length());
            fw.flush();
            fw.close();
        }catch(IOException ex) {
            System.err.println("Error writing "+file+": "+ex);
        }
    }
}

// $Id: HmmWrapper.java 1699 2008-10-19 23:07:41Z labsky $
package hmmwrp.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import uep.util.CacheItem;
import uep.util.Fetcher;
import uep.util.Logger;

import medieq.iet.generic.DataModelImpl;
import medieq.iet.generic.DocumentImpl;
import medieq.iet.components.BikeDocumentReader;
import medieq.iet.components.Engine;
import medieq.iet.components.InstanceExtractor;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;
import medieq.iet.model.DocumentSet;
import medieq.iet.model.Instance;

public class HmmWrapper implements Engine, InstanceExtractor {
    Properties cfg; // keys: hmmDir, tmpDir, outDir, encoding;
    Logger log;
    Process hmmProc;
    Fetcher fetch;
    int dnlDocCnt;
    boolean inclInstances;
    BikeDocumentReader dr;
    
    static SimpleDateFormat dateFmt=new SimpleDateFormat("yyMMdd_HHmm");

    public String getName() {
        return "HMM Engine wrapper for IET v1.0";
    }
    
    public boolean initialize(String configFile) throws IOException {
        log=Logger.getLogger("hmm");
        cfg=new Properties();
        cfg.setProperty("contentType","text/html");
        cfg.setProperty("author", "hmm");
        cfg.setProperty("prefix", "");
        inclInstances=false;
        if(configFile!=null) {
            cfg.load(new FileInputStream(new File(configFile)));
        }
        fetch=new Fetcher();
        dnlDocCnt=0;
        dr=new BikeDocumentReader();
        return false;
    }
    
    public boolean cancel(int cancelType) {
        boolean rc=false;
        synchronized(this) {
            if(hmmProc!=null) {
                hmmProc.destroy();
                hmmProc=null;
                rc=true;
            }
        }
        return rc;
    }

    public String getModel() {
        return cfg.getProperty("hmmDir");
    }

    public int loadModel(String modelFile) throws IOException {
        cfg.setProperty("hmmDir", modelFile);
        return 0;
    }

    public void uninitialize() {
        cfg.clear();
    }

    public void configure(Properties params) {
        Iterator<Map.Entry<Object,Object>> pit=params.entrySet().iterator();
        while(pit.hasNext()) {
            Map.Entry<Object,Object> p=pit.next();
            setParam(p.getKey().toString(), p.getValue().toString());
        }
    }

    public void configure(InputStream cfgFile) throws IOException {
        cfg.load(cfgFile);
    }

    public Object getParam(String name) {
        return cfg.getProperty(name);
    }

    public void setParam(String name, Object value) {
        cfg.setProperty(name, (value!=null)? value.toString(): null);
    }

    private int extract(Document doc, String modelName, String classFile, DataModel model) {
        // get doc source if we do not have it from IET
        if(doc.getSource()==null) {
            if(!readRawDoc(doc))
                return -1;
        }
        String rawFn=cfg.getProperty("tmpDir")+"/"+(new File(doc.getFile()).getName());
        File f=new File(rawFn);
        try {
            f.createNewFile();
            OutputStreamWriter w=new OutputStreamWriter(new FileOutputStream(f), "utf-8");
            w.write(doc.getSource());
            w.close();
        }catch(IOException ex) {
            log.LGERR("Error writing HMM tmp input file: "+ex);
        }
        // execute c:/Perl/bin/perl.exe
        String cmd="perl ado.pl -f -i "+f.getAbsolutePath()+" -m "+modelName;
        if(classFile!=null) {
            cmd+=" -r "+classFile;
        }
        String outFn=cfg.getProperty("outDir")+"/"+(new File(doc.getFile()).getName());
        File f2=new File(outFn);
        cmd+=" -O "+f2.getAbsolutePath();
        
        executeProcess(cmd);

        return addAnnotations(doc, f2.getAbsolutePath(), model);
    }
    
    public int extractAttributes(Document doc, DataModel model) {
        String modelName=cfg.getProperty("model");
        if(modelName==null || modelName.length()==0)
            throw new IllegalArgumentException("HMM model not specified!");
        String classFile=cfg.getProperty("classes");
        if(classFile!=null && classFile.length()==0)
            classFile=null;        
        // store input files first to tmp directory
        setDirectories();

        return extract(doc, modelName, classFile, model);
    }

    public int extractAttributes(DocumentSet docSet, DataModel model) {
        String modelName=cfg.getProperty("model");
        if(modelName==null || modelName.length()==0)
            throw new IllegalArgumentException("HMM model not specified!");
        String classFile=cfg.getProperty("classes");
        if(classFile!=null && classFile.length()==0)
            classFile=null;
        // store input files first to tmp directory
        setDirectories();
        
        int cnt=0;
        for(Document doc: docSet.getDocuments()) {
            cnt+=extract(doc, modelName, classFile, model);
        }
        return cnt;
    }
    
    protected int addAnnotations(Document origDoc, String hmmOutFile, DataModel model) {
        int cnt=0;
        Document outDoc=new DocumentImpl(null, hmmOutFile);
        try {
            dr.readDocument(outDoc, model, null, true);
        }catch(IOException ex) {
            String err="Error reading Ellogon output document "+outDoc;
            log.LGERR(err);
        }
        cnt+=outDoc.getAttributeValues().size();
        String author=cfg.getProperty("author");
        String prefix=cfg.getProperty("prefix");
        if(prefix!=null) {
            prefix=prefix.trim();
        }else {
            prefix="";
        }
        // origDoc.getAttributeValues().addAll(outDoc.getAttributeValues());
        Iterator<AttributeValue> avit=outDoc.getAttributeValues().iterator();
        while(avit.hasNext()) {
            AttributeValue av=avit.next();
            av.setAttributeName(prefix+av.getAttributeName(), model);
            av.setAuthor(author);
            origDoc.getAttributeValues().add(av);
        }
        if(inclInstances) {
            cnt+=outDoc.getInstances().size();
            //origDoc.getInstances().addAll(outDoc.getInstances());
            Iterator<Instance> init=outDoc.getInstances().iterator();
            while(init.hasNext()) {
                Instance ins=init.next();
                ins.setAuthor("hmm");
                origDoc.getInstances().add(ins);
            }
        }
        return cnt;
    }

    private void executeProcess(String cmd) {
        String dir=cfg.getProperty("hmmDir");
        if(dir==null || dir.length()==0)
            dir="./hmm";
        File workDir=new File(dir);
        String[] envp=null; //new String[0];
        try {
            if(!workDir.exists() || !workDir.isDirectory()) {
                throw new IOException("Working dir "+workDir+" does not exist or is not a directory.");
            }
            workDir=new File(workDir.toString()+"/train");
            log.LG(Logger.WRN, "Executing in "+workDir+":\n"+ cmd);
            
//            boolean useShell=true;
//            if(useShell) {
//                String tmpFn="_tmpiet.cmd";
//                File tmpCmd=new File(workDir.getAbsolutePath()+"/"+tmpFn);
//                tmpCmd.createNewFile();
//                FileWriter fw=new FileWriter(tmpCmd);
//                fw.write(cmd);
//                fw.close();
//                cmd="cmd /C "+tmpFn;
//            }
            
            hmmProc=Runtime.getRuntime().exec(cmd, envp, workDir);
            BufferedReader stdout=new BufferedReader(new InputStreamReader(hmmProc.getInputStream()));
            BufferedReader stderr=new BufferedReader(new InputStreamReader(hmmProc.getErrorStream()));
            Thread stdoutDump=new Thread(new OutputDumper(stdout));
            Thread stderrDump=new Thread(new OutputDumper(stderr));
            stdoutDump.start();
            stderrDump.start();
            hmmProc.waitFor();
        }catch(IOException ex) {
            log.LGERR("Error executing "+workDir+"/"+cmd+": "+ex);
        }catch(InterruptedException ex) {
            log.LGERR("Interrupted waiting for completion of "+cmd+": "+ex);
        }
    }
    
    protected class OutputDumper implements Runnable {
        BufferedReader reader;
        public OutputDumper(BufferedReader reader) {
            this.reader=reader;
        }
        public void run() {
            String line;
            try {
                while((line=reader.readLine())!=null) {
                    log.LG(Logger.WRN,line);
                }
            }catch(IOException ex) {
                log.LGERR("Error reading HMM output: "+ex);
            }
        }
    }
    
    private void setDirectories() {
        String tmpDir=cfg.getProperty("tmpDir");
        String d=dateFmt.format(new Date());
        if(tmpDir==null) {
            tmpDir=d + "tmp";
            cfg.setProperty("tmpDir", tmpDir);
            File f=new File(tmpDir);
            if(!f.exists()) {
                f.mkdirs();
            }
        }
        String outDir=cfg.getProperty("outDir");
        if(outDir==null) {
            outDir=d + "out";
            cfg.setProperty("outDir", outDir);
            File f=new File(outDir);
            if(!f.exists()) {
                f.mkdirs();
            }
        }
    }
    
    public int extractInstances(Document doc, DataModel model) {
        inclInstances=true;
        int cnt=extractAttributes(doc, model);
        inclInstances=false;
        return cnt;
    }

    public int extractInstances(DocumentSet docSet, DataModel model) {
        inclInstances=true;
        int cnt=extractAttributes(docSet, model);
        inclInstances=false;
        return cnt;
    }
    
    protected boolean readRawDoc(Document doc) {
        CacheItem citem=null;
        if(doc.getFile()!=null && doc.getFile().length()>0) {
            doc.setEncoding(cfg.getProperty("encoding"));
            doc.setContentType(cfg.getProperty("contentType"));
            citem=CacheItem.fromFile(doc.getFile(), doc.getContentType(), doc.getEncoding(), doc.getForceEncoding());
            if(citem==null) {
                log.LG(Logger.ERR,"Cannot open document from file: "+doc.getFile());
            }else {
                doc.setSource(citem.data);
            }
        }else if(doc.getUrl()!=null && doc.getUrl().length()>0) {
            fetch.setDirectory(cfg.getProperty("tmpDir"));
            // fetches the document, caches it under a name based on docCounter into Fetcher.downloadDir 
            int rc=fetch.fetch(doc.getUrl(), new Integer(++dnlDocCnt).toString(), true, true, true, true);
            if(rc!=0 || (citem=fetch.getCacheMap().get(doc.getUrl()))==null) {
                log.LG(Logger.ERR,"Cannot fetch document from web: "+doc.getUrl());
            }else {
                doc.setFile(citem.cachedUrl);
                doc.setSource(citem.data);
            }
        }
        return doc.getSource()!=null;
    }

    public static void main(String[] args) throws IOException {
        String cfgFile=(args.length>0)? args[0]: "hmm.cfg";
        String docFile=(args.length>1)? args[1]: "../Collections/test/a.html";
        HmmWrapper ew=new HmmWrapper();
        ew.initialize(cfgFile);
        
        Document doc=new DocumentImpl(null, docFile);
        doc.setSource("<html><body> John Newman works at the Neurology Department at the University of New York. John's office is seated in Suite A/2, Office Park, 14th West 11th Street, Manhattan, 10506 New York, NY, USA. </body></html>");
        DataModel model=new DataModelImpl(null, "initially empty model");
        ew.extractAttributes(doc, model);
        
        System.out.println("Attributes ["+doc.getAttributeValues().size()+"]:\n");
        System.out.println(doc.getAttributeValues().toString());
        System.out.println("Instances ["+doc.getInstances().size()+"]:\n");
        System.out.println(doc.getInstances().toString());
        
        ew.uninitialize();
    }
}

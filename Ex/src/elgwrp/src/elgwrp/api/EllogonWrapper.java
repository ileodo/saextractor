// $Id: EllogonWrapper.java 1644 2008-09-12 21:56:55Z labsky $
package elgwrp.api;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uep.util.CacheItem;
import uep.util.Fetcher;
import uep.util.Logger;
import uep.util.Util;

import medieq.iet.components.DatDocumentReader;
import medieq.iet.components.Engine;
import medieq.iet.components.InstanceExtractor;
import medieq.iet.generic.DataModelImpl;
import medieq.iet.generic.DocumentImpl;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;
import medieq.iet.model.DocumentSet;
import medieq.iet.model.Instance;

public class EllogonWrapper implements Engine, InstanceExtractor {
    Properties cfg; // keys: elgDir, tmpDir, outDir, encoding;
    Logger log;
    Process elgProc;
    Fetcher fetch;
    int dnlDocCnt;
    boolean inclInstances;
    DatDocumentReader dr;
    final String MODEL_SCRIPT="share/modules/BOEMIE_CRF_Evaluate/creole_config.tcl";
    
    static SimpleDateFormat dateFmt=new SimpleDateFormat("yyMMdd_HHmm");

    public String getName() {
        return "NCSR Ellogon CRF-based IE tool v1.0";
    }
    
    public boolean initialize(String configFile) throws IOException {
        log=Logger.getLogger("elg");
        cfg=new Properties();
        cfg.setProperty("contentType","text/html");
        cfg.setProperty("author", "elg");
        cfg.setProperty("prefix", "");
        cfg.setProperty("elgDir", "elgDir/IE_App_Contact_Other");
        inclInstances=false;
        if(configFile!=null) {
            cfg.load(new FileInputStream(new File(configFile)));
        }
        fetch=new Fetcher();
        dnlDocCnt=0;
        dr=new DatDocumentReader();
        return false;
    }
    
    public boolean cancel(int cancelType) {
        boolean rc=false;
        synchronized(this) {
            if(elgProc!=null) {
                elgProc.destroy();
                elgProc=null;
                rc=true;
            }
        }
        return rc;
    }

    public String getModel() {
        return cfg.getProperty("model");
    }

    public int loadModel(String modelFile) throws IOException {
        cfg.setProperty("model", modelFile);
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

    public int extractAttributes(Document doc, DataModel model) {
        setDirectories();
        // get doc source if we do not have it from IET
        if(doc.getSource()==null) {
            if(!readRawDoc(doc))
                return -1;
        }
        File f=new File(cfg.getProperty("tmpDir")+"/"+(new File(doc.getFile()).getName()));
        try {
            f.createNewFile();
            OutputStreamWriter w=new OutputStreamWriter(new FileOutputStream(f), "utf-8");
            w.write(doc.getSource());
            w.close();
        }catch(IOException ex) {
            log.LGERR("Error writing ELG tmp input file "+f+": "+ex);
        }
        // execute
        String cmd="tclsh "+cfg.getProperty("elgDir")+
            "/Init.tcl -inputdir \""+cfg.getProperty("tmpDir") // +"\" -inputfile \""+ (new File(doc.getFile()).getName())
                 +"\" -outputdir \""+cfg.getProperty("outDir"); // +"\" -outputfile \""+(new File(doc.getFile()).getName())+"\"";
        executeProcess(cmd);

        if(!f.delete()) {
            log.LGERR("Error deleting ELG tmp input file: "+f);
        }
        int cnt=addAnnotations(doc, cfg.getProperty("outDir")+"/"+(new File(doc.getFile()).getName()), model);
        return cnt;
    }

    public int extractAttributes(DocumentSet docSet, DataModel model) {
        // store input files first to tmp directory
        setDirectories();
        Iterator<Document> dit=docSet.getDocuments().iterator();
        while(dit.hasNext()) {
            Document doc=dit.next();
            // get doc source if we do not have it from IET
            if(doc.getSource()==null) {
                if(!readRawDoc(doc))
                    return -1;
            }
            File f=new File(cfg.getProperty("tmpDir")+"/"+(new File(doc.getFile()).getName()));
            try {
                f.createNewFile();
                OutputStreamWriter w=new OutputStreamWriter(new FileOutputStream(f), "utf-8");
                w.write(doc.getSource());
                w.close();
            }catch(IOException ex) {
                log.LGERR("Error writing ELG tmp input file: "+ex);
            }
        }
        // execute
        String cmd="tclsh "+cfg.getProperty("elgDir")+"/Init.tcl -inputdir \""+cfg.getProperty("tmpDir")
            +"\" -outputdir \""+cfg.getProperty("outDir")+"\" -encoding \"utf-8\"";
        executeProcess(cmd);
        
        int cnt=0;
        while(dit.hasNext()) {
            Document doc=dit.next();
            cnt+=addAnnotations(doc, cfg.getProperty("outDir")+"/"+(new File(doc.getFile()).getName()), model);
        }
        return cnt;
    }
    
    protected int addAnnotations(Document origDoc, String elgOutFile, DataModel model) {
        int cnt=0;
        Document outDoc=new DocumentImpl(null, elgOutFile);
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
                ins.setAuthor("elg");
                origDoc.getInstances().add(ins);
            }
        }
        return cnt;
    }

    private void executeProcess(String cmd) {
        File workDir=new File("."); // cfg.getProperty("workDir")
        String[] envp=null; //new String[0];
        try {
            if(!workDir.exists() || !workDir.isDirectory()) {
                throw new IOException("Working dir "+workDir+" does not exist or is not a directory.");
            }
            setModel();
            log.LG(Logger.USR, "Executing:\n"+ cmd);            
            elgProc=Runtime.getRuntime().exec(cmd, envp, workDir);
            BufferedReader stdout=new BufferedReader(new InputStreamReader(elgProc.getInputStream()));
            BufferedReader stderr=new BufferedReader(new InputStreamReader(elgProc.getErrorStream()));
            Thread stdoutDump=new Thread(new OutputDumper(stdout));
            Thread stderrDump=new Thread(new OutputDumper(stderr));
            stdoutDump.start();
            stderrDump.start();
            elgProc.waitFor();
        }catch(IOException ex) {
            log.LGERR("Error executing "+workDir+"/"+cmd+": "+ex);
        }catch(InterruptedException ex) {
            log.LGERR("Interrupted waiting for completion of "+cmd+": "+ex);
        }
    }
    
    Pattern patModelStart=Pattern.compile("\\{Use Model\\} %ENTRY% \\{", Pattern.CASE_INSENSITIVE);
    Pattern patModelName=Pattern.compile("[^\r\n}]*", Pattern.CASE_INSENSITIVE);
    private boolean setModel() throws IOException {
        String modelFile=cfg.getProperty("model");
        boolean rc=false;
        if(modelFile!=null && (modelFile=modelFile.trim()).length()>0) {
            String scriptToEdit = cfg.getProperty("elgDir")+"/"+MODEL_SCRIPT;
            File f=new File(scriptToEdit);
            if(f.exists()) {
                String code = Util.readFile(scriptToEdit, "windows-1252");
                Matcher m=patModelStart.matcher(code);
                if(m.find()) {
                    Matcher m2=patModelName.matcher(code);
                    if(m2.find(m.end())) {
                        String code2=code.substring(0,m.end())+modelFile+code.substring(m2.end());
                        Util.writeFile(scriptToEdit, code2, "windows-1252");
                        rc=true;
                        log.LG(Logger.USR,"Set Ellogon model "+modelFile+" in "+scriptToEdit+" (original was "+m2.group()+")");
                    }
                }
            }
            if(!rc) {
                log.LGERR("Could not set Ellogon model "+modelFile+" in "+scriptToEdit);
            }
        }
        return rc;
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
                    log.LG(Logger.USR,line);
                }
            }catch(IOException ex) {
                log.LGERR("Error reading Ellogon output: "+ex);
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
            //doc.setEncoding(cfg.getProperty("encoding"));
            //doc.setContentType(cfg.getProperty("contentType"));
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
        String cfgFile=(args.length>0)? args[0]: "elg.cfg";
        String docFile=(args.length>1)? args[1]: "../Collections/test/a.html";
        EllogonWrapper ew=new EllogonWrapper();
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

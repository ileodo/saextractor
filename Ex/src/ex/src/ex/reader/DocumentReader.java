// $Id: DocumentReader.java 1990 2009-04-22 22:07:59Z labsky $
package ex.reader;

import uep.util.*;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.sql.*;
import ex.features.*;
import ex.reader.annot.SentSplitter;
import ex.train.*;

import medieq.iet.model.Classification;

import org.xml.sax.SAXException;

public class DocumentReader {
    private Logger log;
    private Options o;
    private HashMap<String,CacheItem> cache;
    public int docCounter;
    public String tmpDocDir;
    private HtmlSaxParser parser;
    private static FM fm; // feature manager
    protected static String liveScriptFile="/res/livescript.js";

    public static void main(String[] argv) throws Exception {
        Logger.init("reader.log", -1, -1, null);
        Logger lg=Logger.getLogger("DocumentReader");
        String cfg="config.cfg";
        Options o=Options.getOptionsInstance();
        try {
            o.load(new FileInputStream(cfg));
        }catch(Exception ex) { 
            lg.LG(Logger.WRN,"Cannot find "+cfg+": "+ex.getMessage());
        }
        KB emptyKb=new KB("DocumentKB",1000,5000);
        fm=FM.getFMInstance();
        fm.registerKB(emptyKb);
        //DocumentReader reader=new DocumentReader(new SimpleTokenizer());
        DocumentReader reader=new DocumentReader();
        for (int i=0; i<argv.length; i++) {
            CacheItem ci=reader.cacheItemFromFile(argv[i]);
            if(ci==null)
                continue;
            Document d=reader.parseHtml(ci);
            d.setTokenFeatures(emptyKb.vocab);
        }
        emptyKb.save("document_kb.ser");
        fm.deinit();
    }

    public Document parseHtml(CacheItem ci) {
        Document doc=new Document(ci.absUrl, ci);
        String err=null;
        try {
            parser.parseDocument(doc);
        }catch(SAXException ex) {
            err=ex.getMessage();
        }catch(IOException ex) {
            err=ex.getMessage();
        }
        if(err!=null) {
            log.LG(Logger.ERR,err);
            return null;
        }
        if(o.getIntDefault("sentences", 1)>0) {
            SentSplitter sp=new SentSplitter(parser.getTokenizer());
            sp.annotate(doc);
        }
        return doc;
    }

    public DocumentReader() throws ConfigException {
        this(null);
    }

    public DocumentReader(Tokenizer tok) throws ConfigException {
        o=Options.getOptionsInstance();
        /*
        String cfg="config.cfg";
        try {
            o.load(new FileInputStream(cfg));
        }catch(Exception ex) { 
            Logger.LOG(Logger.WRN,"Cannot find "+cfg+": "+ex.getMessage());
        }
        Logger.init("reader.log", -1, -1, null);
        */
        log=Logger.getLogger("DocumentReader");

        cache=new HashMap<String,CacheItem>(10);
        docCounter=0;
        // create HtmlSaxParser with the supplied or default tokenizer
        if(tok==null) {
            String tokCls=o.getProperty("tokenizer");
            if(tokCls!=null) {
                try {
                    tok=(Tokenizer) Class.forName(tokCls).newInstance();
                }catch(Exception ex) {
                    log.LG(Logger.ERR,"Tokenizer class "+tokCls+" not instantiated, using GrammarTokenizer: "+ex.toString());
                }
            }
            if(tok==null)
                tok=new GrammarTokenizer(); // new SimpleTokenizer();
        }
        parser=new HtmlSaxParser(tok);
        // load live label script
        if(Document.liveScript==null)
            loadLiveScript();
        // dir for temporary downloaded and processed files
        setTempDir();
        // prepare for connecting to DB
        String dbDriver;
        try {
            dbDriver=o.getMandatoryProperty("db_driver");
        }catch(ConfigException ex) {
            log.LG(Logger.ERR,"Config error: "+ex.getMessage());
            return;
        }
        try {
            Class.forName(dbDriver).newInstance(); 
        } catch (Exception ex) { 
            log.LG(Logger.ERR,dbDriver+" could not be instantiated, running without DB");
            ex.printStackTrace();
        }
    }

    private void loadLiveScript() throws ConfigException {
        String fn=null;
        InputStream is=null;
        StringBuffer buff=new StringBuffer(2048);
        try {
            if((fn=o.getProperty("live_script"))!=null) {
                liveScriptFile=fn;
                is=new FileInputStream(fn);
            }else {
                is=this.getClass().getResourceAsStream(liveScriptFile);
            }
            if(is==null) {
                throw new ConfigException("live_script="+liveScriptFile+" was not found in jar!");
            }
            CharBuffer cb=CharBuffer.allocate(1024);
            BufferedReader br=new BufferedReader(new InputStreamReader(is),1024);
            while(br.read(cb)!=-1) {
                cb.flip();
                buff.append(cb);
            }
        }catch(IOException ex) {
            throw new ConfigException("live_script="+liveScriptFile+" was not found!");
        }
        Document.liveScript=buff.toString();
    }

    protected void setTempDir() {
        tmpDocDir=o.getProperty("tmp_doc_dir", "./temp/");
        if(!tmpDocDir.endsWith("/") && !tmpDocDir.endsWith("\\"))
            tmpDocDir+="/";
        // create tmp_doc_dir if it does not exist
        File dir=new File(tmpDocDir);
        if(!dir.exists())
            dir.mkdirs();
    }
    
    public String getTempDir() {
        return tmpDocDir;
    }
    
    /* reading cache items from DB, internet, file */
    public CacheItem cacheItemFromDb(String absUrl) {
        CacheItem ci=null;
        try {
            String objectTable=o.getMandatoryProperty("object_table");
            String resourceTable=o.getMandatoryProperty("resource_table");
            String linkTable=o.getMandatoryProperty("link_table");
            Connection conn=getConnection();
            ci=CacheItem.fromDb(absUrl, conn, resourceTable, linkTable, 
                    true, tmpDocDir, true, cache);  
        }catch(ConfigException ex) {
            log.LG(Logger.ERR,"Config error: "+ex.getMessage());
        }
        return ci;
    }
    
    public CacheItem cacheItemFromInternet(String absUrl) {
        CacheItem ci=null;
        Fetcher f=new Fetcher();
        f.setDirectory(tmpDocDir);
        // fetches the document, caches it under a name based on docCounter into Fetcher.downloadDir 
        int rc=f.fetch(absUrl, new Integer(++docCounter).toString(), true, true, true, true);
        if(rc!=0)
            return null;
        Map<String,CacheItem> res=f.getCacheMap();
        if(!res.containsKey(absUrl))
            return null;
        ci=(CacheItem) res.get(absUrl);
        // make cachedUrl an absolute path
        if(ci!=null && ci.cachedUrl!=null && ci.cachedUrl.indexOf("/")==-1 && ci.cachedUrl.indexOf("\\")==-1) {
            ci.cachedUrl=tmpDocDir+ci.cachedUrl;
        }
        return ci;
    }

    public CacheItem cacheItemFromFile(String fileName) {
        return CacheItem.fromFile(fileName);
    }
    
    public CacheItem cacheItemFromFile(String fileName, String contentType, String encoding, boolean forceEnc) {
        return CacheItem.fromFile(fileName, contentType, encoding, forceEnc);
    }
    
    public CacheItem cacheItemFromString(String source, String url, String cachedFile, String enc, boolean forceEnc, String contentType) {
        String workDir=null;
        if(cachedFile==null || cachedFile.length()==0) {
            cachedFile=new Integer(++docCounter).toString();
            String ext=Fetcher.extensionFor(contentType, url);
            if(ext!=null) {
                // this doc will be clean html even in case we process already annotated documents
                if(ext.equals(".atf"))
                    ext=".html";
                cachedFile+=ext; // incl. dot
            }
        }
        if(cachedFile.indexOf("/")==-1 && cachedFile.indexOf("\\")==-1) {
            workDir=tmpDocDir;
        }
        CacheItem tempCi=new CacheItem(url, null, 0, contentType, enc, 0);
        tempCi.data=source;
        tempCi.cachedUrl=cachedFile;
        int rc=tempCi.saveToDir(workDir);
        if(rc==UtilConst.ERR) {
            // could not write temp file - e.g. access denied. Try system temp file instead.
            try {
                File tmp = File.createTempFile("iet_doc", ".tmp");
                FileOutputStream os = new FileOutputStream(tmp);
                tempCi.saveToStream(os);
                tempCi.cachedUrl = tmp.getAbsolutePath();
                workDir = null;
                os.close();
            }catch(IOException ex) {
                throw new UnsupportedOperationException("Could not write temporary document: "+ex);
            }
        }
        // now reload it from the temp file
        String tmpFile=(workDir!=null)? (workDir+tempCi.cachedUrl): tempCi.cachedUrl;
        return cacheItemFromFile(tmpFile, contentType, enc, forceEnc);
    }

    private Connection getConnection() {
        Connection conn=null;
        try {
            String dbConnection=o.getMandatoryProperty("db_connection");
            conn=DriverManager.getConnection(dbConnection);
        }catch(SQLException ex) {
            log.LG(Logger.ERR,"SQLException: " + ex.getMessage());
            log.LG(Logger.ERR,"SQLState: " + ex.getSQLState());
            log.LG(Logger.ERR,"VendorError: " + ex.getErrorCode());
            return null;
        }catch(ConfigException ex) {
            log.LG(Logger.ERR,"Config error: "+ex.getMessage());
        }
        return conn;
    }

    /** Adds attribute values and instances found in IET document ietDoc to doc as semantic annotations. 
     * Returns the number of added annotations. */
    public int addLabels(Document doc, medieq.iet.model.Document ietDoc) {
        // add document class
        doc.classifications.clear();
        if(ietDoc.getClassifications()!=null) {
            for(Classification cls: ietDoc.getClassifications()) {
                doc.classifications.add(new DocumentClass(cls.getClassName(), cls.getConfidence()));
            }
        }
        
        // add labels
        int cnt=0;
        List<medieq.iet.model.AttributeValue> attVals=ietDoc.getAttributeValues();
        Iterator<medieq.iet.model.AttributeValue> ait=attVals.iterator();
        // this may not be very effective but it ensures we handle correctly both
        // the case where attribute values are listed only as part of instances 
        // and when they are also listed separately:
        HashSet<medieq.iet.model.AttributeValue> avSet=new HashSet<medieq.iet.model.AttributeValue>(attVals.size()); 
        while(ait.hasNext()) {
            medieq.iet.model.AttributeValue av=ait.next();
            if (avSet.contains(av)) {
                log.LG(Logger.ERR,"Attribute value listed repeatedly: "+av+" (ignoring further definitions)");
                continue;
            }
            avSet.add(av);
            log.LG(Logger.INF,"*** ADDING "+av);
            int rc=addLabel(doc, av, null);
            if(rc>0)
                cnt+=rc;
        }
        List<medieq.iet.model.Instance> insts=ietDoc.getInstances();
        Iterator<medieq.iet.model.Instance> iit=insts.iterator();
        while(iit.hasNext()) {
            medieq.iet.model.Instance inst=iit.next();
            ait=inst.getAttributes().iterator();
            while(ait.hasNext()) {
                medieq.iet.model.AttributeValue av=ait.next();
                if (avSet.contains(av)) {
                    // attribute value seen before in the global list is also used as part of instance
                    continue;
                }
                int rc=addLabel(doc, av, inst);
                if(rc>0)
                    cnt+=rc;
            }
            int rc=addLabel(doc, inst);
            if(rc>0)
                cnt+=rc;            
        }
        return cnt;
    }
    
    public int addLabel(Document doc, medieq.iet.model.AttributeValue av, medieq.iet.model.Instance parent) {
        int rc=0;
        List<medieq.iet.model.Annotation> ans=av.getAnnotations();
        Iterator<medieq.iet.model.Annotation> ait=ans.iterator();
        while(ait.hasNext()) {
            medieq.iet.model.Annotation an=ait.next();
            int spos=an.getStartOffset();
            int epos=an.getStartOffset()+an.getLength();
            String text=an.getText();
            TokenAnnot stok=doc.getTokenByStartPos(spos);
            TokenAnnot etok=doc.getTokenByEndPos(epos);
            // todo: populate data correctly, populate parent correctly 
            // (update translation tables of iet objects to ex objects) 
            SemAnnot sa=new SemAnnot(SemAnnot.TYPE_AV, stok.idx, etok.idx, null, -1, av, an);
            doc.addSemAnnot(sa);
        }
        return rc;
    }
    
    public int addLabel(Document doc, medieq.iet.model.Instance inst) {
        int rc=0;
        int acnt=inst.getAttributes().size();
        if(acnt==0)
            return -1;
        medieq.iet.model.AttributeValue avFirst=inst.getAttributes().get(0);
        medieq.iet.model.AttributeValue avLast=inst.getAttributes().get(acnt-1);
        if(avFirst.getAnnotations().size()==0 || avLast.getAnnotations().size()==0)
            return -1;
        medieq.iet.model.Annotation anFirst=avFirst.getAnnotations().get(0);
        medieq.iet.model.Annotation anLast=avLast.getAnnotations().get(avLast.getAnnotations().size()-1);
        int spos=anFirst.getStartOffset();
        int epos=anLast.getStartOffset()+anLast.getLength();
        TokenAnnot stok=doc.getTokenByStartPos(spos);
        TokenAnnot etok=doc.getTokenByEndPos(epos);
        SemAnnot sa=new SemAnnot(SemAnnot.TYPE_INST, stok.idx, etok.idx, null, -1, inst, inst);
        doc.addSemAnnot(sa);
        return rc;
    }
}

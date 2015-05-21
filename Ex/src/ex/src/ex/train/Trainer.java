// $Id: Trainer.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.*;
import java.io.*;
import uep.util.*;

import ex.util.*;
import ex.reader.*;
import ex.model.*;
import ex.features.*;
import java.sql.*;

/** Reads training objects from DB and documents they are referenced from. 
Objects need to be of the classes defined in a given extraction model (EOL).
Outcome of the training are:
 - Training Vocab, consiting of all tokens observed in training objects' attributes and in the referring documents.
 - Training PhraseBook, containing all (somehow preprocesed) attribute values of training objects,
   and also other "relevant" phrases found in documents.
 - Training WrapperBook (comes next)
 */
public class Trainer {
    public Model model;
    public FM fm;
    public DocumentReader dr;

    public Vocab vocab;
    public PhraseBook book;

    private Map<String,Document> documents; // absUrl -> parsed Document
    private Map<String,List<Document>> servers; // server -> List of parsed Documents

    private Connection conn=null;
    private ResultSet serverRows=null;
    private ResultSet resourceRows=null; // select URL_ABS from RESOURCES
    private ResultSet objectRows=null;   // select * from OBJECTS where DESCRIBED_IN = URL_ABS 
    String curServer;   // resourceTable.URL_SERVER
    String curResource; // resourceTable.URL_ABS
    String curObject;   // objectTable.ID

    private Options o;
    public static Logger log;
    String objectTable;
    String resourceTable;
    String linkTable;
    Tokenizer tokenizer;

    public Trainer() {
        o=Options.getOptionsInstance();
        String cfg="config.cfg";
        try {
            o.load(new FileInputStream(cfg));
        }catch(Exception ex) {
            Logger.LOG(Logger.WRN,"Cannot find "+cfg+": "+ex.getMessage());
        }
        Logger.init("trainer.log", -1, -1, null);
        log=Logger.getLogger("Trainer");
        try {
            objectTable=o.getMandatoryProperty("object_table");
            resourceTable=o.getMandatoryProperty("resource_table");
            linkTable=o.getMandatoryProperty("link_table");
        }catch(ConfigException ex) {
            log.LG(log.ERR,"Config error: "+ex.getMessage());
        }
        String tokCls=o.getProperty("tokenizer");
        if(tokCls!=null) {
            try {
                tokenizer=(Tokenizer) Class.forName(tokCls).newInstance();
            }catch(Exception ex) {
                log.LG(log.ERR,"Tokenizer class "+tokCls+" not instantiated, using GrammarTokenizer: "+ex.toString());
            }
        }
        if(tokenizer==null)
            tokenizer=new GrammarTokenizer(); // new SimpleTokenizer();

        fm=FM.getFMInstance();	
        try {
            dr=new DocumentReader(tokenizer);
        }catch(ConfigException ex) {
            log.LG(log.ERR,ex.toString());
        }
        documents=new HashMap<String,Document>(30);
        servers=new HashMap<String,List<Document>>(30);

        conn=getConnection();
    }

    public int train(Model m) {
        KB masterKb=m.kb;
        fm.registerKB(masterKb);
        int serCnt=selectServers();
        while((curServer=getNextServer())!=null) {
            int docCnt=selectDocuments(curServer);
            while((curResource=getNextDocument())!=null) {
                Document doc=readDocument(curResource);
                if(doc==null) {
                    log.LG(log.ERR,"Error reading document '"+curResource+"', skipping.");
                    continue;
                }
                // phrasebook just for the attribute values of the current document's instances
                PhraseBook attBook=new PhraseBookImpl("train", 100, PhraseBook.STATIC_PHRASEINFO, PhraseBook.MATCH_LEMMA, masterKb.vocab);
                int objCnt=loadObjects(doc, masterKb.vocab, attBook);

                int rc=doc.setTokenFeatures(masterKb.vocab);
                if(rc!=Const.EX_OK) {
                    log.LG(log.ERR,"Error setting features in document '"+curResource+"', skipping.");
                    continue;
                }
                rc=processDocument(doc, masterKb, attBook);
                if(rc!=Const.EX_OK) {
                    log.LG(log.ERR,"Error processing document '"+curResource+"'.");
                    continue;
                }
            }
            closeDocuments();
        }
        closeServers();
        return Const.EX_OK;
    }

    public int loadObjects(Document doc, Vocab masterVocab, PhraseBook attBook) {
        String sql="select * from "+objectTable+ " where DESCRIBED_IN=?";
        String sqlc="select count(*) from "+objectTable+ " where DESCRIBED_IN=?";
        int cnt=0;
        PreparedStatement ps=null;
        objectRows=null;
        ArrayList<TokenAnnot> tokBuff=new ArrayList<TokenAnnot>(100);
        try {
            ps=conn.prepareStatement(sqlc);
            ps.setString(1,doc.cacheItem.absUrl);
            objectRows=ps.executeQuery();
            objectRows.first();
            cnt=objectRows.getInt(1);
            log.LG(log.INF,"Loading "+cnt+" objects for document '"+doc.cacheItem.absUrl+
                    "', storing to masterVocab["+masterVocab.size()+"], attBook["+attBook.size()+"]");

            // attKB uses master vocabulary, but only contains attribute phrases
            KB attKb=new KB("Training KB", masterVocab, attBook);
            // make space for objects
            doc.instances=new Instance[cnt];
            // read objects
            ps=conn.prepareStatement(sql);
            ps.setString(1,doc.cacheItem.absUrl);
            objectRows=ps.executeQuery();
            int i=0;
            while(objectRows.next()) {
                Instance ins=Instance.fromDb(model, objectRows, tokenizer, tokBuff, attBook);
                if(ins==null) {
                    log.LG(log.ERR,"Error reading object "+objectRows.getString("ID")+", skipping.");
                    continue;
                }
                doc.instances[i++]=ins;
            }
        }catch(SQLException ex) {
            log.LG(log.WRN,"Error loading objects: "+ex.toString());
            return Const.EX_ERR;
        }
        log.LG(log.INF,"Loaded "+cnt+" extractable objects for document '"+doc.cacheItem.absUrl+"'");
        return cnt;
    }

    public int selectServers() {
        String sql="select distinct URL_SERVER from "+resourceTable;
        String sqlc="select count(distinct URL_SERVER) from "+resourceTable;
        int cnt=0;
        PreparedStatement ps=null;
        serverRows=null;
        try {
            ps=conn.prepareStatement(sqlc);
            serverRows=ps.executeQuery();
            serverRows.first();
            cnt=serverRows.getInt(1);

            ps=conn.prepareStatement(sql);
            serverRows=ps.executeQuery();
        }catch(SQLException ex) {
            log.LG(log.WRN,"Could not select servers: "+ex.toString());
            return Const.EX_ERR;
        }
        log.LG(log.INF,"Selected "+cnt+" servers.");
        return cnt;
    }

    public String getNextServer() {
        curServer=null;
        try {
            if(serverRows.next()==false)
                return null;
            curServer=serverRows.getString(1);
        }catch(SQLException ex) {
            log.LG(log.ERR,"Could not get next server: "+ex.toString());
            return null;
        }
        log.LG(log.INF,"Current server: "+curServer);
        return curServer;
    }

    public void closeServers() {
        try {
            serverRows.close();
        }catch(SQLException ex) {
            log.LG(log.WRN,"Could not close resource resultset: "+ex.toString());
        }
    }

    public int selectDocuments(String server) {
        String sql="select distinct DESCRIBED_IN from "+objectTable+", "+resourceTable+
        " where DESCRIBED_IN=URL_ABS and URL_SERVER=?";
        String sqlc="select count(distinct DESCRIBED_IN) from "+objectTable+", "+resourceTable+
        " where DESCRIBED_IN=URL_ABS and URL_SERVER=?";
        PreparedStatement ps=null;
        resourceRows=null;
        int cnt=0;
        try {
            ps=conn.prepareStatement(sqlc);
            ps.setString(1,server);
            resourceRows=ps.executeQuery();
            resourceRows.first();
            cnt=resourceRows.getInt(1);

            ps=conn.prepareStatement(sql);
            ps.setString(1,server);
            resourceRows=ps.executeQuery();
        }catch(SQLException ex) {
            log.LG(log.WRN,"Could not select documents: "+ex.toString());
            return Const.EX_ERR;
        }
        log.LG(log.WRN,"Selected "+cnt+" documents from server '"+server+"'");
        return cnt;
    }

    public String getNextDocument() {
        curResource=null;
        try {
            if(resourceRows.next()==false)
                return null;
            curResource=resourceRows.getString(1);
        }catch(SQLException ex) {
            log.LG(log.ERR,"Could not get next Document: "+ex.toString());
            return null;
        }
        log.LG(log.INF,"Current document: "+curResource);
        return curResource;
    }

    public Document readDocument(String absUrl) {
        CacheItem ci=dr.cacheItemFromDb(absUrl);
        if(ci==null) {
            log.LG(log.ERR,"Could not get '"+absUrl+"' from DB/disc");
            return null;
        }
        Document d=dr.parseHtml(ci);
        if(d==null)
            log.LG(log.ERR,"Error parsing '"+absUrl+"' cached='"+ci.cachedUrl+"' enc="+ci.encoding);
        else
            log.LG(log.INF,"Document url='"+absUrl+"' cached='"+ci.cachedUrl+"' enc="+ci.encoding+" read OK.");
        return d;
    }

    public int processDocument(Document d, KB masterKb, PhraseBook attBook) {
        int matchCnt=d.findMatches(attBook, PhraseBook.MATCH_LEMMA);
        log.LG(log.INF,"Found "+matchCnt+" attribute candidates in '"+d.id+"'");
        if(matchCnt==0)
            return Const.EX_ERR;
        // int cnt=d.identifyInstances();
        return Const.EX_OK;
    }

    public void closeDocuments() {
        try {
            resourceRows.close();
        }catch(SQLException ex) {
            log.LG(log.WRN,"Could not close resource resultset: "+ex.toString());
        }
    }

    public int saveModels(String fileName) {
        return Const.EX_OK;
    }

    private Connection getConnection() {
        Connection conn=null;
        try {
            String dbConnection=o.getMandatoryProperty("db_connection");
            conn=DriverManager.getConnection(dbConnection);
        }catch(SQLException ex) {
            log.LG(log.ERR,"SQLException: " + ex.getMessage());
            log.LG(log.ERR,"SQLState: " + ex.getSQLState());
            log.LG(log.ERR,"VendorError: " + ex.getErrorCode());
            return null;
        }catch(ConfigException ex) {
            log.LG(log.ERR,"Config error: "+ex.getMessage());
        }
        return conn;
    }

    public static void main(String[] argv) throws Exception {
        // knowledgebase to be trained (now vocab+phrasebook)
        KB modelKb=new KB("TrainKB",1000,5000);

        String modelFile=argv[0];
        Model model=null;
        ModelReader mr=new ModelReader();
        try {
            model=mr.read(modelFile, modelKb);
        }catch(org.xml.sax.SAXException sex) {
            log.LG(log.ERR,"Error XML parsing model "+modelFile+": "+sex.toString());
        }catch(ModelException mex) {
            log.LG(log.ERR,"Error reading model "+modelFile+": "+mex.toString());
        }catch(java.io.IOException iex) {
            log.LG(log.ERR,"Cannot open model "+modelFile+": "+iex.toString());
        }

        Trainer trainer=new Trainer();
        trainer.train(model);
        FM.getFMInstance().deinit();
    }
}

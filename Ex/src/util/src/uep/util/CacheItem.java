// $Id: CacheItem.java 1990 2009-04-22 22:07:59Z labsky $
package uep.util;

import java.util.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.regex.*;
import java.sql.*;

public class CacheItem implements Serializable {
    private static final long serialVersionUID = 8738012935248813003L;
    public static final int HTML=1;
    public static final int IMAGE=2;
    public static final int CSS=3;
    public static final int SCRIPT=4;
    public static final int UNKNOWN=5;

    public static final int CACHE_NONE=1; // item is not cached anywhere
    public static final int CACHE_FILE=2; // item is cached only as file
    public static final int CACHE_DB=3;   // item is cached as file and referred from db

    public static String defHTMLEncoding="utf-8";
    public static String defXMLEncoding="utf-8";

    public String refUrl; // as in referring document
    public String absUrl;
    public String cachedUrl; // as stored on disc
    public String server; // from absUrl
    public byte[] rawData;
    public String data;
    public String contentType;
    public int simpleContentType; // one of the types HTML, CSS...
    public String encoding;
    public int contentLength;
    public long lastModified; // ms since 1970

    public HashMap<String,CacheItem> externals=null;   // external files by referring url
    public int cached=CacheItem.CACHE_NONE;
    public int srcChanged=0;

    protected static Logger log=null;
    private static final Pattern metaEncPat=Pattern.compile("charset\\s*=\\s*([a-zA-Z0-9_\\-]+)");
    
    public CacheItem(String absUrl, byte[] rawData, int cl, String ct, String enc, long lm) {
        this(absUrl, rawData, cl, ct, enc, true, lm);
    }
    
    public CacheItem(String absUrl, byte[] rawData, int cl, String ct, String enc, boolean forceEnc, long lm) {
        if(log==null)
            log=Logger.getLogger("CI");
        this.absUrl=absUrl;
        this.rawData=rawData;
        contentType=(ct!=null)? ct: "unknown";
        simpleContentType=toSimpleContentType(ct);
        encoding=enc;
        if(encoding!=null && forceEnc) {
            // keep specified encoding, repair guessed content type if needed
            if(!isTextual(simpleContentType))
                simpleContentType=HTML;
        }else {
            String detectedEnc=detectEncoding(contentType, rawData);
            if(detectedEnc!=null) {
                encoding=detectedEnc;
                log.LG(Logger.USR,"Detected encoding "+encoding);
            }
        }
        if(encoding==null) {
            setDefaultEncoding();
        }
        log.LG(Logger.USR,"Using encoding "+encoding);
        lastModified=lm;
        contentLength=cl;
        data=null;
        if(rawData!=null) {
            if(rawData.length!=contentLength) {
                log.LGERR("Data in '"+absUrl+"' length mismatch: Content-Length="+
                        contentLength+", real="+rawData.length);
                contentLength=rawData.length;
            }
            if(isTextual(simpleContentType)) {
                try {
                    data=new String(rawData, 0, contentLength, encoding);
                }catch(UnsupportedEncodingException ex) {
                    log.LGERR("Error decoding data in '"+absUrl+"' from encoding '"+encoding+"': "+ex.getMessage());
                    data=null;
                }
            }
        }
    }
    
    // creates a CacheItem without any metadata, must be set manually
    public static CacheItem fromFile(String fileName) {
        return CacheItem.fromFile(fileName, "", "", false);
    }
    
    public static CacheItem fromFile(String fileName, String contentType, String encoding, boolean forceEnc) {
        if(fileName.startsWith("file:///"))
            fileName=fileName.substring(8);
        CacheItem ci=null;
        try {
            File f=new File(fileName);
            byte[] data=null;
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
            int sz=(int)f.length();
            data=new byte[sz];
            int cnt=bis.read(data, 0, sz);
            if(cnt!=sz)
                throw(new IOException("Read "+cnt+" bytes from "+fileName+" instead of "+sz));
            // set empty contentType and encoding so that data doesn' get decoded
            ci=new CacheItem(fileName, data, sz, contentType, encoding, forceEnc, 0);
        }catch(IOException ex) {
            Logger.LOGERR("Error reading CacheItem's data from "+fileName+": "+ex.getMessage());
        }
        return ci;
    }

    public static CacheItem fromDb(String url, Connection conn, String resourceTable, String linkTable, 
            boolean readData, String dataDir, boolean recursive, HashMap<String,CacheItem> cache) {
        // prevent multiple instantiations of the same CacheItem - relying on external cache 
        if(cache!=null && cache.containsKey(url)) {
            CacheItem ci=(CacheItem)cache.get(url);
            if(ci!=null)
                return ci;
        }
        // find CacheItem by url
        String sql="select * from "+resourceTable+" where URL_ABS=?";
        PreparedStatement ps=null;
        ResultSet row=null;
        CacheItem ci=null;
        String cached=null;
        try {
            // get the row with resource
            ps=conn.prepareStatement(sql);
            ps.setString(1,url);
            row=ps.executeQuery();
            if(row.first()==false) {
                throw(new SQLException("CacheItem "+url+" not found in DB"));
            }
            cached=dataDir+"/"+row.getString("URL_CACHED");
            // read CacheItem's data from disc
            byte[] data=null;
            if(readData) {
                File f=new File(cached);
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
                int sz=(int)f.length();
                data=new byte[sz];
                int cnt=bis.read(data, 0, sz);
                if(cnt!=sz) {
                    throw(new IOException("Read "+cnt+" bytes from "+cached+" instead of "+sz));
                }
            }
            // create CacheItem
            ci=new CacheItem(row.getString("URL_ABS"), data, 
                    row.getInt("CONTENT_LENGTH"), row.getString("CONTENT_TYPE"),
                    row.getString("ENCODING"), row.getInt("LAST_MODIFIED"));
            ci.refUrl=row.getString("URL_REF");
            ci.server=row.getString("URL_SERVER");
            ci.cachedUrl=cached;
            row.close();
            ps.close();
            // get external urls
            sql="select URL_TARGET from "+linkTable+" where URL_SOURCE=?";
            ps=conn.prepareStatement(sql);
            ps.setString(1,ci.absUrl);
            row=ps.executeQuery();
            while(row.next()!=false) {
                String resUrl=row.getString(1);
                CacheItem resCi=null;
                if(recursive) {
                    resCi=CacheItem.fromDb(resUrl, conn, resourceTable, linkTable, readData, dataDir, recursive, cache);
                    // warn but continue when resource fails to load
                    if(resCi==null) {
                        log.LG(Logger.WRN,"Could not load external resource "+resUrl+" of "+ci.absUrl);
                    }
                }
                cache.put(resUrl,resCi);
            }
            ci.cached=CACHE_DB;
        }catch(SQLException ex) {
            log.LG(Logger.ERR,"Select SQLException: " + ex.getMessage());
            log.LG(Logger.ERR,"SQLState: " + ex.getSQLState());
            log.LG(Logger.ERR,"VendorError: " + ex.getErrorCode());
            log.LG(Logger.ERR,"SQL: "+sql);
            ci=null;
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error reading CacheItem's data from "+cached+": "+ex.getMessage());
            ci=null;
        }finally {
            if(row!=null) {
                try { row.close(); } catch (SQLException ex) { }
                row=null;
            }
            if(ps!=null) {
                try { ps.close(); } catch (SQLException ex) { }
                ps=null;
            }
        }
        return ci;
    }

    public int saveToStream(OutputStream os) throws IOException {
        if(rawData!=null) {
            os.write(rawData, 0, contentLength);
        }else if(data!=null) {
            OutputStreamWriter out = (encoding!=null)? 
                    new OutputStreamWriter(os, encoding):
                    new OutputStreamWriter(os);
            out.write(data);
            out.flush();
        }else {
            log.LGERR("CacheItem: No data to save for '"+this+"'");
            return UtilConst.NODATA;
        }
        if(cached==CACHE_NONE)
            cached=CACHE_FILE;
        return UtilConst.OK;
    }
    
    public int saveToDir(String dir) {
        String file=cachedUrl;
        if(dir!=null && dir.length()>0)
            file=dir+(dir.endsWith("/")? "": "/")+file;
        int rc = UtilConst.OK;
        try {
            FileOutputStream os = new FileOutputStream(file);
            rc = saveToStream(os);
            os.flush();
            os.close();
        }catch (IOException ex) {
            log.LGERR("Cannot write '"+this+"' to stream: "+ex);
            return UtilConst.ERR;
        }            
        if(cached==CACHE_NONE)
            cached=CACHE_FILE;
        return rc;
    }

    public boolean isInDb(Connection conn, String resourceTable) {
        String sql="select URL_ABS from "+resourceTable+" where URL_ABS=?";
        PreparedStatement ps=null;
        ResultSet row=null;
        boolean rc=false;
        try {
            ps=conn.prepareStatement(sql);
            ps.setString(1,absUrl);
            row=ps.executeQuery();
            if(row.first()==true) {
                rc=true;
            }
        }catch(SQLException ex) {
            log.LGERR("Select SQLException: " + ex.getMessage());
            log.LGERR("SQLState: " + ex.getSQLState());
            log.LGERR("VendorError: " + ex.getErrorCode());
            log.LGERR("SQL: "+sql);
        }finally {
            if(row!=null) {
                try { row.close(); } catch (SQLException ex) { }
                row=null; 
            }
            if(ps!=null) {
                try { ps.close(); } catch (SQLException ex) { }
                ps=null; 
            }
        }
        return rc;
    }

    public int insertToDb(Connection conn, String resourceTable, String linkTable, boolean recursive) {
        return _updateDb(conn, resourceTable, linkTable, recursive, true);
    }

    public int saveToDb(Connection conn, String resourceTable, String linkTable, boolean recursive) {
        return _updateDb(conn, resourceTable, linkTable, recursive, false);
    }

    private int _updateDb(Connection conn, String resourceTable, String linkTable, boolean recursive, boolean insert) {
        boolean isInDb=isInDb(conn, resourceTable);
        if(isInDb && insert) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Insert CacheItem "+absUrl+" cancelled: already is in DB");
            return UtilConst.IGNORE;
        }else if (!isInDb && !insert) {
            log.LG(Logger.ERR,"Update CacheItem "+absUrl+" failed: not in DB");
            return UtilConst.ERR;
        }
        String sql=null;
        String action=null;
        String attrs="URL_ABS=?, URL_CACHED=?, URL_REF=?, CONTENT_LENGTH=?, CONTENT_TYPE=?, "+
        "ENCODING=?, LAST_MODIFIED=?, URL_SERVER=?";
        if(insert) {
            sql="insert into "+resourceTable+" set "+attrs;
            action="insert";
        }else {
            sql="update "+resourceTable+" set "+attrs+" where URL_ABS=?";
            action="update";
        }
        PreparedStatement psSel=null;
        PreparedStatement psUpd=null;
        ResultSet row=null;
        int rc=UtilConst.OK;
        try {
            // try insert/update
            psUpd=conn.prepareStatement(sql);
            psUpd.setString(1,absUrl);
            psUpd.setString(2,cachedUrl);
            psUpd.setString(3,refUrl);
            psUpd.setInt(4,contentLength);
            psUpd.setString(5,contentType);
            psUpd.setString(6,encoding);
            psUpd.setInt(7,(int)lastModified);
            psUpd.setString(8,server);
            if(!insert)
                psUpd.setString(9,absUrl);
            int cnt=psUpd.executeUpdate();
            psUpd.close();
            psUpd=null;
            if(cnt!=1) {
                throw(new SQLException("Could not "+action+" CacheItem "+absUrl));
            }
            // insert/delete differing external urls
            HashMap<String,CacheItem> newLinks=null;
            if(insert) {
                newLinks=externals;
            }else {
                sql="select URL_TARGET from "+linkTable+" where URL_SOURCE=?";
                psSel=conn.prepareStatement(sql);
                psSel.setString(1,absUrl);
                row=psSel.executeQuery(sql);
                HashMap<String,Object> oldLinks=null;
                newLinks=(externals!=null)? new HashMap<String,CacheItem>(externals): null;
                while(row.next()!=false) {
                    String resUrl=row.getString(1);
                    // old link in DB to be deleted - insert
                    if(!externals.containsKey(resUrl)) {
                        if(oldLinks==null)
                            oldLinks=new HashMap<String,Object>(16);
                        oldLinks.put(resUrl,null);
                    }
                    // new links to be inserted into DB - remove
                    if(newLinks!=null && newLinks.containsKey(resUrl)) {
                        newLinks.remove(resUrl);
                    }
                }
                // delete removed links
                if(oldLinks!=null && oldLinks.size()>0) {
                    sql="delete from "+linkTable+" where URL_SOURCE=?, URL_TARGET=?";
                    psUpd=conn.prepareStatement(sql);
                    Iterator<String> it=oldLinks.keySet().iterator();
                    while(it.hasNext()) {
                        String resUrl=it.next();
                        psUpd.setString(1,absUrl);
                        psUpd.setString(2,resUrl);
                        cnt=psUpd.executeUpdate();
                        if(cnt!=1) {
                            throw(new SQLException("Could not delete link from "+absUrl+" to "+resUrl));
                        }
                    }
                    psUpd.close();
                    psUpd=null;
                }
            }
            // insert new links
            if(newLinks!=null && newLinks.size()>0) {
                sql="insert into "+linkTable+" set URL_SOURCE=?, URL_TARGET=?";
                psUpd=conn.prepareStatement(sql);
                Iterator<String> it=newLinks.keySet().iterator();
                while(it.hasNext()) {
                    String resUrl=(String)it.next();
                    psUpd.setString(1,absUrl);
                    psUpd.setString(2,resUrl);
                    cnt=psUpd.executeUpdate();
                    if(cnt!=1) {
                        throw(new SQLException("Could insert link from "+absUrl+" to "+resUrl));
                    }
                }
                psUpd.close();
                psUpd=null;
            }
            // insert/update also external resources?
            if(recursive && externals!=null) {
                Iterator<Map.Entry<String,CacheItem>> it=externals.entrySet().iterator();
                while(it.hasNext()) {
                    Map.Entry<String,CacheItem> e=(Map.Entry<String,CacheItem>)it.next();
                    String resUrl=(String)e.getKey();
                    CacheItem resCi=(CacheItem)e.getValue();
                    if(resCi!=null) {
                        int extRc=resCi._updateDb(conn, resourceTable, linkTable, recursive, insert);
                        // warn but continue when resource fails to update 
                        if(extRc==UtilConst.ERR) {
                            log.LG(Logger.WRN,"Could not "+action+" external resource "+resUrl+" of "+absUrl);
                        }
                    }
                }	
            }
            // update ok - change caching state
            cached=CACHE_DB;
        }catch(SQLException ex) {
            log.LG(Logger.ERR, action+" SQLException: " + ex.getMessage());
            log.LG(Logger.ERR, action+" SQLState: " + ex.getSQLState());
            log.LG(Logger.ERR, action+" VendorError: " + ex.getErrorCode());
            log.LG(Logger.ERR, "SQL: "+sql);
            rc=UtilConst.ERR;
        }finally {
            if(row!=null) {
                try { row.close(); } catch (SQLException ex) { }
                row=null;
            }
            if(psUpd!=null) {
                try { psUpd.close(); } catch (SQLException ex) { }
                psUpd=null; 
            }
            if(psSel!=null) {
                try { psSel.close(); } catch (SQLException ex) { }
                psSel=null; 
            }
        }
        return rc;
    }

    public int addResource(CacheItem res) {
        if(externals==null)
            externals=new HashMap<String,CacheItem>(4);
        externals.put(res.absUrl, res);
        return 0;
    }

    public int setData(String d) {
        data=d;
        try {
            rawData=data.getBytes(encoding);
            contentLength=rawData.length;
        }catch(UnsupportedEncodingException ex) {
            log.LGERR("Error in setData() encoding '"+absUrl+"' to '"+encoding+"': "+ex.getMessage());
            return -1;
        }
        return 0;
    }

    private static final Pattern charsetPat=Pattern.compile("charset\\s*=\\s*[\"']?\\s*([a-zA-Z0-9_\\-]+)");
    public static String detectEncoding(String contentType, byte[] rawData) {
        String enc=null;
        // first use HTTP Content-Type header
        if(contentType!=null) {
            Matcher mat=metaEncPat.matcher(contentType);
            if(mat.find()) {
                enc=mat.group(1).trim();
            }
        }
        // else try to find meta tag in HTML header
        if(enc==null && rawData!=null) {
            ByteArrayInputStream bis=new ByteArrayInputStream(rawData);
            BufferedReader bisr=null;
            try {
                bisr=new BufferedReader(new InputStreamReader(bis, "utf-8"));
                String line=null;
                while((line=bisr.readLine())!=null) {
                    Matcher mat=charsetPat.matcher(line);
                    if(mat.find()) {
                        enc=mat.group(1);
                        break;
                    }
                }
                bisr.close();
                bis.close();
            }catch(IOException e) {
                Logger.LOGERR("Error reading file content: "+e);
            }
        }
        return enc;
    }
    
    /** Sets default encoding for CacheItem's ContentType. */
    private void setDefaultEncoding() {
        if(contentType==null) {
            encoding="";
            return;
        }
        encoding=defaultEncoding(contentType);
    }

    public static int toSimpleContentType(String ct) {
        if(ct==null)
            return UNKNOWN;
        ct=ct.trim().toLowerCase();
        if(ct.indexOf("css")!=-1 ||
                ct.indexOf("style")!=-1)
            return CSS;
        if(ct.indexOf("script")!=-1)
            return SCRIPT;
        if(ct.indexOf("html")!=-1 ||
                ct.indexOf("xhtml")!=-1 ||
                ct.indexOf("xml")!=-1 ||
                ct.indexOf("text")!=-1)
            return HTML;
        if(ct.indexOf("image")!=-1)
            return IMAGE;
        return UNKNOWN;
    }

    public static boolean isTextual(int simpleCType) {
        switch(simpleCType) {
        case HTML:
        case CSS:
        case SCRIPT:
            return true;
        }
        return false;
    }

    public static String defaultEncoding(String ct) {
        ct=ct.trim().toLowerCase();
        if(ct.indexOf("xhtml")!=-1 || ct.indexOf("xml")!=-1)
            return defXMLEncoding;
        if(isTextual(toSimpleContentType(ct)))
            return defHTMLEncoding;
        return "unknown";
    }

    /* using PreparedStatement instead
    public static String escapeDBString(String s) {
	if(s==null)
	    return "";
	s=s.replaceAll("\\\\","\\\\\\\\");
	return s.replaceAll("'","\\\\'");
    }
     */

    public void deleteData() {
        rawData=null;
        data=null;
    }
}

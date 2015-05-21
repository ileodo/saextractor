// $Id: Downloader.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

/*
  Downloads all resources that belong to specified items in database.
  Item range is specified using -s and -e, which determine IDs of items to process.
  Metadata about downloaded resources is stored in table RESOURCE,
  raw data is downloaded to a directory specified by -d.
*/

import java.util.*;
import java.io.*;
import java.sql.*;

public class Downloader implements CacheItemManager {
    public static final String usage=
    "Usage: java Downloader [-cfg CFG] -download_dir DIR -url_field FLD\n"+
    "       [-download_prefix P] -url_field FLD -s 1 -e 20\n"+
    "       [-pause P] [-randomize 1]";
    private Logger log;
    private Fetcher fetcher=null;
    // settings form config:
    private static String cfgFile="./config.cfg";
    private String dbDriver=null;
    private String dbConnection=null;
    private String objectTable=null;
    private String resourceTable=null;
    private String linkTable=null;
    private String downloadDir=null;
    private int pause=0; // sec to wait after each request
    private int randomize=0; // whether to randomly shuffle the order of downloaded pages
    
    public static void main(String args[]) {
        // load global config + cmdline parameters
        Options o=Options.getOptionsInstance();
        if ((args.length >= 2) && args[1].toLowerCase().equals("-cfg")) {
            cfgFile=args[2];
        }
        try { o.load(new FileInputStream(cfgFile)); }
        catch(Exception ex) { System.err.println("Cannot find "+cfgFile+": "+ex.getMessage()); return; }
        o.add(0, args);
        int startKey=-1;
        int endKey=-1;
        String ddir=null;
        String downloadPrefix="";
        String urlField=null;
        try {
            startKey=o.getInt("s");
            endKey=o.getInt("e");
            if(startKey<=0 || endKey<=0 || startKey>endKey) {
                throw(new ConfigException("Start and End keys must be integers>=1"));
            }
            ddir=o.getMandatoryProperty("download_dir");
            urlField=o.getMandatoryProperty("url_field");
            String value=null;
            if((value=o.getProperty("download_prefix"))!=null)
                downloadPrefix=value;
        }catch(ConfigException ex) {
            System.err.println("Config error: "+ex.getMessage());
            System.err.println(usage);
            return;
        }
        // init downloader
        Downloader d=new Downloader(ddir);
        int rc=d.download(urlField, startKey, endKey, downloadPrefix);
        if(rc==UtilConst.OK)
            System.err.println("Download OK");
        else
            System.err.println("Download had problems");
    }

    public Downloader(String ddir) {
        downloadDir=ddir;
        Logger.init("downloader.log", -1, -1, null);
        log=Logger.getLogger("Downloader");
        Options o=Options.getOptionsInstance();
        try {
            dbDriver=o.getMandatoryProperty("db_driver");
            dbConnection=o.getMandatoryProperty("db_connection");
            objectTable=o.getMandatoryProperty("object_table");
            resourceTable=o.getMandatoryProperty("resource_table");
            linkTable=o.getMandatoryProperty("link_table");
            pause=o.getIntLoose("pause");
            randomize=o.getIntLoose("randomize");
        }catch(ConfigException ex) {
            log.LG(Logger.ERR,"Config error: "+ex.getMessage());
        }
        try {
            Class.forName(dbDriver).newInstance(); 
        } catch (Exception ex) { 
            log.LG(Logger.ERR,dbDriver+" could not be instantiated");
            ex.printStackTrace();
        }
        fetcher=new Fetcher();
    }

    public int download(String urlField, int startKey, int endKey, String prefix) {
        fetcher.setDirectory(downloadDir);
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Using tables: "+objectTable+", "+resourceTable+", "+linkTable);
        int rc=prefillCache();
        if(rc==UtilConst.ERR)
            return UtilConst.ERR;

        // make endKey-startKey+1 fetches
        String sql="select ID, "+urlField+" from "+objectTable+" where ID>="+startKey+" and ID<="+endKey;
        Statement st=null;
        ResultSet rows=null;
        Connection conn=null;
        rc=UtilConst.OK;
        try {
            conn=getConnection();
            st=conn.createStatement();
            rows=st.executeQuery(sql);
            int[] order=null;
            int objCnt=endKey-startKey+1;
            if(randomize!=0) {
                RandomArray ra=new RandomArray();
                order=ra.generate(objCnt);
            }
            // put all downloaded URLs into cache
            boolean pauseNeeded=false;
            int key=startKey;
            for(int idx=0; idx<objCnt; idx++) {
                if((idx+1)%20==0) {
                    System.gc();
                }
                // move on to the next object
                if(rows.absolute((randomize!=0)? (order[idx]+1): (idx+1))==false) {
                    log.LG(Logger.WRN,"DB object key mismatch: no more objects in DB when key="+key+"; endKey="+endKey);
                    break;
                }
                int dbKey=rows.getInt(1);
                if(randomize==0 && dbKey!=key) 
                    log.LG(Logger.WRN,"DB object key mismatch: found "+dbKey+" expected "+key+", using "+dbKey);
                key++;
                // wait before fetch
                if(pause>0 && pauseNeeded) {
                    try {
                        Thread.sleep(pause*1000);
                    }catch(InterruptedException ex) {
                        log.LG(Logger.WRN,"Interrupted while sleeping before fetch");
                    }
                }
                // fetch!
                String absUrl=rows.getString(2);
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,(idx+1)+". ["+dbKey+"] downloading "+absUrl);
                int fetchRc=fetcher.fetch(absUrl, prefix+pad4(dbKey), true, true, true, true);
                pauseNeeded=true;
                int insert=1;
                switch(fetchRc) {
                case UtilConst.ERR:
                    log.LG(Logger.ERR,"Could not fetch object "+dbKey+" at "+absUrl);
                    insert=0;
                    break;
                case UtilConst.CACHED:
                    log.LG(Logger.WRN,"Ignoring cached object "+dbKey+" at "+absUrl);
                    insert=0;
                    pauseNeeded=false;
                    break;
                }
                if(fetchRc==UtilConst.ERR)
                    continue;
                // get fetched instance
                CacheItem fetchedItem=(CacheItem)fetcher.getCacheMap().get(absUrl);
                if(fetchedItem==null) {
                    log.LG(Logger.ERR,"Fetched object "+dbKey+" at "+absUrl+" not found after successful fetch!");
                    rc=UtilConst.ERR;
                    break;
                }
                if(insert==0)
                    continue;
                // insert fetched object and referenced objects into DB
                rc=insertFetchedObjects(fetchedItem, fetcher.getCacheMap());
                if(rc==UtilConst.IGNORE) {
                    log.LG(Logger.WRN,"Fetched object "+dbKey+" at "+absUrl+" is already stored in DB, skipping");
                    // rc=updateFetchedObjects(fetchedCi);
                }
                if(rc==UtilConst.ERR) {
                    log.LG(Logger.ERR,"Error storing fetched object "+dbKey+" at "+absUrl+" in DB!");
                    break;
                }
            }
        }catch(SQLException ex) {
            log.LG(Logger.ERR,"Select SQLException: " + ex.getMessage());
            log.LG(Logger.ERR,"SQLState: " + ex.getSQLState());
            log.LG(Logger.ERR,"VendorError: " + ex.getErrorCode());
            log.LG(Logger.ERR,"SQL: "+sql);
            rc=UtilConst.ERR;
        }finally {
            if(rows!=null) { 
                try { rows.close(); } catch (SQLException ex) { }
                rows=null; 
            }
            if(st!=null) { 
                try { st.close(); } catch (SQLException ex) { }
                st=null; 
            }
        }
        return rc;
    }

    /* populates Fetcher's cache with previously downloaded items 
       so that the same absolute URL is never downloaded again */
    public int prefillCache() {
        Map<String,CacheItem> cache=fetcher.getCacheMap();
        fetcher.setCacheItemManager(this); // might be asked by Fetcher to load needed CacheItems from DB
        String sql="select URL_ABS from "+resourceTable;
        Statement st=null;
        ResultSet rows=null;
        Connection conn=null;
        int rc=UtilConst.OK;
        int cnt=0;
        try {
            conn=getConnection();
            st=conn.createStatement();
            rows=st.executeQuery(sql);
            // put all downloaded URLs into cache
            while(rows.next()!=false) {
                String absUrl=rows.getString(1);
                /* we don't need to have real CacheItems in cache - we only need
		   to know we have them cached, and to load them from DB when needed */
                cache.put(absUrl,null);
                cnt++;
            }
        }catch(SQLException ex) {
            log.LG(Logger.ERR,"Select SQLException: " + ex.getMessage());
            log.LG(Logger.ERR,"SQLState: " + ex.getSQLState());
            log.LG(Logger.ERR,"VendorError: " + ex.getErrorCode());
            log.LG(Logger.ERR,"SQL: "+sql);
            rc=UtilConst.ERR;
        }finally {
            if(rows!=null) { 
                try { rows.close(); } catch (SQLException ex) { }
                rows=null; 
            }
            if(st!=null) { 
                try { st.close(); } catch (SQLException ex) { }
                st=null; 
            }
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Preloaded "+cnt+" resources");
        return rc;
    }

    public int updateFetchedObjects(CacheItem master) {
        Connection conn=getConnection();
        if(conn==null)
            return UtilConst.ERR;
        return master.saveToDb(conn, resourceTable, linkTable, true);
    }

    public int insertFetchedObjects(CacheItem master, Map<String,CacheItem> cache) {
        /* cache contains: 
	   - fresh fetched items not yet in DB (we only want to insert these)
	   - previously fetched items in this run, already in DB
	   - cached items from previous runs, in DB, with null values in cache
         */
        Connection conn=getConnection();
        if(conn==null)
            return UtilConst.ERR;
        return master.insertToDb(conn, resourceTable, linkTable, true);
    }

    private Connection getConnection() {
        Connection conn=null;
        try {
            conn=DriverManager.getConnection(dbConnection);
        }catch(SQLException ex) {
            log.LG(Logger.ERR,"SQLException: " + ex.getMessage());
            log.LG(Logger.ERR,"SQLState: " + ex.getSQLState());
            log.LG(Logger.ERR,"VendorError: " + ex.getErrorCode());
            return null;
        }
        return conn;
    }

    public static String pad4(int n) {
        if(n<10)
            return "000"+n;
        if(n<100)
            return "00"+n;
        if(n<1000)
            return "0"+n;
        return ""+n;
    }

    public CacheItem loadCacheItem(String absUrl, boolean withData, boolean recursive) {
        Connection conn=getConnection();
        CacheItem ci=CacheItem.fromDb(absUrl, conn, resourceTable, linkTable, 
                withData, downloadDir, recursive, fetcher.getCacheMap());
        return ci;
    }

    public int insertCacheItem(CacheItem ci, boolean recursive) {
        Connection conn=getConnection();
        int rc=ci.insertToDb(conn, resourceTable, linkTable, recursive);
        return rc;
    }

    public int saveCacheItem(CacheItem ci, boolean recursive) {
        Connection conn=getConnection();
        int rc=ci.saveToDb(conn, resourceTable, linkTable, recursive);
        return rc;
    }
}

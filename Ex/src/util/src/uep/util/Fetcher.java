// $Id: Fetcher.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;

public class Fetcher {
    public static final String usage=
	"Usage: java Fetcher [-cfg CFG] [-download_dir DIR] -u URL [-n NAME]";
    private Logger log;
    private String fetchId=null; // id of a request to fetch a resource including referenced resources
    private int resId=0;         // id of fetched resource within a fetch (0-main document 1,2...-referred)
    private HashMap<String,CacheItem> url2resource=null; // information about each downloaded CacheItem
    private String downloadDir=".";
    public int save=1;           // save all downloaded files to downloadDir
    public int replaceUrls=1;    // replaces url references to saved resources in HTML with cached urls
    public int keepOrigUrls=1;   // keeps original url references as orig_url attributes
    public int keepData=1;       // keep cacheItem.data and cacheItem.rawData in memory
    public long pause=1000;      // milliseconds to wait between fetches to the same server
    public long maxPause=-1;     // if positive and > pause, we wait for a (unif) random time between these 2
    private HashMap<String,Long> hostAccessTimes=null; // hash of last access times to visited servers
    private Calendar cal;
    private Random rnd;
    public static String mimetypeFile="/res/mimetypes.txt"; // overriden by options
    private static HashMap<String,String> type2ext=null;
    private static HashMap<String,String> ext2type=null;

    private CacheItemManager cim=null;

    public static void main(String args[]) {
        Options o=Options.getOptionsInstance();
        // config file must be explicitly given, no default here
        if ((args.length >= 2) && args[0].toLowerCase().equals("-cfg")) {
            try { o.load(new FileInputStream(args[1])); }
            catch(Exception ex) { System.err.println("Cannot find "+args[1]+": "+ex.getMessage()); return; }
        }
        o.add(0, args);
        String url=null;
        String ddir=null;
        String fetchName="untitled";
        try {
            url=o.getMandatoryProperty("u");
            ddir=o.getProperty("download_dir");
            String value=null;
            if((value=o.getProperty("n"))!=null)
                fetchName=value;
        }catch(ConfigException ex) {
            System.err.println("Config error: "+ex.getMessage());
            System.err.println(usage);
            return;
        }
        Fetcher f=new Fetcher();
        if(ddir!=null)
            f.setDirectory(ddir);
        else
            f.setDefaultDirectory();
        f.fetch(url, fetchName);
    }

    public Fetcher() {
        Logger.init("fetcher.log", -1, -1, null);
        log=Logger.getLogger("Fetcher");
        Options o=Options.getOptionsInstance();
        String value=null;
        save=o.getIntDefault("save",1);
        replaceUrls=o.getIntDefault("replace_urls",1);
        keepOrigUrls=o.getIntDefault("keep_orig_urls",1);
        keepData=o.getIntDefault("keep_data",1);
        if((value=o.getProperty("mimetype_file"))!=null) { mimetypeFile=value; }
        if((value=o.getProperty("def_html_encoding"))!=null) { CacheItem.defHTMLEncoding=value; }
        if((value=o.getProperty("def_xml_encoding"))!=null) { CacheItem.defXMLEncoding=value; }
        pause=(long)o.getIntDefault("pause",(int)pause);
        maxPause=(long)o.getIntDefault("max_pause",(int)maxPause);
        loadExtensions();
        url2resource=new HashMap<String,CacheItem>(20);
        hostAccessTimes=new HashMap<String,Long>(20);
        cal=Calendar.getInstance();
        rnd=new Random();
    }

    public int fetch(String url, String fid) {
        Options o=Options.getOptionsInstance();
        boolean frames=(o.getIntDefault("fetch_frames",1)==1);
        boolean images=(o.getIntDefault("fetch_images",1)==1);
        boolean css=(o.getIntDefault("fetch_css",1)==1);
        boolean scripts=(o.getIntDefault("fetch_scripts",1)==1);
        return fetch(url, fid, frames, images, css, scripts);
    }

    public int fetch(String url, String fid,
            boolean fetchFrames, boolean fetchImages, boolean fetchCss, boolean fetchScripts) {
        fetchId=fid;
        resId=0;
        return _fetch(url, fetchFrames, fetchImages, fetchCss, fetchScripts, null);
    }

    // provide absolute url
    private int _fetch(String url, boolean fetchFrames, boolean fetchImages, boolean fetchCss, boolean fetchScripts,
            String forceExt) {
        // fetch unless we have it cached (this ignores ev. changes in parameters though...)
        if(url2resource.containsKey(url)) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Found "+url+" in cache, fetch cancelled");
            CacheItem ci=(CacheItem)url2resource.get(url);
            if(ci==null) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Value of cached resource '"+url+"' not found in cache, loading from DB...");
                if(cim==null) {
                    log.LG(Logger.ERR,"CacheItem manager not set, cannot load resources from previous runs.");
                    return UtilConst.ERR;
                }
                ci=cim.loadCacheItem(url, false, false);
                if(ci==null) {
                    log.LG(Logger.ERR,"CacheItem manager failed to load "+url+" from previous run.");
                    return UtilConst.ERR;
                }
                // put value in cache for next time
                url2resource.put(url,ci);
            }
            return UtilConst.CACHED;
        }
        String ct;
        String enc;
        String server;
        String path;
        long lm;
        int cl;
        byte[] rawData=null;
        try {
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Fetching '"+url+"'");
            URL urlObj=new URL(url);
            server=urlObj.getHost();
            path=urlObj.getPath();
            // wait before next fetch to the same server
            Long lastAccessTime=(Long) hostAccessTimes.get(server);
            long lat=(lastAccessTime==null)? 0: lastAccessTime.longValue();
            long now=cal.getTimeInMillis();
            long thisPause=(maxPause==-1 || maxPause<=pause)? pause: (pause + (rnd.nextLong()%(maxPause-pause)));
            if(now-lat < thisPause) {
                long interval=thisPause-(now-lat);
                try {
                    java.lang.Thread.sleep(interval);
                }catch(java.lang.InterruptedException ex) { 
                    log.LG(Logger.ERR,"Interrupted waiting in fetch: "+ex);
                }
            }
            // open connection, read headers
            URLConnection conn=urlObj.openConnection();
            conn.connect();
            cl=conn.getContentLength();
            ct=conn.getContentType();
            enc=conn.getContentEncoding();
            lm=conn.getLastModified();
            // read binary or text data
            InputStream in=conn.getInputStream();
            BufferedInputStream bufIn=new BufferedInputStream(in);
            int capacity=cl;
            if(capacity<=0)
                capacity=2048;
            rawData=new byte[capacity];
            int bytesRead=0;
            while(cl<=0 || bytesRead<cl) {
                int read;
                try {
                    read=bufIn.read(rawData, bytesRead, rawData.length-bytesRead);
                }catch(IndexOutOfBoundsException ex) {
                    throw new IOException("Read more bytes then Content-Length="+cl);
                }
                if(read==-1) {
                    if(cl<=0)
                        cl=bytesRead;
                    break;
                }
                bytesRead+=read;
                // is there more?
                if(bytesRead==capacity) {
                    byte[] oldRawData=rawData;
                    int oldCapacity=capacity;
                    capacity*=2;
                    rawData=new byte[capacity];
                    System.arraycopy(oldRawData,0,rawData,0,oldCapacity);
                }
            }
            bufIn.close();
            // shrink rawData to the exact size needed
            if(bytesRead!=rawData.length) {
                byte[] oldRawData=rawData;
                rawData=new byte[bytesRead];
                System.arraycopy(oldRawData,0,rawData,0,bytesRead);
            }
            if(bytesRead!=cl)
                throw new IOException("Only read "+bytesRead+" bytes when Content-Length="+cl);
        }
        catch(MalformedURLException mue) {
            log.LG(Logger.ERR,"Invalid URL '"+url+"': "+mue.toString());
            return -1;
        }
        catch(IOException ioe) {
            log.LG(Logger.ERR,"Could not fetch '"+url+"' I/O Network Error: "+ioe.getMessage()+", "+ioe.toString());
            return -1;
        }
        // update lastAccessTime for this server
        if(pause>0)
            hostAccessTimes.put(server,new Long(cal.getTimeInMillis()));
        // create and store the resource
        CacheItem ci=new CacheItem(url, rawData, cl, ct, enc, lm);
        resId++;
        ci.cachedUrl=fetchId+"_"+pad2(resId)+((forceExt!=null)? ("."+forceExt): extensionFor(ci.contentType, path));
        ci.server=server;
        url2resource.put(url, ci);
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Fetched '"+ci.absUrl+"' as '"+ci.cachedUrl+"'");
        // if the resource is an HTML page, fetch also external resources
        switch(ci.simpleContentType) {
        case CacheItem.HTML:
        case CacheItem.CSS:
            break;
        default:
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Content-type: '"+ct+"' is neither HTML or CSS, no externals");
        if(save!=0) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Saving non-HTML "+ci.absUrl+" as "+ci.cachedUrl);
            ci.saveToDir(downloadDir);
            ci.deleteData(); // only keep data on the disk
        }
        return 0;
        }
        byte[] origData=ci.rawData; // keep reference to unmodified source for ev. saving later
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Content-type: '"+ct+"' is HTML, searching for externals");
        // 'parse' doc with regexps and fetch external files, possibly replace referring urls
        int rc;
        if(ci.simpleContentType==CacheItem.HTML) { // HTML
            if(fetchImages) {
                rc=fetchExternals(ci, "enclosure", "url", null, null);
                rc=fetchExternals(ci, "img|input|button", "src", null, null);
            }
            if(fetchCss)
                rc=fetchExternals(ci, "link", "href", "type\\s*=\\s*\"?text/css\"?", "css");
            if(fetchScripts)
                rc=fetchExternals(ci, "script", "src", null, "js");
            if(fetchFrames) {
                rc=fetchExternals(ci, "frame|iframe", "src", null, null, fetchFrames, fetchImages, fetchCss, fetchScripts);
            }
        }else { // CSS may further contain other CSS and images, all encolsed in url() constructs
            rc=fetchCssExternals(ci, fetchImages);
        }
        if(save!=0) {
            if(ci.srcChanged==1 && true) { // keep original downloaded version (for debugging purposes)
                CacheItem orig=new CacheItem(ci.absUrl, origData, origData.length, 
                        ci.contentType, ci.encoding, ci.lastModified);
                orig.cachedUrl=new String(ci.cachedUrl)+"_orig";
                orig.saveToDir(downloadDir);
                orig=null;
            }
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Saving HTML "+ci.absUrl+" as "+ci.cachedUrl);
            ci.saveToDir(downloadDir);
            if(keepData==0)
                ci.deleteData(); // only keep data on the disk
        }
        return 0;
    }

    private int fetchExternals(CacheItem ci, String tag, String att, String cond, String forceExt) {
        return fetchExternals(ci, tag, att, cond, forceExt, false, false, false, false);
    }

    private int fetchExternals(CacheItem ci, String tag, String att, String cond, String forceExt,
            boolean fetchFrames, boolean fetchImages, boolean fetchCss, boolean fetchScripts) {
        Pattern patTag = Pattern.compile("<\\s*("+tag+")\\s+", Pattern.CASE_INSENSITIVE);
        Matcher matTag = patTag.matcher(ci.data);
        Pattern patAtt1 = Pattern.compile(att+"\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Pattern patAtt2 = Pattern.compile(att+"\\s*=\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);
        Pattern patAtt3 = Pattern.compile(att+"\\s*=\\s*([^\\s\">]+)", Pattern.CASE_INSENSITIVE);
        Pattern patCnd = (cond==null)? null: Pattern.compile(cond, Pattern.CASE_INSENSITIVE);
        StringBuffer newSource=null;
        char[] origSource=null;
        int lastCopiedByte=-1;
        if(replaceUrls!=0) {
            newSource = new StringBuffer(ci.data.length()+500); // with replaced url references
            origSource = ci.data.toCharArray();
        }
        while(matTag.find()) {
            int idx1=matTag.end();
            int idx2=ci.data.indexOf(">",idx1);
            String elm=ci.data.substring(idx1,idx2);
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Matched '"+elm+"'");
            if(patCnd!=null) {
                Matcher matCnd=patCnd.matcher(elm);
                if(!matCnd.find()) {
                    continue; // next external if condition not fulfilled
                }
            }
            String url=null;
            Matcher matAtt=patAtt1.matcher(elm);
            if(matAtt.find()) {
                url=matAtt.group(1);
            }
            if(url==null) { // retry with different att=val patterns
                matAtt=patAtt2.matcher(elm);
                if(matAtt.find()) {
                    url=matAtt.group(1);
                }
            }
            if(url==null) {
                matAtt=patAtt3.matcher(elm);
                if(matAtt.find()) {
                    url=matAtt.group(1);
                }
            }
            // url position within page for replacement later
            int idx1url=-1;
            int idx2url=-1;
            int idx2att=-1;
            if(url==null) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Element content '"+elm+"' does not contain attribute '"+att+"'");
                continue;
            }else {
                idx1url=idx1+matAtt.start(1);
                idx2url=idx1+matAtt.end(1);
                idx2att=idx1+matAtt.end();
            }
            // resolve and fetch the url
            CacheItem res=fetchExternalResource(ci, url, forceExt, fetchFrames, fetchImages, fetchCss, fetchScripts);
            if(res==null)
                continue; // ignore errors with subordinated resources
            String cachedUrl=res.cachedUrl;
            // replace referring url with cached
            if(replaceUrls!=0) {
                newSource.append(origSource, lastCopiedByte+1, idx1url-(lastCopiedByte+1));
                newSource.append(cachedUrl);
                lastCopiedByte=idx2url-1;
                if(keepOrigUrls!=0) {
                    newSource.append(origSource, lastCopiedByte+1, idx2att-(lastCopiedByte+1));
                    newSource.append(" orig_url=\""+url+"\"");
                    lastCopiedByte=idx2att-1;
                }
                ci.srcChanged=1;
            }
        }
        if(replaceUrls!=0) {
            newSource.append(origSource, lastCopiedByte+1, ci.data.length()-(lastCopiedByte+1));
            String newSrc=newSource.toString();
            if(true) {
                // comment any javascript redirects from the page, e.g. if(self==top) location.href="/";
                newSrc=newSrc.replaceAll("(location.href\\s*=\\s*\"[^\"]+\")","/*$1*/");
            }
            int rc=ci.setData(newSrc);
            if(rc!=0)
                return -1;
        }
        return 0;
    }

    private URL fixUrl(URL absUrl) {
        // fix absUrl if its path starts with .. like in "protocol://server/../something"
        String path=absUrl.getFile();
        boolean fixes=false;
        while(path.startsWith("/..")) {
            path=path.substring(3);
            fixes=true;
        }
        if(fixes) {
            log.LG(Logger.WRN, "Fixed url path "+absUrl.getFile()+" as "+path);
            try {
                absUrl=new URL(absUrl.getProtocol(), absUrl.getHost(), absUrl.getPort(), path);
            }catch(MalformedURLException ex) {
                log.LG(Logger.ERR,"Cannot fix URL: "+ex.toString());
            }
        }
        return absUrl;
    }

    private CacheItem fetchExternalResource(CacheItem ci, String url, String forceExt,
            boolean fetchFrames, boolean fetchImages, boolean fetchCss, boolean fetchScripts) {
        // convert url to absolute
        String absUrl;
        try {
            url=Fetcher.myUrlEncode(url);
            URL absUrlObj=new URL(new URL(ci.absUrl), url);
            absUrlObj=fixUrl(absUrlObj);
            absUrl=absUrlObj.toString();
        }catch(MalformedURLException ex) {
            log.LG(Logger.ERR,"Document '"+ci.absUrl+"' references bad url '"+url+"': "+ex);
            return null;
        }
        /* catch(UnsupportedEncodingException ex) {
	    log.LG(Logger.ERR,"Error decoding url '"+url+"' from '"+ci.absUrl+"': "+ex);
	    continue;
	    } */
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Found resource '"+url+"'"+(absUrl.equals(url)? "": " ("+absUrl+")"));
        // download resource (unless we already have it)
        int rc=_fetch(absUrl, fetchFrames, fetchImages, fetchCss, fetchScripts, forceExt);
        if(rc==UtilConst.ERR) {
            return null; // ignore errors with external resources (was logged in _fetch)
        }
        // get the resource instance, or only its needed cachedUrl (if cached from previous run)
        CacheItem res=(CacheItem)url2resource.get(absUrl);
        if(res==null) {
            log.LG(Logger.ERR,"Value of external resource "+absUrl+" not found after successful fetch, skipping");
            return null;
        }
        ci.addResource(res);
        return res;
    }

    private int fetchCssExternals(CacheItem ci, boolean fetchImages) {
        Pattern patUrl = Pattern.compile("url\\s*\\(['\"\\s]*([^'\"\\s\\)]+)", Pattern.CASE_INSENSITIVE);
        Matcher matUrl = patUrl.matcher(ci.data);
        StringBuffer newSource=null;
        char[] origSource=null;
        int lastCopiedByte=-1;
        if(replaceUrls!=0) {
            newSource = new StringBuffer(ci.data.length()+32); // with replaced url references
            origSource = ci.data.toCharArray();
        }
        while(matUrl.find()) {
            String url=matUrl.group(1);
            int idx1=matUrl.start(1);
            int idx2=matUrl.end(1);
            // resolve and fetch the url
            CacheItem res=fetchExternalResource(ci, url, null, false, true, true, false);
            if(res==null)
                continue; // ignore errors with subordinated resources
            String cachedUrl=res.cachedUrl;
            // replace referring url with cached
            if(replaceUrls!=0) {
                newSource.append(origSource, lastCopiedByte+1, idx1-(lastCopiedByte+1));
                newSource.append(cachedUrl);
                lastCopiedByte=idx2-1;
                ci.srcChanged=1;
            }
        }
        if(replaceUrls!=0) {
            newSource.append(origSource, lastCopiedByte+1, ci.data.length()-(lastCopiedByte+1));
            String newSrc=newSource.toString();
            int rc=ci.setData(newSrc);
            if(rc!=0)
                return -1;
        }
        return 0;
    }

    public static String absoluteUrl(String relativeUrl, String documentUrl) {
        if(relativeUrl.startsWith("(http|https|ftp)://")) {
            return relativeUrl;
        }
//        String[] rel=relativeUrl.split("/");
//        String[] doc=documentUrl.split("/");
        return relativeUrl;
    }

    public int setDirectory(String dir) {
        try {
            if(!dir.endsWith("/"))
                dir+="/";
            File ddir=new File(dir);
            ddir.mkdirs();
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Storing into "+dir);
        }catch(SecurityException ex) {
            log.LG(Logger.ERR,"No permission to create directory '"+dir+"': "+ex);
            return -1;
        }
        downloadDir=dir;
        return 0;
    }

    public int setDefaultDirectory() {
        Calendar c=Calendar.getInstance();
        c.setTime(new Date());
        String dir="./"+c.get(Calendar.YEAR)+"_"+c.get(Calendar.MONTH)+"_"+c.get(Calendar.DAY_OF_MONTH)+"_"+c.get(Calendar.HOUR)+"_"+c.get(Calendar.MINUTE);
        return setDirectory(dir);
    }

    public static int loadExtensions() {
        int lno=0;
        if(type2ext!=null && ext2type!=null)
            return 0;
        Logger log=Logger.getLogger("Fetcher");
        Options o=Options.getOptionsInstance();
        try {
            BufferedReader in=null;
            String value=null;
            if((value=o.getProperty("mimetypes"))!=null) {
                mimetypeFile=value;
                in=new BufferedReader(new FileReader(mimetypeFile));
            }else {
                InputStream is=Fetcher.class.getResourceAsStream(mimetypeFile);
                if(is!=null) {
                    in=new BufferedReader(new InputStreamReader(is));
                }else {
                    log.LG(Logger.WRN, "Can't load "+mimetypeFile+" from util.jar, using built-in");
                    in=new BufferedReader(new StringReader(FetcherMimetypes.defaults));
                }
            }
            String line;
            type2ext=new HashMap<String,String>(120);
            ext2type=new HashMap<String,String>(120);
            // mimetype \t extension
            while((line=in.readLine())!=null) {
                lno++;
                String[] pair=line.split("\\s+");
                if(pair.length<2) {
                    log.LG(Logger.ERR,"Error reading "+mimetypeFile+"."+lno+": "+line);
                    continue;
                }
                type2ext.put(pair[0].trim(), pair[1].trim());
                for(int i=1;i<pair.length;i++)
                    ext2type.put(pair[i].trim(), pair[0].trim());
            }
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error reading "+mimetypeFile+"."+lno+": "+ex);
            return -1;
        }
        return 0;
    }

    /** Determines file extension for a locally cached file based on a remote URL and 
        MIME type specified by the remote server. Returns empty string if extension cannot be determined. */ 
    public static String extensionFor(String contentType, String pathUrl) {
        if(type2ext==null) {
            loadExtensions();
            if(type2ext==null)
                return "";
        }
        if(contentType!=null) {
            String ext=(String)type2ext.get(contentType);
            if(ext!=null)
                return "."+ext;
            // try stripping encoding information e.g. "text/html;charset=utf-8"
            int idx=contentType.indexOf(";");
            if(idx!=-1) {
                String ct=contentType.substring(0,idx);
                ext=(String)type2ext.get(ct);
                if(ext!=null)
                    return "."+ext;
            }
        }
        if(contentType!=null) 
            Logger.getLogger("Fetcher").LG(Logger.WRN,"No extension for unknown content type '"+contentType+"', guessing from "+pathUrl);
        String ext=null;
        if(pathUrl!=null) {
            int idx=Math.max(pathUrl.lastIndexOf("/"), pathUrl.lastIndexOf("\\"));
            if(idx!=-1)
                ext=pathUrl.substring(idx+1);
            idx=ext.lastIndexOf(".");
            if(idx!=-1) {
                ext=ext.substring(idx); // incl. dot
                Logger.getLogger("Fetcher").LG(Logger.WRN,"Copying observed extension '"+ext+"' from "+pathUrl);
            }else {
                ext=""; // "incl. dot"
                Logger.getLogger("Fetcher").LG(Logger.WRN,"No extension found in "+pathUrl+", using empty");
            }
        }else {
            ext=""; // "incl. dot"
            Logger.getLogger("Fetcher").LG(Logger.WRN,"No extension found for: contentType="+contentType+", url="+pathUrl+", using empty");
        }        
        return ext;
    }
    
    /* Attempts to guess MIME type from url. Returns null if not found. */
    public static String ext2mimeType(String pathUrl) {
        if(type2ext==null) {
            loadExtensions();
            if(type2ext==null)
                return "";
        }
        String type=null;
        int idx=Math.max(pathUrl.lastIndexOf("/"), pathUrl.lastIndexOf("\\"));
        if(idx!=-1)
            pathUrl=pathUrl.substring(idx+1);
        idx=pathUrl.lastIndexOf(".");
        if(idx!=-1) {
            String ext=pathUrl.substring(idx+1);
            type=ext2type.get(ext);
        }
        return type;
    }

    public HashMap<String,CacheItem> getCacheMap() {
        return url2resource;
    }

    public static String pad2(int n) {
        if(n<10)
            return "0"+n;
        return ""+n;
    }

    public void setCacheItemManager(CacheItemManager c) {
        cim=c;
    }

    public static String myUrlEncode(String s) { // throws UnsupportedEncodingException
        s=s.replaceAll("\\\\","/");
        s=s.replaceAll("&amp;","&");
        return s;
        /* not functional for chars like :? - leave bad urls with codepages/unicode as they are for now
        String[] dirs=(s+"/ ").split("/"); // in order not to remove trailing / in split
        String out="";
        for(int i=0;i<dirs.length-1;i++) {
            if(i!=0)
	            out+="/";
            out+=URLEncoder.encode(dirs[i],"iso-8859-2");
        }
        return out; */
    }

    public int saveCache(String fileName) {
        try {
            ObjectOutputStream oos=new ObjectOutputStream(new FileOutputStream(new File(fileName)));
            oos.writeObject(url2resource);
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error saving cache to "+fileName+": "+ex.toString());
            return UtilConst.ERR;
        }
        return UtilConst.OK;
    }

    public int loadCache(String fileName) {
        try {
            ObjectInputStream ois=new ObjectInputStream(new FileInputStream(new File(fileName)));
            url2resource=(HashMap<String,CacheItem>) ois.readObject();
        }catch(IOException ex) {
            log.LG(Logger.WRN,"Cannot load cache from "+fileName+": "+ex.toString());
            return UtilConst.ERR;
        }catch(ClassNotFoundException ex) {
            log.LG(Logger.ERR,"Error loading cache from "+fileName+": "+ex.toString());
            return UtilConst.ERR;
        }
        return UtilConst.OK;
    }
}

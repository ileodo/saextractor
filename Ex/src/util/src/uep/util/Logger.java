// $Id: Logger.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.io.*; 

public class Logger extends LoggerBase {
    protected String name; // this goes into config file - e.g. "parser" or "reader"
    protected int logLevel;    // logLevel of this logger
    protected MasterLogger ml; // using this to do real logging
    protected static boolean enablePause=false;
    
    // this is duplicated here from LoggerBase just to suppress 
    // eclipse warnings for development
    public static final int USR=0;
    public static final int ERR=1;
    public static final int WRN=2;
    public static final int INF=3;
    public static final int TRC=4;
    public static final int MML=5;
    
    protected Logger(String n, int lvl, MasterLogger m) {
        name=n;
        logLevel=lvl;
        ml=m;
    }

    // logging methods
    public void LG(String msg) { LG(INF,msg); }
    public void LGERR(String msg) { LG(ERR,msg); }
    public synchronized void LG(int level, String msg) {
        if(level>logLevel)
            return;
        ml.LG(name, level, msg);
    }
    public boolean IFLG(int level) {
        return level<=logLevel;
    }
    // static logging
    public static void LOGERR(String msg) {
        getLogger().LGERR(msg);
    }
    public static void LOG(int level, String msg) {
        getLogger().LG(level,msg);
    }
    public static boolean IFLOG(int level) {
        return getLogger().IFLG(level);
    }
    // get named logger
    public static Logger getLogger() {
        return MasterLogger.getLogger();
    }
    public static Logger getLogger(String name) {
        return MasterLogger.getLogger(name);
    }
    // init master logger
    public static void init() {
        MasterLogger.init();
    }
    public static void init(String file, int level, int append, String cp) {
        MasterLogger.init(file,level,append,cp);
    }
    // dump to external file once, overwriting previous content
    public void LGX(int level, String data, String fileName) {
        if(level>logLevel)
            return;
        Writer osw=ml.openLogFile(fileName, ml.logCodePage, false);
        if(osw==null)
            return;
        try {
            osw.write(data);
            osw.flush();
            osw.close();
        }catch(IOException ex) {
            return;
        }
    }
    
    public static void pause(String event) {
        if(enablePause) {
            Logger.LOG(Logger.WRN, "Paused ("+event+") Press ENTER to continue...");
            try {
                new BufferedReader(new InputStreamReader(System.in)).readLine();
            }catch(IOException ex) {};
        }
    }
}

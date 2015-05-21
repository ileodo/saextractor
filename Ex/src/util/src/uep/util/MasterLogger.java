// $Id: MasterLogger.java 1989 2009-04-22 18:48:45Z labsky $
package uep.util;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.nio.charset.*;

public class MasterLogger extends LoggerBase {
    public String logFileName; // log file
    public int logLevel;       // global default log level
    public int logAppend;      // append to previous logs
    public String logCodePage;
    private Calendar cal;

    // invalid log level
    public static final int INV=Logger.MML+1;

    public static final String defaultLogFileName="logger.log";
    public static final int defaultLogLevel=Logger.INF;
    public static final String defaultCodePage="utf-8";
    public static final String defaultLoggerName="SYS";    

    //private File logFile;
    private OutputStreamWriter logWriter;

    private static MasterLogger singleton;
    private static final Object lock = new Object();

    // child loggers
    private Map<String,Logger> loggers;

    // logging methods on MasterLogger - call directly or indirectly through child Loggers
    public void LG(String msg) { LG(defaultLoggerName,Logger.INF,msg); }
    public void LGERR(String msg) { LG(defaultLoggerName,Logger.ERR,msg); }
    public void LG(int level, String msg) { LG(defaultLoggerName,level,msg); }
    public synchronized void LG(String name, int level, String msg) {
        //if(level<=logLevel) { return; }
        try {
            cal.setTimeInMillis(System.currentTimeMillis());
            String time=String.format("%1$tH:%1$tM:%1$tS.%1$tL ", cal);
            logWriter.write(logLevels[level]+" "+time+name+": "+msg+"\n");
            logWriter.flush();
        }catch(IOException e) {
            System.err.println("E: MasterLogger cannot write to log file '"+logFileName+"'!");
            System.err.println(logLevels[level]+" "+msg);
        }
        if(level<Logger.INF) {
            System.err.println(logLevels[level].charAt(0)+": "+msg);
        }
    }
    // static logging
    public static void LOGERR(String msg) {
        LOG(Logger.ERR,msg);
    }
    public static void LOG(int level, String msg) {
        MasterLogger log=getInstance();
        log.LG(defaultLoggerName,level,msg);
    }

    /* ML initialization */
    public static MasterLogger init() {
        return MasterLogger.init(null, -1, -1, null);
    }
    public static MasterLogger init(String file, int level, int append, String cp) {
        if(singleton!=null) {
            synchronized (lock) {
                singleton.configureLogLevels();
            }
            return singleton;
        }
        synchronized (lock) {
            if (singleton == null) {
                singleton = new MasterLogger(file, level, append, cp);
            }
        }
        return singleton;
    }
    public static MasterLogger getInstance() { return init(); }
    public static MasterLogger getInstance(String file, int level, int append, String cp) { return init(file, level, append, cp); }

    protected MasterLogger() {
        // System.err.println("-- MasterLogger()");
        logFileName=null;
        logLevel=-1;
        logAppend=-1;
        logCodePage=null;
        initialize();
    }

    protected MasterLogger(String file, int level, int append, String cp) {
        // System.err.println("-- MasterLogger(+)");
        logFileName=file;
        logLevel=level;
        logAppend=append;
        logCodePage=cp;
        initialize();
    }

    protected synchronized OutputStreamWriter openLogFile(String fileName, String codePage, boolean append) {
        File logFile=new File(fileName);
        OutputStreamWriter writer=null;
        if(codePage==null || codePage.length()==0) {
            codePage=defaultCodePage;
        }
        Charset chset=null;
        int i=0;
        while(i++<2) {
            try {
                chset=Charset.forName(codePage);
                break;
            }catch(UnsupportedCharsetException e) {
                System.err.println("E: Unsupported logger encoding '"+codePage+"', defaulting to "+defaultCodePage);
                codePage=defaultCodePage;
            }catch(IllegalCharsetNameException e) {
                System.err.println("E: Illegal name of logger encoding '"+codePage+"', defaulting to "+defaultCodePage);
                codePage=defaultCodePage;
            }
        }
        if(chset==null) {
            System.err.println("E: Could not initialize logger due to invalid encoding");
            return null;
        }
        if(!logFile.exists()) {
            try {
                logFile.createNewFile();
            }catch(IOException e) {
                try {
                    File logFile2 = createLogAsTemp();
                    System.err.println("E: Could not create new log file '"+fileName+"' ("+e+"), redirecting to "+logFile2);
                    logFile=logFile2;
                }catch(IOException e2) {
                    System.err.println("E: Could not create tmp log file ("+e2+"), redirecting to STDERR");
                    writer=new OutputStreamWriter(System.err, chset);
                }
            }
        }
        if(!logFile.canWrite()) {
            try {
                File logFile2 = createLogAsTemp();
                System.err.println("E: Could not write to log file '"+fileName+"', redirecting to "+logFile2);
                logFile=logFile2;
            }catch(IOException e2) {
                System.err.println("E: Could not create tmp log file ("+e2+"), redirecting to STDERR");
                writer=new OutputStreamWriter(System.err, chset);
            }
        }
        if(writer==null) {
            FileOutputStream fos;
            try {
                fos=new FileOutputStream(logFile, append);
                writer=new OutputStreamWriter(fos, chset);
            }catch(IOException e) {
                System.err.println("E: Cannot open output stream to log file '"+fileName+"', redirecting to STDERR");
                try {
                    File logFile2 = createLogAsTemp();
                    System.err.println("E: Could not write stream to log file '"+fileName+"', redirecting to "+logFile2);
                    logFile=logFile2;
                    fos=new FileOutputStream(logFile, append);
                    writer=new OutputStreamWriter(fos, chset);
                }catch(IOException e2) {
                    System.err.println("E: Could not create tmp log file ("+e2+"), redirecting to STDERR");
                    writer=new OutputStreamWriter(System.err, chset);
                }
            }
        }
        return writer;
    }
    
    private File createLogAsTemp() throws IOException {
        File tmpFile = File.createTempFile("iet", ".log");
        return tmpFile;
    }
    
    private void initialize() {
        configure();
        cal=Calendar.getInstance();
        /* open file for write/append in the correct CP */
        logWriter=openLogFile(logFileName, logCodePage, (logAppend>=1));
        configureLogLevels();
    }

    public void configure() {
        Options o=Options.getOptionsInstance();
        String p;
        if(logFileName==null) {
            p=o.getProperty("log_file");
            logFileName=(p==null)? defaultLogFileName: p;
        }
        if(logAppend==-1) {
            logAppend=o.getIntDefault("log_append",0);
        }
        if(logCodePage==null) {
            p=o.getProperty("log_cp");
            logCodePage=(p==null)? defaultCodePage: p;
        }
        if(logLevel==-1)
            logLevel=INV;
    }

    public void configureLogLevels() {
        if(loggers==null) // keeps loggers initialized previously
            loggers=new HashMap<String,Logger>(16);
        Options o=Options.getOptionsInstance();
        String p=o.getProperty("log_level");
        Logger.enablePause=o.getIntDefault("enable_pause",0)!=0;
        int newLogLevel=-1;
        if(p!=null) {
            StringBuffer b=new StringBuffer(64);
            int i;
            for(i=0;i<logLevels.length;i++) {
                if(i>0)
                    b.append('|');
                b.append(logLevels[i]);
            }
            String llevels=b.toString();
            Pattern pairPat=Pattern.compile("([a-z_0-9]+)\\s*:\\s*("+llevels+")",Pattern.CASE_INSENSITIVE);
            Pattern levPat=Pattern.compile("("+llevels+")",Pattern.CASE_INSENSITIVE);
            // split log_level parameter
            String[] pairs=p.split("\\s*[,;]\\s*");
            String name;
            String ll;
            int level;
            for(i=0;i<pairs.length;i++) {
                String segment=pairs[i].trim();
                if(segment.length()==0)
                    continue;
                Matcher pairMat=pairPat.matcher(segment);
                if(pairMat.matches()) {
                    name=pairMat.group(1);
                    ll=pairMat.group(2).toUpperCase();
                    level=logLevel2int(ll);
                    if(level==-1) {
                        LGERR("Config: unknown log level '"+ll+"' in "+segment+" using default");
                        continue;
                    }
                    Logger existing=loggers.get(name);
                    if(existing!=null) {
                        existing.logLevel=level;
                    }else {
                        loggers.put(name, new Logger(name, level, this));
                    }
                }else {
                    Matcher levMat=levPat.matcher(segment);
                    if(levMat.matches()) {
                        ll=levMat.group(1).toUpperCase();
                        level=logLevel2int(ll);
                        if(level==-1) {
                            LGERR("Config: unknown log level '"+ll+"' in "+segment+" using default");
                            continue;
                        }
                        newLogLevel=level;
                    }else {
                        LGERR("Config: cannot parse log_level '"+segment+"'. Possible levels: "+llevels);
                    }
                }
            }
        }
        if(newLogLevel!=-1)
            logLevel=newLogLevel;
        if(logLevel==INV)
            logLevel=defaultLogLevel;
        loggers.put(defaultLoggerName, new Logger(defaultLoggerName, logLevel, this));
    }

    protected int logLevel2int(String lev) {
        for(int i=0;i<logLevels.length;i++) {
            if(lev.equals(logLevels[i]))
                return i;
        }
        return -1;
    }

    /** Get child logger by name, creating one if it does not yet exist. */
    public static Logger getLogger() {
        return getLogger(defaultLoggerName);
    }
    public static Logger getLogger(String name) {
        if(singleton==null)
            init();
        return singleton.getChildLogger(name);
    }

    public Logger getChildLogger(String name) {
        if(loggers.containsKey(name))
            return (Logger) loggers.get(name);
        synchronized (lock) {
            if(!loggers.containsKey(name))
                loggers.put(name,new Logger(name,logLevel,this));
        }
        return (Logger) loggers.get(name);
    }
}

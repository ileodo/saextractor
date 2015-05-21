// $Id: ConsoleServer.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.lang.Process;
import java.lang.Runtime;
import java.util.regex.*;

/* 
   A socket server that writes input to and reads output from a command-line 
   STDIN/STDOUT application. Multiple clients can connect to the server
   at the same time and their requests are processed one-by-one. 
   All clients must implement the ConsoleClient interface.

   WARNING: The underlying console application must flush its STDOUT after 
   each write operation. Otherwise, this server may deadlock while 
   writing commands to console's STDIN and waiting for responses on its STDOUT.
   For example, in perl, remember to call STDOUT->autoflush(); during 
   console application startup.
*/

public class ConsoleServer implements Runnable {
    public static final String usage=
	"Usage: java ConsoleServer [-cfg CFG] -cmd \"CMD\" -port P [-enc E] [-in FILE]\n"+
	"-cmd \"CMD\" command line to run\n"+
	"-in FILE   input to be written to console STDIN after start [none]\n"+
	"-port P    port to listen at [3456]\n"+
	"-enc ENC   encoding used for writing console input and reading its output [utf-8]\n"+
	"-cfg CFG   config file with the above options (must be 1st arg)\n";
    
    public String cmd=null;
    public int port=3456;
    public static int CON_BUFFER_OUT_SIZE=2048;
    public static int CON_BUFFER_ERR_SIZE=512;
    public static String enc="utf-8";
    
    private Process conProcess;
    private OutputStream conInStream;
    private InputStream conOutStream;
    private InputStream conErrStream;
    
    private PrintWriter conInWriter;
    private BufferedReader conOutReader;
    private BufferedReader conErrReader;
    
    private Thread processThread;
    private Thread conOutThread;
    private Thread conErrThread;
    private Thread listenThread;

    protected boolean inited;
    protected ConResult result;
    protected Logger log;
    protected Options opt;
    
    protected ServerSocket listenSocket;
    protected Vector<ConsoleConnection> connections;
    protected boolean busy;

    class ConResult {
	public String value;
	public boolean available;
	public int rc;
	public void clear() {
	    value=null;
	    available=false;
	    rc=UtilConst.ERR;
	}
	public synchronized void waitFor() {
	    try {
	        this.wait(20000);
	        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"notified.");
	    }catch(InterruptedException ex) {
	        log.LG(Logger.ERR,"Interrupted while waiting for console to respond.");
	        rc=UtilConst.ERR;
	    }
	}
	public synchronized void ready() {
	    if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"ready()");
	    this.notifyAll();
	}
    }

    public static void main(String args[]) throws IOException {
        Options o=Options.getOptionsInstance();
        // config file must be explicitly given, no default here
        if ((args.length >= 2) && args[0].toLowerCase().equals("-cfg")) {
            try { o.load(new FileInputStream(args[1])); }
            catch(Exception ex) { System.err.println("Cannot find "+args[1]+": "+ex.getMessage()); return; }
        }
        o.add(0, args);
        // read parameters
        String cmd=null;
        int port=0;
        String inFile=o.getProperty("in");
        String s=o.getProperty("enc");
        if(s!=null)
            enc=s;
        try {
            cmd=o.getMandatoryProperty("cmd");
            port=o.getInt("port");
        }catch(ConfigException ex) {
            System.err.println("Config error: "+ex.getMessage());
            System.err.println(usage);
            return;
        }
        // init and listen
        ConsoleServer cs=new ConsoleServer();
        try {
            cs.init(cmd,enc);
            if(inFile!=null)
                cs.processFile(inFile);
            cs.listen(port);
            // wait for console server to shutdown
            cs.block();
        }catch(IOException ex) {
            System.err.println("ERR Cannot run morphology server: "+ex.toString());
            return;
        }
        cs.deinit(); // already done but...
    }

    public ConsoleServer() {
        Logger.init("console_server.log", -1, -1, null);
        log=Logger.getLogger("ConsoleServer");
        opt=Options.getOptionsInstance();
        result=new ConResult();
        listenSocket=null;
        connections=new Vector<ConsoleConnection>(4);
        busy=false;
    }

    public void init(String c, String e) throws IOException {
        cmd=c; enc=e;
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"INIT "+cmd+", enc="+enc);
        String err=null;
        Runtime rt=Runtime.getRuntime();
        try {
            conProcess=rt.exec(cmd);
        }catch(SecurityException ex) {
            err="No permission to create subprocess: "+ex.toString();
        }catch(IOException ex) {
            err="Cannot run '"+cmd+"': "+ex.toString();
        }catch(Exception ex) {
            err="Wrong cmd '"+cmd+"': "+ex.toString();
        }
        if(err!=null) {
            log.LG(Logger.ERR,err);
            throw(new IOException(err));
        }

        Charset chset=null;
        try {
            chset=Charset.forName(enc);
        }catch(UnsupportedCharsetException ex) {
            err="ERR Unsupported FM client encoding '"+enc+"'";
        }catch(IllegalCharsetNameException ex) {
            err="ERR Illegal name of logger encoding '"+enc+"'";
        }
        if(err!=null) {
            log.LG(Logger.ERR,err);
            throw(new IOException(err));
        }

        // for printing commands to console
        conInStream=conProcess.getOutputStream();
        conInWriter=new PrintWriter(new OutputStreamWriter(conInStream, chset), true); // autoflush
        // for reading output lines from console
        conOutStream=conProcess.getInputStream();
        conOutReader=new BufferedReader(new InputStreamReader(conOutStream, chset), CON_BUFFER_OUT_SIZE);
        // for reading error lines from FM
        conErrStream=conProcess.getErrorStream();
        conErrReader=new BufferedReader(new InputStreamReader(conErrStream, chset), CON_BUFFER_ERR_SIZE);

        inited=true;
        // start process watching thread, output and error reader threads
        processThread=new Thread(this);
        conOutThread=new Thread(this);
        conErrThread=new Thread(this);
        processThread.start();
        conOutThread.start();
        conErrThread.start();
    }

    public void listen(int p) {
        port=p;
        listenThread=new Thread(this);
        listenThread.start();
    }

    public void stopSocketServer() {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Stopping socket server");

        // stop accepting new clients (terminates listenThread)
        try {
            listenSocket.close(); 
        }catch(IOException ex) {
            log.LG(Logger.WRN,"Closed listen socket with problems");
        }

        // disconnect old clients
        for(int i=0;i<connections.size();i++)
            connections.get(i).disconnect();

        connections.clear();
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Stopped socket server");
    }

    public void deinit() {

        // Thread.dumpStack();

        if(!inited)
            return;

        stopSocketServer();

        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Stopping console");
        // close communication
        inited=false;
        conInWriter.close();
        try {
            conOutReader.close();
        }catch(IOException ex) { log.LG(Logger.ERR,"Error closing con.stdout: "+ex.toString()); }
        try {
            conErrReader.close();
        }catch(IOException ex) { log.LG(Logger.ERR,"Error closing con.stderr: "+ex.toString()); }
        // terminate process
        conProcess.destroy();
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Stopped console");
        // notify main (and any other waiters)
        this.notifyAll();
    }

    private synchronized int writeCommand(String cmd) { // don't enter when somebody is in waitForResult() and is not waiting
        if(!inited) {
            log.LG(Logger.ERR,"Not initialized: cannot write command '"+cmd+"'");
            return UtilConst.ERR;
        }
        if(busy)
            result.waitFor(); // wait if somebody else is waiting in waitForResult()
        busy=true;
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"con.in> "+cmd);
        conInWriter.println(cmd);
        return UtilConst.OK;
    }

    private synchronized void waitForResult() { // sync with storeResult()
        if(result.available) // con might have been quick... ;-) 
            return;
        // this.wait(20000); // relinquishes the synchronized lock on this
        result.waitFor(); // relinquishes the synchronized lock on result
        if(!result.available) {
            log.LG(Logger.ERR,"Timed out waiting for con to respond.");
            result.rc=UtilConst.ERR;
        }
        busy=false;
    }

    private void processOutputLine(String s) {
        s=s.trim();
        if(s.equals("<csts>") || s.equals("</csts>"))
            return; // ignore
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"con.out> "+s);
        storeResult(s);
    }

    private void storeResult(String s) {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"storeresult");

        // getResult might be waiting for us in waitForResult() or may have not reached it yet - doesn't matter
        result.value=s;
        result.available=true;
        result.rc=UtilConst.OK;
        // this.notify(); // wake up wait
        result.ready(); // wake up wait
    }

    private void processErrorLine(String s) {
        s=s.trim();
        if(log.IFLG(Logger.WRN)) log.LG(Logger.WRN,"con.err> "+s);
    }

    // the 3 threads above run here
    public void run() {
        Thread me=Thread.currentThread();
        if(me==processThread) {
            // just wait for the console process to finish
            try {
                int rc=conProcess.waitFor();
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"con process ended with rc="+rc);
                deinit();
            }catch (InterruptedException ex) {
                log.LG(Logger.ERR,"Interrupted while waiting for con to terminate: "+ex.getMessage());
                deinit();
            }
        }else if(me==conOutThread) {
            // read line of output if available, otherwise block, end on EOF or IOException
            String line=null;
            try {
                while((line=conOutReader.readLine())!=null) { // while(!EOF)
                    processOutputLine(line);
                }
            }catch(IOException ex) {
                if(inited) {
                    log.LG(Logger.ERR,"Error reading con stdout: "+ex.getMessage());
                    deinit();
                }
            }
        }else if(me==conErrThread) {
            String line=null;
            try {
                while((line=conErrReader.readLine())!=null) { // while(!EOF)
                    processErrorLine(line);
                }
            }catch(IOException ex) {
                if(inited) {
                    log.LG(Logger.ERR,"Error reading con stderr: "+ex.getMessage());
                    deinit();
                }
            }
        }else if(me==listenThread) {
            // open server socket
            try {
                listenSocket=new ServerSocket(port); 
            } catch (IOException ex) {
                log.LG(Logger.ERR,"Could not start server on port "+port+": "+ex.toString());
                deinit();
            }
            log.LG(Logger.USR,"Accepting connections on port "+port+"\n");
            while(true) {
                Socket clientSocket=null;
                try {
                    // wait in accept
                    clientSocket=listenSocket.accept();
                    ConsoleConnection cc=new ConsoleConnection(clientSocket,this);
                    Thread readThread=new Thread(cc);
                    readThread.start();
                }catch(IOException ex) {
                    if(log.IFLG(Logger.WRN)) log.LG(Logger.WRN,"accept() ended");
                    break;
                }
            }
        }
    }

    // only processes 1 word per line
    public synchronized int processFile(String file) throws IOException {
        BufferedReader br=null;
        if(file==null || file.length()==0)
            br=new BufferedReader(new InputStreamReader(System.in));
        else {
            File f=new File(file);
            try {
                br=new BufferedReader(new InputStreamReader(new FileInputStream(f), enc));
            }catch(UnsupportedEncodingException ex) {
                log.LG(Logger.ERR,"Unsupported encoding '"+enc+"': "+ex.getMessage());
                return UtilConst.ERR;
            }
        }
        try {
            String line;
            while((line=br.readLine())!=null) {
                System.out.println(getLemma(line));
            }
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error reading "+file+": "+ex.getMessage());
            return UtilConst.ERR;
        }
        br.close();
        return UtilConst.OK;
    }

    public String processCmd(String cmd) {
        result.clear();
        int rc=writeCommand(cmd);
        waitForResult(); // returns immediately if result is already available, or blocks until it becomes available

        // try { log.LG(log.WRN,"processing...."); Thread.currentThread().sleep(3000); log.LG(log.WRN,"done!"); } catch(InterruptedException ex) { ; }

        if(result.rc==UtilConst.ERR) {
            log.LG(Logger.ERR,"Error processing cmd '"+cmd+"'");
            return null;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"response='"+result.value+"' for cmd='"+cmd+"'");
        return result.value;
    }

    // FM-specific
    private static Pattern patLem=Pattern.compile("<MMl>([^_<\\-]*)"); // finds the first lemma text in CSTS output
    public String getLemma(String word) {
        word=word.trim();
        if(word.length()==0)
            return null;
        result.clear();
        int rc=writeCommand(word);
        waitForResult(); // returns immediately if result is already available, or blocks until it becomes available
        if(result.rc==UtilConst.ERR) {
            log.LG(Logger.ERR,"Error getting lemma for '"+word+"'");
            return null;
        }
        Matcher matLem=patLem.matcher(result.value);
        if(matLem.find()) {
            result.value=matLem.group(1);
        }
        if(result.value==null) {
            log.LG(Logger.ERR,"Lemma not found for '"+word+"'");
            return null;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"getLemma("+word+")="+result.value);
        // extract results from lemmatizer's CSTS output
        return result.value;
    }

    public void removeConnection(ConsoleConnection cc) {
        for(int i=0;i<connections.size();i++)
            if(cc==connections.get(i))
                connections.remove(i);
    }

    public synchronized void block() {
        try {
            this.wait(0);
            if(log.IFLG(Logger.WRN)) log.LG(Logger.WRN,"Stopped waiting in block()");
        }catch(InterruptedException ex) {
            if(log.IFLG(Logger.WRN)) log.LG(Logger.WRN,"Interrupted in block() "+ex.toString());
        }
    }
}

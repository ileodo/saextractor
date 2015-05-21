// $Id: ConsoleClient.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.io.*;
import java.net.*;

/* 
   A client for ConsoleServer
 */

public class ConsoleClient implements Runnable {
    protected Socket socket;
    protected boolean connected;
    protected String host;
    protected int port;
    protected Logger log;
    protected BufferedReader in;
    protected PrintStream out;
    protected String response;
    protected String encoding;
    protected Thread readThread;
    protected boolean busy;

    public ConsoleClient() {
        socket=null;
        connected=false;
        host=null;
        port=-1;
        in=null;
        out=null;
        readThread=null;
        response=null;
        busy=false;
        Logger.init("console_client.log", -1, -1, null);
        log=Logger.getLogger("ConsoleClient");
    }

    public boolean isConnected() {
        return connected;
    }

    public void connect(String h, int p, String enc) throws IOException {
        host=h; port=p; encoding=enc;
        try {
            socket=new Socket(host, port);
            in=new BufferedReader(new InputStreamReader(socket.getInputStream(), encoding));
            out=new PrintStream(socket.getOutputStream());
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Connected to "+host+":"+p);
            readThread=new Thread(this);
            readThread.start();
        }catch(IOException e) {
            log.LG(Logger.WRN,"Cannot connect to "+host+":"+p+": "+e.toString());
            throw e;
        }
    }

    public void disconnect() {
        if(out==null) {
            log.LG(Logger.INF,"Already disconnected");
            return;
        }
        out.close(); // no server disconnect required
        try {
            in.close();
            socket.close();
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".disconnect");
        }catch(IOException ex) {
            log.LG(Logger.WRN,"c"+socket.getLocalPort()+".disconnect (with problems)");
        }
    }

    public synchronized String processCmd(String cmd) {
        if(out==null) {
            log.LG(Logger.ERR,"ConsoleClient not connected!");
            return null;
        }
        busy=true;
        try {
            out.print(cmd);
            out.flush();
            this.wait(25000);
        }catch (InterruptedException e) { 
            log.LG(Logger.ERR,"Interrupted waiting in client of "+host+": "+port+": "+e.toString());
        }
        String r=this.response;
        this.response=null;
        busy=false;
        return r;
    }

    public void run() {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".start_reading");
        String msg=null;
        while(true) {
            try {
                msg=in.readLine();
            }catch(IOException ex) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".error_reading: "+ex.toString());
                break;
            }
            if(msg==null) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".server_disconnected (read null)");
                break;
            }
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+": msg='"+msg+"'");
            if(ConsoleConnection.MSG_END.equals(msg)) {
                disconnect();
                break;
            }
            if(busy) {
                this.response=msg;
                synchronized(this) {
                    this.notifyAll();
                }
            }
        }
        if(busy) {
            this.response=msg;
            synchronized(this) {
                this.notifyAll();
            }
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".end_reading");
    }

    public static void main(String[] args) throws IOException {
        if (args.length<2) {
            System.out.println("Usage: java ConsoleClient <host> <port> <enc>");
            System.exit(-1);
        }
        String host=args[0];
        int port=Integer.parseInt(args[1]);
        String enc=(args.length>2)? args[2]: "utf-8";

        ConsoleClient client=new ConsoleClient();
        client.connect(host,port,enc);

        System.out.println("ConsoleClient");
        System.out.println("Enter command (1 line cmd to be send, 1 line msg to be received, quit to end)");
        BufferedReader user=new BufferedReader(new InputStreamReader(System.in));
        StringBuffer cmd=new StringBuffer(128);

        main:
        while(true) {
            while(cmd.length()==0) {
                System.out.print(host+":"+port+"> ");
                try {
                    String line=user.readLine();
                    cmd.append(line);
                    cmd.append("\n");
                }catch(IOException e) { break main; }
                if(cmd.equals("quit"))
                    break main;
            }
            String response=client.processCmd(cmd.toString());
            System.out.println(response);
            cmd.setLength(0);
        }
    }
}

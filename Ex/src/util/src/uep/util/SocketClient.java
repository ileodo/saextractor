//$Id: SocketClient.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.io.*;
import java.net.*;
import java.nio.CharBuffer;

/** 
   A simple socket client
*/

public class SocketClient implements Runnable {
    protected Socket socket;
    protected String host;
    protected int port;
    protected Logger log;
    protected BufferedReader in;
    protected PrintStream out;
    protected String response;
    protected String encoding;
    protected Thread readThread;
    protected String termSeq;
    protected CharBuffer recvBuff;
    protected int recvBuffSize=2048;
    protected StringBuffer buff;

    public SocketClient() {
        socket=null;
        host=null;
        port=-1;
        in=null;
        out=null;
        readThread=null;
        response=null;
        termSeq="\0";
        recvBuff=CharBuffer.allocate(2048); 
        buff=new StringBuffer(256);
        Logger.init("socket_client.log", -1, -1, null);
        log=Logger.getLogger("SocketClient");
    }

    public boolean isConnected() {
        return (socket!=null) && socket.isConnected();
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
            log.LG(Logger.ERR,"Cannot connect to "+host+":"+p+": "+e.toString());
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

    public synchronized boolean processCmd(String cmd) {
        if(out==null) {
            log.LG(Logger.ERR,"SocketClient not connected!");
            return true;
        }
        out.print(cmd);
        out.print(termSeq);
        out.flush();
        return true;
    }

    public void run() {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".start_reading");
        while(isConnected()) {
            try {
                readMessages(in);
            }catch(IOException ex) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".error_reading: "+ex.toString());
                break;
            }
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+".end_reading");
    }
    
    protected int readMessages(BufferedReader br) throws IOException {
        buff.setLength(0);
        int rc=0;
        while((rc=br.read(recvBuff))!=-1) {
            recvBuff.flip();
            int prevLen=buff.length();
            buff.append(recvBuff);
            recvBuff.clear();
            int termIdx;
            while(buff.length()>0 && (termIdx=buff.indexOf(termSeq, prevLen))!=-1) {
                String msg=buff.substring(0, termIdx);
                processIncomingMsg(msg);
                buff.delete(0, termIdx+termSeq.length());
            }
        }
        return rc;
    }

    public void processIncomingMsg(String msg) {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"c"+socket.getLocalPort()+": msg='"+msg+"'");
        if(msg.equals("@BYE")) {
            disconnect();
            return;
        }
        System.out.println("\nCIMA: "+msg);
        printPrompt();
    }
    
    protected void printPrompt() {
        System.out.print(host+":"+port+"> ");
    }

    public static void main(String[] args) throws IOException {
        if (args.length<2) {
            System.out.println("Usage: java SocketClient <host> <port> <enc>");
            System.exit(-1);
        }
        String host=args[0];
        int port=Integer.parseInt(args[1]);
        String enc=(args.length>2)? args[2]: "utf-8";

        SocketClient client=new SocketClient();
        client.connect(host,port,enc);

        System.out.println("SocketClient");
        System.out.println("Enter command (1 line cmd to be send, 1 line msg to be received, quit to end)");
        BufferedReader user=new BufferedReader(new InputStreamReader(System.in));
        StringBuffer cmd=new StringBuffer(128);

        main:
        while(true) {
            while(cmd.length()==0) {
                client.printPrompt();
                try {
                    String line=user.readLine();
                    cmd.append(line);
                    cmd.append("\n");
                }catch(IOException e) { break main; }
                if(cmd.equals("quit"))
                    break main;
            }
            client.processCmd(cmd.toString());
            cmd.setLength(0);
        }
    }
}

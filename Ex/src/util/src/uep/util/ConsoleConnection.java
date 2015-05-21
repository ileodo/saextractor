// $Id: ConsoleConnection.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.io.*;
import java.net.*;

/* reads from socket, writes to server, registers callback with server, writes response to socket */

public class ConsoleConnection implements Runnable {
    public static final String CMD_BYE="BYE"; // command to perform server disconnect
    public static final String CMD_END="SHU"; // command to shutdown the server
    public static final String MSG_END="END"; // response sent to each client before server shuts down
    public static final String MSG_ERR="ERR"; // response sent if an error occurs while processing command
    protected Socket socket;
    protected ConsoleServer server;
    protected PrintStream out;
    protected BufferedReader in;
    protected Logger log;
    protected Thread readThread;

    public ConsoleConnection(Socket clientSocket, ConsoleServer srv) {
        log=Logger.getLogger("ConsoleClient");
        socket=clientSocket;
        server=srv;
        try {
            in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out=new PrintStream(new BufferedOutputStream(socket.getOutputStream()));
        }catch(IOException ex) {
            log.LGERR("Cannot read/write to client socket: "+ex.toString());
        }
    }

    public int respond(String msg) {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"C"+socket.getLocalPort()+".RESPOND("+msg+")");
        out.println(msg);
        out.flush();
        return 0;
    }

    public void run() {
        readThread=Thread.currentThread();
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"C"+socket.getLocalPort()+".INIT");
        while(true) {
            String cmd;
            try {
                cmd=in.readLine();
            }catch(IOException ex) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"C"+socket.getLocalPort()+".STOP_READING");
                break;
            }
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"C"+socket.getLocalPort()+": cmd='"+cmd+"'");
            if(cmd==null) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"C"+socket.getLocalPort()+".CLIENT_DISCONNECTED");
                break;
            }else if(CMD_BYE.equals(cmd)) {
                disconnect();
                break;
            }else if(CMD_END.equals(cmd)) {
                disconnect();
                server.deinit();
                break;
            }
            String response=server.processCmd(cmd);
            if(response==null)
                response=MSG_ERR;
            respond(response);
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"C"+socket.getLocalPort()+".END");
    }

    public void disconnect() {
        respond(MSG_END);
        out.close();
        try {
            in.close();
            socket.close();
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"C"+socket.getLocalPort()+".DISCONNECT");
        }catch(IOException ex) {
            log.LG(Logger.WRN,"C"+socket.getLocalPort()+".DISCONNECT (with problems)");
        }
        server.removeConnection(this);
    }
}

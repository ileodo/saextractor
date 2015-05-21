// $Id: MorphoClient.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util;

import java.io.*;
import java.util.regex.*;
import uep.util.*;

/* 
   A client that accesses the "Free Morphology" perl script developed by Jan Hajic at UFAL.
   To get it working:
   1. Download Free Morpohlogy (http://ufal.mff.cuni.cz/pdt/Morphology_and_Tagging/Morphology)
   2. Add the following line to the begining of script FMAnalyze.pl:
      STDOUT->autoflush();
      Otherwise, this client may deadlock while writing commands to FM's stdin and waiting for responses on FM's stdout
   3. Set the fmPath and fmDict variables below to reflect your setup
*/

public class MorphoClient extends ConsoleClient {
    public static final String usage=
	"Usage: java MorphoClient [-cfg CFG] [-text FILE]";

    public static void main(String args[]) throws IOException {
        // config file may be explicitly given
        Options o=Options.getOptionsInstance();
        if ((args.length >= 2) && args[0].toLowerCase().equals("-cfg")) {
            try { o.load(new FileInputStream(args[1])); }
            catch(Exception ex) { System.err.println("Cannot find "+args[1]+": "+ex.getMessage()); return; }
        }
        o.add(0, args);
        String inputFile=o.getProperty("text");
        MorphoClient cl=new MorphoClient();
        try {
            cl.connect();
        }catch(IOException ex) {
            System.err.println("ERR Cannot run morphology server: "+ex.getMessage());
            return;
        }catch(ConfigException ex) {
            System.err.println("ERR Cannot run morphology server: "+ex.getMessage());
            return;
        }
        System.out.println("ptáka: "+ cl.getLemma("ptáka"));
        System.out.println("kraba: "+ cl.getLemma("kraba"));
        System.out.println("hada: "+ cl.getLemma("hada"));
        cl.processFile(inputFile);
        cl.disconnect();
    }

    public MorphoClient() {
        host="localhost";
        port=3456;
        encoding="iso-8859-2";
    }

    public void connect() throws IOException, ConfigException {
        Options o=Options.getOptionsInstance();
        String s=o.getProperty("lemmatizer");
        if(s!=null) {
            String[] hostPort=s.trim().split(":");
            if(hostPort.length==2) {
                try {
                    port=Integer.parseInt(hostPort[1]);
                    host=hostPort[0];
                }catch(NumberFormatException ex) {
                    throw new ConfigException("Cannot parse lemmatizer server (expected host:port): "+s+": "+ex.toString());
                }
            }else
                throw new ConfigException("Cannot parse lemmatizer server (expected host:port): "+s);
        }
        s=o.getProperty("lemmatizer_encoding");
        if(s!=null)
            encoding=s;
        connect(host, port, encoding);
    }

    private static Pattern patLem=Pattern.compile("<MMl>([^_<\\-]*)"); // finds the first lemma text in CSTS output
    public String getLemma(String word) {
        word=word.trim();
        if(word.length()==0)
            return null;
        String msg=processCmd(word+"\n");
        if(msg==null) {
            log.LG(Logger.ERR,"Error getting lemma for '"+word+"'");
            return null;
        }
        Matcher matLem=patLem.matcher(msg);
        if(matLem.find()) {
            msg=matLem.group(1);
        }
        if(msg==null) {
            log.LG(Logger.ERR,"Lemma not found for '"+word+"'");
            return null;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"getLemma("+word+")="+msg);
        return msg;
    }

    private void processOutputLine(String s) {
        s=s.trim();
        if(s.equals("<csts>") || s.equals("</csts>"))
            return; // ignore
        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"fm.out> "+s);
    }

    // only processes 1 word per line
    public synchronized int processFile(String file) throws IOException {
        BufferedReader br=null;
        if(file==null || file.length()==0)
            br=new BufferedReader(new InputStreamReader(System.in));
        else {
            File f=new File(file);
            try {
                br=new BufferedReader(new InputStreamReader(new FileInputStream(f), encoding));
            }catch(UnsupportedEncodingException ex) {
                log.LG(Logger.ERR,"Unsupported encoding '"+encoding+"': "+ex.getMessage());
                return Const.EX_ERR;
            }
        }
        try {
            String line;
            while((line=br.readLine())!=null) {
                System.out.println(getLemma(line));
            }
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error reading "+file+": "+ex.getMessage());
            return Const.EX_ERR;
        }
        br.close();
        return Const.EX_OK;
    }
}

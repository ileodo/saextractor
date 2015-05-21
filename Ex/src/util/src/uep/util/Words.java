// $Id: Words.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

public class Words {
    public static final int OP_ADD=1;
    public static final int OP_SUBTRACT=2;
    
    public static boolean ics=false;
    
    protected static void usage() {
        System.out.println("Usage: java Words [-i] file1[:encoding] (-|+) file2[:encoding] [outFile[:outencoding]]");
        System.exit(-1);
    }

    protected static String[] readFileEnc(String fileEnc) {
        String[] rc=null;
        String[] f=fileEnc.split(":");
        if(f.length>2 || f.length==0) {
            usage();
        }else if(f.length==2) {
            rc=f;
        }else {
            rc=new String[2];
            rc[0]=f[0];
            rc[1]="utf-8";
        }
        return rc;
    }
    
    protected static void readUniq(String fileEnc, Map<String,Object> entries) throws IOException {
        String[] fe=readFileEnc(fileEnc);
        BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(new File(fe[0])), fe[1]));
        String s;
        while((s=br.readLine())!=null) {
            s.trim();
            String orig=s;
            if(ics) {
                s=s.toLowerCase();
            }
            entries.put(s, orig);
        }
    }
    
    private static void dump(String fileEnc, Map<String, Object> entries) throws IOException {
        PrintWriter pw;
        if(fileEnc==null) {
            pw=new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
        }else {
            String[] fe=readFileEnc(fileEnc);
            OutputStream os;
            if(fe[0].equals("-"))
                os=System.out;
            else
                os=new FileOutputStream(new File(fe[0]));
            pw=new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, fe[1])));
        }
        for(Object val: entries.values()) {
            pw.println(val);
        }
        pw.flush();
        pw.close();
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length<3 || args.length>5) {
            usage();
        }
        String flags="";
        int argOffset=0;
        if(args[0].startsWith("-")) {
            flags=args[0].substring(1);
            argOffset=1;
        }else if(args.length==5) {
            usage();
        }
        ics=flags.contains("i");

        int op=0;
        if(args[argOffset+1].equals("+"))
            op=OP_ADD;
        else if(args[argOffset+1].equals("-"))
            op=OP_SUBTRACT;
        else
            usage();

        Map<String,Object> s1=new TreeMap<String,Object>();
        readUniq(args[argOffset+0], s1);
        Map<String,Object> s2=new TreeMap<String,Object>();
        readUniq(args[argOffset+2], s2);
        
        switch(op) {
        case OP_ADD:
            s1.putAll(s2);
            break;
        case OP_SUBTRACT:
            for(String key: s2.keySet()) {
                if(s1.containsKey(key)) {
                    // System.err.println("removing: "+key);
                    s1.remove(key);
                }
            }
            break;
        }
        
        dump(args.length>(argOffset+3)? args[argOffset+3]: null, s1);
    }
}

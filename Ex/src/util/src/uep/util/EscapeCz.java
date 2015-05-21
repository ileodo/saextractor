// $Id: $
package uep.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EscapeCz {
    static String sHacek = "ěščřžďťňĚŠČŘŽĎŤŇ";
    static String sCarka = "áéíóúýÁÉÍÓÚÝ";
    static String sKrouzek = "ůŮ";
    
    static String src=sHacek+sCarka+sKrouzek;
    static String tgt="escrzdtnESCRZDTNaeiouyAEIOUYuU";
    static Map<String,String> map=new HashMap<String,String>();
    static {
        for(int i=0;i<src.length();i++) {
            map.put(src.substring(i,i+1), tgt.substring(i,i+1));
        }
    }
    
    static Pattern hacek=Pattern.compile("(["+sHacek+"])", Pattern.UNICODE_CASE);
    static Pattern carka=Pattern.compile("(["+sCarka+"])", Pattern.UNICODE_CASE);
    static Pattern krouzek=Pattern.compile("(["+sKrouzek+"])", Pattern.UNICODE_CASE);
    static Pattern[] pats={hacek,carka,krouzek};
    static String[] signs={"\\v","\\'","\\r"};
    
    public static String esc(String s) {
        // System.err.print("-> "+s+"\n");
        StringBuffer b=new StringBuffer(128);
        int j=0;
        for(Pattern p: pats) {
            String sign = signs[j++];
            Matcher m = p.matcher(s);
            int i=0;
            while(m.find()) {
                b.append(s.substring(i,m.start()));
                b.append(sign);
                b.append("{");
                b.append(map.get(m.group()));
                b.append("}");
                i=m.end();
            }
            b.append(s.substring(i));
            s=b.toString();
            b.setLength(0);
        }
        // System.err.print("-> "+s+"\n");
        return s;
    }
    
    public static void main(String[] args) throws IOException {
        if(args.length!=2) {
            throw new IllegalArgumentException("Usage: EscapeCz in_enc out_enc < text.tex");
        }
        String enc1 = args[0];
        String enc2 = args[1];
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in, enc1));
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(System.out, enc2));
        String line;
        while((line=in.readLine())!=null) {
            out.write(esc(line));
            out.write("\n");
        }
        out.flush();
        out.close();
        in.close();
    }
}

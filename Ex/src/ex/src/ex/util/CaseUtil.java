// $Id: CaseUtil.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import uep.util.Logger;
import uep.util.Options;

public class CaseUtil {
    protected static String mapFile="/res/unaccent.txt";
    protected static Map<Character,Character> charMap=null;
    protected static StringBuffer sb=new StringBuffer(128);
    
    public static String removeAccents(String str) {
        if(charMap==null) {
            InputStream is=null;
            try {
                String fn;
                if((fn=Options.getOptionsInstance().getProperty("accent_map"))!=null) {
                    mapFile=fn;
                    is=new FileInputStream(mapFile);
                }else {
                    is=CaseUtil.class.getResourceAsStream(mapFile);
                }
                if(is!=null) {
                    readAccentMap(is, mapFile);
                }else {
                    Logger.LOG(Logger.ERR,"Cannot find tag type file "+mapFile);
                    charMap=Collections.emptyMap();
                }
            }catch(IOException ex) {
                Logger.LOG(Logger.ERR,"Error reading tag type file "+mapFile);
            }
        }
        int chng=0;
        for(int i=0;i<str.length();i++) {
            char c1=str.charAt(i);
            Character c2=charMap.get(c1);
            if(c2!=null) {
                sb.append(c2);
                chng++;
            }else {
                sb.append(c1);
            }
        }
        if(chng>0) {
            str=sb.toString();
        }
        sb.setLength(0);
        return str;
    }

    private static void readAccentMap(InputStream is, String mapFile) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String s;
        int lno=0;
        charMap=new TreeMap<Character,Character>();
        while((s=br.readLine())!=null) {
            lno++;
            s=s.trim();
            int len=s.length();
            if(len==0 || s.startsWith("#"))
                continue;
            String[] pair=s.split("\\s*;\\s*");
            if(pair.length!=2) {
                Logger.LOGERR("Error reading "+mapFile+":"+lno);
                continue;
            }
            int from=Integer.parseInt(pair[0], 16);
            char to=(char) Integer.parseInt(pair[1], 16);
            if(((int)(char)from)!=from) {
                Logger.LOG(Logger.WRN,"Can't map char 0x"+pair[0]+"("+(char)from+") to "+(char)to+", "+mapFile+":"+lno);
                continue;
            }
            charMap.put((char)from, to);
        }
    }
}

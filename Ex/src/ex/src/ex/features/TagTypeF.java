// $Id: TagTypeF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.io.*;
import java.util.*;
import uep.util.*;

/** Copies constants from NekoHTML parser
*/
public class TagTypeF extends TagF implements EnumFeature {
    public static final int OTHER    =0;
    public static final int A        =1;
    public static final int STYLE    =2;
    public static final int HEADING  =3;
    public static final int INLINE   =4;
    public static final int BLOCK    =5;
    public static final int CONTAINER=6;
    public static final int OBJECT   =7;
    public static final String STR_OTHER="OTHER";
    public static String[] valueNames={STR_OTHER};
    public static int[] tag2type;
    protected Logger log;
    protected static String tagTypeFile="/res/tagtypes.txt";

    private static TagTypeF singleton;
    public static TagTypeF getSingleton() { return singleton; }

    protected TagTypeF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=1;
        log=Logger.getLogger("FM");
        Options o=Options.getOptionsInstance();
        String fn=null;
        InputStream is=null;
        try {
            if((fn=o.getProperty("tag_type_file"))!=null) {
                tagTypeFile=fn;
                is=new FileInputStream(tagTypeFile);
            }else {
                is=this.getClass().getResourceAsStream(tagTypeFile);
            }
            if(is!=null) {
                readTagTypes(is, tagTypeFile);
            }else {
                log.LG(Logger.ERR,"Cannot find tag type file "+tagTypeFile);
            }
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error reading tag type file "+tagTypeFile);
        }
        singleton=this;
    }

    public String toString(int val) {
        return valueNames[val];
    }

    public int fromString(String name) {
        for(int i=0;i<valueNames.length;i++)
            if(valueNames[i].equals(name))
                return i;
        return -1;
    }

    public static int getValue(int tagId) {
        return tag2type[tagId];
    }

    public static String getValueToString(int tagId) {
        return valueNames[tag2type[tagId]];
    }

    private void readTagTypes(InputStream is, String resName) throws IOException, FileNotFoundException {
        TagNameF tnf=(TagNameF)TagNameF.getSingleton();
        tag2type=new int[tnf.valueCnt];
        // all tags are of type OTHER by default
        for(int i=0;i<tag2type.length;i++)
            tag2type[i]=OTHER;
        // read line by line, registering new types and assigning the current type to each tag found
        HashMap<String,Integer> typeNames=new HashMap<String,Integer>(10);
        typeNames.put(STR_OTHER, new Integer(OTHER));
        int curTypeId=-1;
        HashMap<String,Object> seenTags=new HashMap<String,Object>(64);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String s;
        int lno=0;
        while((s=br.readLine())!=null) {
            lno++;
            s=s.trim();
            int len=s.length();
            if(len==0)
                continue;
            // type_name:
            if(s.charAt(len-1)==':') {
                s=s.substring(0,len-1).trim();
                Integer intgr=(Integer)typeNames.get(s);
                if(intgr!=null) {
                    curTypeId=intgr.intValue();
                }else {
                    curTypeId=typeNames.size();
                    typeNames.put(s, new Integer(curTypeId));
                }
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"TagType '"+s+"'");
                continue;
            }
            // tag_name
            if(seenTags.containsKey(s)) {
                String msg=resName+"."+lno+": Tag '"+s+"' has been already assigned a type.";
                log.LG(Logger.ERR,msg);
                throw(new IOException(msg));
            }
            seenTags.put(s, null);
            if(curTypeId==-1) {
                String msg=resName+"."+lno+": No type to assign to tag '"+s+"'.";
                log.LG(Logger.ERR,msg);
                throw(new IOException(msg));
            }
            short tagId=(short)tnf.valueOf(s);	    
            if(id==TagNameF.UNK_TAG) {
                String msg=resName+"."+lno+": Non-HTML tag '"+s+"' ignored.";
                log.LG(Logger.WRN,msg);
                continue;
            }
            tag2type[tagId]=curTypeId;
        }
        br.close();
        // build valueNames
        valueNames=new String[typeNames.size()];
        Set<Map.Entry<String,Integer>> entrySet=typeNames.entrySet();
        Iterator<Map.Entry<String,Integer>> it=entrySet.iterator();
        while(it.hasNext()) {
            Map.Entry<String,Integer> entry=(Map.Entry<String,Integer>)it.next();
            valueNames[((Integer)entry.getValue()).intValue()]=(String)entry.getKey();
        }
    }
    
    public String enumerateValues(StringBuffer buff) {
        StringBuffer b=(buff==null)? new StringBuffer(512): buff;
        for(short i=0;i<valueNames.length;i++) {
            if(i>0)
                b.append(",");
            b.append(valueNames[i]);
        }
        String ret=(buff==null)? b.toString(): null; 
        return ret;
    }
}

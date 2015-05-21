// $Id: TokenTypeF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.Arrays;
import java.util.List;

import uep.util.Logger;

/** Token types taken from the currently used tokenizer.
*/
public class TokenTypeF extends TokenF implements EnumFeature {
    public static String[] valueNames={"NA"}; // e.g. NUMERIC, ALPHA, ALPHANUM
    private static TokenTypeF singleton;
    public static TokenTypeF getSingleton() { return singleton; }

    protected TokenTypeF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        singleton=this;
    }

    public String toString(int val) {
        return valueNames[val];
    }

    public int fromString(String name) {
        for(int i=0;i<valueNames.length;i++) {
            if(valueNames[i].equals(name))
                return i;
        }
        return -1;
    }

    public void setTokenTypes(String[] types) {
        // copy token type constants from the currently used tokenizer 
        if(valueCnt>0)
            return;
        valueCnt=types.length;
        valueNames=new String[types.length];
        StringBuffer lg=new StringBuffer(128);
        for(int i=0;i<types.length;i++) {
            String type=types[i];
            if(type.charAt(0)=='<' && type.charAt(type.length()-1)=='>')
                type=type.substring(1,type.length()-1);
            if(i>0) 
                lg.append(',');
            lg.append(type);
            valueNames[i]=type;
        }
        if(Logger.IFLOG(Logger.INF)) Logger.LOG(Logger.INF,lg.toString());
    }
    
    public List<String> getValues() {
        return Arrays.asList(valueNames);
    }
}

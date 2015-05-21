// $Id: TokenCapF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.Arrays;
import java.util.List;

public class TokenCapF extends TokenF implements EnumFeature {
    public static final int XX=0;
    public static final int UC=1;
    public static final int LC=2;
    public static final int CA=3;
    public static final int MX=4;
    public static final int NA=5;
    public static String[] valueNames={
        "XX","UC","LC","CA","MX","NA"
    };
    private static TokenCapF singleton;
    public static TokenCapF getSingleton() { return singleton; }

    protected TokenCapF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        valueCnt=valueNames.length;
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

    public int valueOf(String tok) {
        // don't know how to do this easily with regexps because
        // \u0100-\u017f and \u0180-\u024f contain mixed upper/lower case chars
        int len=tok.length();
        boolean lc=false;
        boolean uc=false;
        boolean ca=false;
        char c=tok.charAt(0);
        if(Character.isUpperCase(c)) {
            ca=true;
        }
        for(int i=0; i<len; i++) {
            c=tok.charAt(i);
            if(Character.isUpperCase(c)) {
                uc=true;
                if(lc)
                    break;
            }else if(Character.isLowerCase(c)) {
                lc=true;
                if(uc)
                    break;
            }
        }
        if(lc) {
            if(ca)
                return CA; // Ahoj, Aa9b, AA9b, Aa9B
            if(uc)
                return MX; // aHoj, aA9b, aA9B
            else
                return LC; // ahoj, a9b
        }
        // !lc
        if(uc)
            return UC;
        return NA;
    }

    public String apply(String tok) {
        return toString(valueOf(tok));
    }

    public List<String> getValues() {
        return Arrays.asList(valueNames);
    }
}

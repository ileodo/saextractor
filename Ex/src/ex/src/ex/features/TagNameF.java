// $Id: TagNameF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import org.cyberneko.html.HTMLElements;
import org.cyberneko.html.HTMLElements.Element;

/** Copies constants from NekoHTML parser
*/
public class TagNameF extends TagF implements EnumFeature {
    public static final int UNK_TAG=HTMLElements.UNKNOWN;
    public static final int HTML_COLUMN=UNK_TAG+1;
    public static final int TEXTNODE=UNK_TAG+2;
    public static final int HTML_LAST=TEXTNODE;

    public static String[] valueNames; // e.g. A, B, P, IMG
    private static TagNameF singleton;
    public static TagNameF getSingleton() { return singleton; }

    protected TagNameF(int featureId, String featureName) {
        super(featureId, featureName, VAL_ENUM);
        setTagNames();
        singleton=this;
    }

    public String toString(int val) {
        return valueNames[val];
    }

    public int fromString(String name) {
        return valueOf(name);
    }

    public int valueOf(String name) {
        Element el=HTMLElements.getElement(name);
        return el.code;
    }

    private void setTagNames() {
        // here we rely on HTMLElements.UNKNOWN being the last "element" in Neko
        valueCnt=HTML_LAST+1;
        valueNames=new String[valueCnt];
        for(short i=0;i<=HTMLElements.UNKNOWN;i++) {
            Element el=HTMLElements.getElement(i);
            valueNames[i]=el.name;
        }
        valueNames[UNK_TAG]="UNK_TAG"; // replacing ""
        valueNames[HTML_COLUMN]="COLUMN";
        valueNames[TEXTNODE]="$TEXT";
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

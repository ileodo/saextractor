// $Id: HtmlAttribute.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

public class HtmlAttribute {
    public int type;
    public TokenAnnot[] tokens;
    // FIXME: remove string from here and only point via 2 indices to document.tokenString 
    // which holds all xml-processed cdata and attribute values in Document
    public String string;
    public HtmlAttribute(int t, String val, TokenAnnot[] toks) {
        type=t;
        string=val;
        tokens=toks;
    }
}

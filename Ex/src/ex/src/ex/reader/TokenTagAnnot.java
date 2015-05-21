// $Id: TokenTagAnnot.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

// found in a Document by html parser
public class TokenTagAnnot extends TokenAnnot {
    // names and TokenAnnot[] values of attributes
    public HtmlAttribute[] attributes;

    public TokenTagAnnot(int tagId, int start, int end, Annot par, int parIdx, int i, int tag) {
        super(tagId, start, end, par, parIdx, i, null);
        annotType=ANNOT_TOKEN_TAG;
        attributes=null;
    }
}

// $Id: DocSegment.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

public class DocSegment {
    public DocSegment(int startIdx, int len) {
        this.startIdx=startIdx;
        this.len=len;
    }
    public int startIdx;
    public int len;
}

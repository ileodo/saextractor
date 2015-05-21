// $Id: Label.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

public class Label {
    public int id; // id within LabelGroup
    //public int startIdx; // char pos within group's innerHTML
    //public int endIdx;
    // char positions within group's innerHTML; label may be broken into more sublabels to prevent crossing tags:
    public int[] idxs;
    public String title;
    public String style;
    //    public Label(int i, int sp, int ep, String st, String ti) {
    //	id=i; startIdx=sp; endIdx=ep; style=st; title=ti;
    public Label(int i, int[] indices, String st, String ti) {
        id=i; idxs=indices; style=st; title=ti;
    }
}

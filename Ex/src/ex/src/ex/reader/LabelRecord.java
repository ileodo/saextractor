// $Id: LabelRecord.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import java.util.Vector;

public class LabelRecord {
    public Object label; // ex.ac.AC or ex.ac.PatMatch
    public int cnt;
    public LabelRecord() {
        label=null; cnt=0;
    }
    // recycling code
    protected static Vector<LabelRecord> freeList=new Vector<LabelRecord>(8); // Vector is synchronized internally
    public static LabelRecord getInstance() {
        int n=freeList.size();
        if(n>0)
            return (LabelRecord) freeList.remove(n-1);
        return new LabelRecord();
    }
    public void disposeInstance() {
        this.label=null;
        this.cnt=0;
        freeList.add(this);
    }
}

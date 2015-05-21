// $Id: FIntList.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.*;
import ex.features.*;

/** 
  Light-weight serializable forward-only linked list
*/
public class FIntList implements Serializable {
    private static final long serialVersionUID = -9008378716698776649L;
    transient IntVal first;
    transient int size;

    public FIntList() {
        first=null;
        size=0;
    }

    private static class IntVal {
        int id;
        int val;
        IntVal next;
        IntVal(int id, int val, IntVal next) {
            this.id=id;
            this.val=val;
            this.next=next;
        }
    }

    public int size() {
        return size;
    }

    public void set(int id, int val) {
        if(first==null) {
            first=new IntVal(id, val, null);
            size++;
            return;
        }
        if(id<first.id) {
            first=new IntVal(id, val, first);
            size++;
            return;
        }
        IntVal e=first;
        while(e.next!=null && e.next.id<id) {
            e=e.next;
        }
        if(e.id==id) {
            e.val=val;
        }else {
            e.next=new IntVal(id,val,e.next);
            size++;
        }
    }

    public int get(int id) {
        IntVal e=first;
        while(e!=null) {
            if(e.id==id)
                return e.val;
            else if(e.id>id) // sorted
                break;
            e=e.next;
        }
        return -1;
    }

    public void toString(StringBuffer s) {
        IntVal e=first;
        while(e!=null) {
            TokenF tf=TokenF.intFeatures.get(e.id);
            if(e!=first)
                s.append(',');
            s.append(tf.name+"="+((IntFeature)tf).toString(e.val));
            e=e.next;
        }
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size);

        // Write out all elements in the proper order
        for(IntVal e=first; e!=null; e=e.next) {
            s.writeInt(e.id);
            s.writeInt(e.val);
        }
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();
        if(size==0)
            return;

        // Read in all elements in the proper order
        IntVal prev=null;
        for (int i=0; i<size; i++) {
            IntVal e=new IntVal(s.readInt(), s.readInt(), null);
            if(prev==null)
                first=e;
            else
                prev.next=e;
            prev=e;
        }
    }
}

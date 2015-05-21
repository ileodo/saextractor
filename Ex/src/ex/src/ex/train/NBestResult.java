// $Id: NBestResult.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

public class NBestResult {
    public int rc;
    public int length;
    public Object[] items;
    public double[] scores;
    public Object[] data; // user data

    public NBestResult(int capacity) {
        length=0;
        rc=PhraseBook.NOMATCH;
        if(capacity<1)
            capacity=1;
        setCapacity(capacity);
    }
    
    public int getCapacity() {
        return items.length;
    }
    
    public void setCapacity(int newCapacity) {
        if(items!=null) {
            Object[] old=items;
            items=new Object[newCapacity];
            System.arraycopy(old, 0, items, 0, old.length);
        }else {
            items=new Object[newCapacity];
        }
        if(scores!=null) {
            double[] old=scores;
            scores=new double[newCapacity];
            System.arraycopy(old, 0, items, 0, old.length);
        }else {
            scores=new double[newCapacity];
        }
        if(data!=null) {
            Object[] old=data;
            data=new Object[newCapacity];
            System.arraycopy(old, 0, data, 0, old.length);
        }else {
            data=new Object[newCapacity];
        }
    }
    
    public int getLength() {
        return length;
    }
    
    public int getRC() {
        return rc;
    }
    
    public void clear() {
        length=0;
        rc=PhraseBook.NOMATCH;
        for(int i=0;i<items.length;i++)
            items[i]=null;
    }

    /* adds if there is space for the item */
    public int add(Object nbestItem, double score, Object userData) {
        if(length<items.length) {
            items[length]=nbestItem;
            scores[length]=score;
            data[length]=userData;
            length++;
            return length;
        }
        // replace the first worse match 
        for(int i=0;i<items.length;i++) {
            if(score<scores[i]) {
                items[i]=nbestItem;
                scores[i]=score;
                data[i]=userData;
                return i;
            }
        }
        return -1;
    }

    public String toString() {
        if(length==0)
            return "[]";
        StringBuffer b=new StringBuffer(64);
        b.append("[");
        for(int i=0;i<length;i++) {
            b.append(items[i]!=null? items[i].toString(): "null");
            b.append(" ");
            b.append(scores[i]);
            b.append(" ");
            b.append(data[i]);
            if(i!=length-1)
                b.append("\n");
        }
        b.append("]");
        return b.toString();
    }
}

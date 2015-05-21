// $Id: IntTrieIterator.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util;

import java.util.*;
import gnu.trove.TIntArrayList;

/**
   Forward iterator for IntTrie
*/

public class IntTrieIterator implements Iterator<Object> {
    protected IntTrie trie;
    protected int idx;
    protected Object nextData;
    protected TIntArrayList childIdxs;

    protected IntTrieIterator(IntTrie trie, int idx) {
        this.trie=trie;
        this.idx=-1;
        childIdxs=new TIntArrayList(trie.depth);
        setNext();
    }
    public boolean hasNext() {
        return (nextData!=null)? true: false;
    }
    public Object next() {
        if(nextData==null)
            return new NoSuchElementException();
        Object rc=nextData;
        setNext();
        return rc;
    }
    public void remove() {
        throw(new UnsupportedOperationException("remove not implemented"));
    }

    protected void setNext() {
        if(idx==-1) {
            idx++;
            if(trie.data!=null) {
                nextData=trie.data;
                return;
            }
        }
        if(trie.next!=null && idx<trie.next.length) {
            trie=trie.next[idx];
            childIdxs.add(idx);
            idx=-1;
            setNext();
            return;
        }
        int depth=childIdxs.size();
        if(depth==0) {
            nextData=null;
            idx=-1;
            return;
        }
        trie=trie.parent;
        idx=childIdxs.remove(depth-1) + 1;
        setNext();
    }
}

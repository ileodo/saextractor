// $Id: IntTrie.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util;

import java.util.*;
import java.io.*;
import gnu.trove.TIntArrayList;
import uep.util.*;

/** An IntTrie is identical to Trie however it supports keys of type int[].
 */
public class IntTrie implements Serializable, Iterable<Object> {
    private static final long serialVersionUID = -5943177995313136962L;
    public static final int OK=0;
    public static final int ALREADY_EXISTS=-1;

    protected IntTrie[] next;
    public int label;   // label of the incoming arc
    public Object data; // if data!=null then this node is final (but may have children)
    public IntTrie parent; // TODO: only needed by iterator; see if this could be removed
    public int depth;

    public IntTrie(IntTrie par, int lab) {
        label=lab;
        parent=par;
        next=null;
        data=null;
        depth=(par==null)? 0: par.depth+1;
    }
    
    public void clear() {
        data=null;
        parent=null;
        if(next!=null) {
            for(IntTrie ch: next) {
                ch.clear();
            }
            next=null;
        }
    }

    public Iterator<Object> iterator() {
        return new IntTrieIterator(this,-1);
    }

    protected IntTrie newChild(int label) {
        return new IntTrie(this, label);
    }

    public int put(int[] s, Object o) {
        return put(s, o, 0, -1, true, null);
    }
    public int put(int[] s, Object o, int start, int len, boolean overwrite, ObjectRef lastTrie) {
        IntTrie node=findLongestPrefix(s, start, len);
        if(node.depth==s.length) {
            // whole string found
            if(!overwrite && node.data!=null)
                return ALREADY_EXISTS;
        }else {
            // insert the right part of the string
            if(len==-1)
                len=s.length-start;
            node=node.insertPath(s, start+node.depth, len-node.depth);
        }
        node.data=o;
        if(lastTrie!=null)
            lastTrie.data=node;
        return OK;
    }
    public int put(IntTrieItem[] s, Object o) { // versions for IntTrieItems
        return put(s, o, 0, -1, true, null);
    }
    public int put(IntTrieItem[] s, Object o, int start, int len, boolean overwrite, ObjectRef lastTrie) {
        IntTrie node=findLongestPrefix(s, start, len);
        if(node.depth==s.length) { 
            // whole string found
            if(!overwrite && node.data!=null)
                return ALREADY_EXISTS;
        }else { 
            // insert the right part of the string
            if(len==-1)
                len=s.length-start;
            node=node.insertPath(s, start+node.depth, len-node.depth);
        }
        node.data=o;
        if(lastTrie!=null)
            lastTrie.data=node;
        return OK;
    }

    public IntTrie insertPath(int[] s, int pos, int len) {
        if(len==0 || pos>=s.length)
            return this;
        int x=s[pos];
        int idx=0;
        if(next!=null) {
            IntTrie[] old=next;
            next=new IntTrie[old.length+1];
            int i=0, j=0;
            for( ; i<old.length; i++,j++) {
                if(i==j && x<=old[i].label) {
                    idx=j;
                    next[j++]=newChild(x);
                }
                next[j]=old[i];
            }
            if(i==j) {
                idx=next.length-1;
                next[idx]=newChild(x);
            }
        }else {
            next=new IntTrie[1];
            next[0]=newChild(x);
        }
        return next[idx].insertPath(s,pos+1,len-1);
    }
    public IntTrie insertPath(IntTrieItem[] s, int pos, int len) {
        if(len==0 || pos>=s.length)
            return this;
        int x=s[pos].getTrieKey();
        int idx=0;
        if(next!=null) {
            IntTrie[] old=next;
            next=new IntTrie[old.length+1];
            int i=0, j=0;
            for( ; i<old.length; i++,j++) {
                if(i==j && x<=old[i].label) {
                    idx=j;
                    next[j++]=newChild(x);
                }
                next[j]=old[i];
            }
            if(i==j) {
                idx=next.length-1;
                next[idx]=newChild(x);
            }
        }else {
            next=new IntTrie[1];
            next[0]=newChild(x);
        }
        return next[idx].insertPath(s,pos+1,len-1);
    }

    public Object get(int[] s) {
        IntTrie node=findLongestPrefix(s, 0, -1);
        if(node.depth==s.length) // whole string found
            return node.data;
        return null;
    }
    public Object get(IntTrieItem[] s) {
        IntTrie node=findLongestPrefix(s, 0, -1);
        if(node.depth==s.length) // whole string found
            return node.data;
        return null;
    }

    public IntTrie findLongestPrefix(int[] s, int startPos, int len) {
        // we found the full string, or there is nowhere to go, 
        // in both cases this is the end of the prefix:
        if(startPos>=s.length || next==null || len==0)
            return this;
        int i=binarySearch(s[startPos]);
        if(i<0)
            return this;
        return next[i].findLongestPrefix(s, startPos+1, len-1);
    }
    public IntTrie findLongestPrefix(IntTrieItem[] s, int startPos, int len) {
        // we found the full string, or there is nowhere to go, 
        // in both cases this is the end of the prefix:
        if(startPos>=s.length || next==null || len==0)
            return this;
        int i=binarySearch(s[startPos].getTrieKey());
        if(i<0)
            return this;
        return next[i].findLongestPrefix(s, startPos+1, len-1);
    }

    public IntTrie transition(int lab) {
        int i=binarySearch(lab);
        if(i<0)
            return null;
        return next[i];
    }

    protected int binarySearch(int lab) {
        if(next==null)
            return -1;
        int hi=next.length-1;
        int lo=0;
        int curIdx=hi/2;
        int curLab=next[curIdx].label;
        while(curLab!=lab) {
            if(hi==lo) {
                return -1;
            }
            if(lab>curLab) {
                lo=curIdx+1;
            }else {
                hi=curIdx-1;
            }
            if(hi<lo) {
                return -1;
            }
            curIdx=(lo+hi)/2;
            curLab=next[curIdx].label;
        }
        return curIdx;
    }

    public String toString() {
        StringBuffer buff=new StringBuffer(4096);
        TIntArrayList path=new TIntArrayList(64);
        toString(buff, path, 0);
        return buff.toString();
    }

    int toString(StringBuffer buff, TIntArrayList path, int processedCnt) {
        if(data!=null) {
            processedCnt++;
            buff.append(processedCnt);
            buff.append(".");
            for(int i=1;i<depth;i++) { // path without my label
                buff.append(" ");
                buff.append(path.getQuick(i));
            }
            buff.append(" "); // my label
            buff.append(label);
            buff.append(" --> ");
            buff.append(data.toString());
            buff.append("\n");
        }
        int cnt=(next==null)? 0:next.length;
        if(cnt==0)
            return processedCnt;
        path.add(label);
        for(int i=0; i<cnt; i++) {
            processedCnt=((IntTrie)next[i]).toString(buff, path, processedCnt);
        }
        path.remove(path.size()-1);
        return processedCnt;
    }

    public static void main(String[] args) {
        IntTrie t=new IntTrie(null, 0);
        int[] a={1,2,5,1}; t.put(a,"a");
        int[] b={1,2,5,1}; t.put(b,"b");
        int[] c={1,2,3,3}; t.put(c,"c");
        int[] d={1,2,6,3}; t.put(d,"d");
        int[] e={0,2,6,3}; t.put(e,"e");
        int[] x={0}; t.put(x,"x");

        System.out.println("toString:");
        System.out.println(t.toString());

        System.out.println((String)t.get(a));
        System.out.println((String)t.get(b));
        System.out.println((String)t.get(c));
        System.out.println((String)t.get(d));
        System.out.println((String)t.get(e));
        System.out.println((String)t.get(x));

        System.out.println("Iterating:");
        Iterator<Object> it=t.iterator();
        int i=0;
        while(it.hasNext()) {
            System.out.println((++i)+". "+it.next());
        }
    }
}

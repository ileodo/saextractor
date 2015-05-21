// $Id: Trie.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util;

import java.util.*;
import java.io.*;
import gnu.trove.TIntArrayList;
import uep.util.Logger;

/** A Trie that can accommodate objects of type String.
 */
public class Trie implements Comparable<Trie>, Serializable {
    private static final long serialVersionUID = -2970345575691011288L;
    public static final int OK=0;
    public static final int ALREADY_EXISTS=-1;

    public static int GROW_STEP=8;

    //public static Logger log;
    //public boolean logOn;

    Trie[] next;
    char label;  // label of the incoming arc
    Object data; // if data!=null then this node is final (but may have children)
    // Trie parent; // unused
    char depth;
    // int count; // unused
    char len; // populated part of next[]

    public Trie(Trie par, char lab) {
        this(par, lab, true);
    }

    public Trie(Trie par, char lab, boolean debug) {
        //logOn=debug;
        //if(logOn) {
        //    Logger.init("trie.log",Logger.TRC,-1,null);
        //    log=Logger.getLogger("Trie");
        //}
        label=lab;
        // parent=par;
        next=null;
        data=null;
        depth=(par==null)? 0: (char)(par.depth+1);
        len=0;
    }

    public int compareTo(Trie trie) {
        if(label<trie.label)
            return -1;
        return 1;
    }

    // put
    public int put(String s, Object o) {
        return put(s, o, false);
    }
    public int put(String s) {
        return put(s, s, false);
    }
    public int put(String s, Object o, boolean overwrite) {
        //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"["+depth+"].put("+s+","+o.toString()+")");
        Trie node=findLongestPrefix(s, 0, false);
        if(node.depth==s.length()) { // whole string found
            if(!overwrite && node.data!=null) // store if overwrite allowed or if the path is not final (data==null) 
                return ALREADY_EXISTS;
            //if(node.data==null)
            //    count++;
            node.data=o;
            return OK;
        }
        // insert the right part of the string
        node=node.insertPath(s, node.depth);
        node.data=o;
        // count++;
        return OK;
    }

    public Trie insertPath(String s, int pos) {
        //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"["+depth+"].insertPath("+s+","+pos+")");
        if(pos>=s.length())
            return this;
        char x= s.charAt(pos);
        int idx=0;
        if(next!=null) {
            if(len==next.length) {               
                Trie[] old=next;
                next=new Trie[old.length+GROW_STEP];
                int i=0, j=0;
                for( ; i<old.length; i++,j++) {
                    if(i==j && x<=old[i].label) {
                        idx=j;
                        next[j++]=new Trie(this, x);
                    }
                    next[j]=old[i];
                }
                if(i==j) {
                    idx=old.length;
                    next[idx]=new Trie(this, x);
                }
            }else {
                int i;
                for(i=len-1;i>=0;i--) {
                    if(x<next[i].label)
                        next[i+1]=next[i];
                    else
                        break;
                }
                idx=i+1;
                next[idx]=new Trie(this, x);
            }
        }else {
            next=new Trie[GROW_STEP];
            next[0]=new Trie(this, x);
        }
        len++;
        return next[idx].insertPath(s,pos+1);
    }

    // get
    public Object get(String s) {
        return get(s, false);
    }
    public Object get(String s, boolean ignCase) {
        //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"["+depth+"].get("+s+")");
        Trie node=findLongestPrefix(s, 0, ignCase);
        if(node.depth==s.length()) // whole string found
            return node.data;
        return null;
    }

    public Trie findLongestPrefix(String s, int pos, boolean ignCase) {
        //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"["+depth+"].flp("+s+","+pos+")");
        // we found the full string, or there is nowhere to go, 
        // in both cases this is the end of the prefix:
        if(pos>=s.length() || next==null)
            return this;
        char c=s.charAt(pos);
        int i=binarySearch(c);
        boolean flag=false;
        if(ignCase && i<0) {
            if(Character.isLowerCase(c)) {
                i=binarySearch(Character.toUpperCase(c));
                flag=true;
            }else if(Character.isUpperCase(c)) {
                i=binarySearch(Character.toLowerCase(c));
                flag=true;
            }
        }
        if(i<0) {
            return this;
        }
        Trie deepest=next[i].findLongestPrefix(s, pos+1, ignCase);
        if(ignCase && !flag && deepest.depth!=s.length()) {
            i=-1;
            if(Character.isLowerCase(c)) {
                i=binarySearch(Character.toUpperCase(c));
            }else if(Character.isUpperCase(c)) {
                i=binarySearch(Character.toLowerCase(c));
            }
            if(i!=-1) { 
                Trie deepest2=next[i].findLongestPrefix(s, pos+1, ignCase);
                if(deepest2.depth>deepest.depth) {
                    deepest=deepest2;
                }
            }
        }
        return deepest;
    }

    public void sort() {
        if(next==null)
            return;
        Arrays.sort(next);
        for(int i=0;i<len;i++)
            next[i].sort();
    }

//    private int seqSearch(char lab) {
//        int hi=len-1;
//        int lo=0;
//        for(int i=0;i<=hi;i++)
//            if(next[i].label==lab)
//                return i;
//        return -1;
//    }

    private int binarySearch(char lab) {
//        String s=""+next[0].label;
//        for(int i=1;i<len;i++)
//            s+=","+next[i].label;
        //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"["+s+"].SEARCH("+lab+")");

        //if(ignCase) lab=Character.toLowerCase(lab);
        int hi=len-1;
        int lo=0;
        int curIdx=hi/2;
        //char curLab=ignCase? Character.toLowerCase(next[curIdx].label): next[curIdx].label;
        char curLab=next[curIdx].label;
        while(curLab!=lab) {
            if(hi==lo) {
                //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"-1: hi=lo="+lo);
                return -1;
            }
            if(lab>curLab) {
                lo=curIdx+1;
            }else {
                hi=curIdx-1;
            }
            if(hi<lo) {
                //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"-1: hi="+hi+"<lo="+lo);
                return -1;
            }
            curIdx=(lo+hi)/2;
            //curLab=ignCase? Character.toLowerCase(next[curIdx].label): next[curIdx].label;
            curLab=next[curIdx].label;
        }
        //if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,curIdx+": OK");
        return curIdx;
    }

    public String toString() {
        StringBuffer buff=new StringBuffer(4096);
        TIntArrayList path=new TIntArrayList(64);
        toString(buff, path);
        return buff.toString();
    }

    void toString(StringBuffer buff, TIntArrayList path) {
        if(data!=null) {
            for(int i=0;i<depth;i++) {
                buff.append((char) path.getQuick(i));
            }
            buff.append(label);
            buff.append(" ");
            buff.append(data.toString());
            buff.append("\n");
        }

        if(false) {
            String s="";
            int x=depth;
            while(x-- > 0)
                s+=' ';
            Logger.LOG(Logger.WRN,s+label);
        }

        int cnt=(next==null)? 0: len;
        if(cnt==0)
            return;
        path.add(label);

        for(int i=0; i<cnt; i++) {
            ((Trie)next[i]).toString(buff, path);
        }
        path.remove(path.size()-1);
    }

    public int fillArray(Object[] arr, int startIdx) {
        if(data!=null)
            arr[startIdx++]=data;
        int cnt=(next==null)? 0: len;
        for(int i=0; i<cnt; i++)
            startIdx=((Trie)next[i]).fillArray(arr, startIdx);
        return startIdx;
    }

    public int size() {
        int cnt=0;
        if(data!=null)
            cnt++;
        if((next!=null))
            for(int i=0; i<cnt; i++)
                cnt+=((Trie)next[i]).size();
        return cnt;
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
        // stream.writeInt(len);
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        // int size = stream.readInt();
        // _data = new int[size];
        // while (size-- > 0) {
        //    int val = stream.readInt();
        //    add(val);
        //}
    }

    public static void main(String[] args) {
        Trie t=new Trie(null, (char)0, true);
        t.put("alfa","1");
        t.put("auto","2");
        t.put("automat","3");
        t.put("aula","7");
        t.put("audio","D");
        t.put("beta","4");
        t.put("betas","5");

        System.out.print((String)t.get("automat"));
        System.out.print((String)t.get("beta"));
        System.out.print((String)t.get("alfa"));
        System.out.print((String)t.get("betas"));
        System.out.print((String)t.get("auto"));
        System.out.print((String)t.get("aula"));
        System.out.print((String)t.get("audio"));

        System.out.print("\n\n");
        System.out.print(t.toString());

        String file="./trie_test.ser";
        try {
            ObjectOutputStream oos=new ObjectOutputStream(new FileOutputStream(new File(file)));
            oos.writeObject(t);

            System.out.print("Deserializing: \n");
            ObjectInputStream ois=new ObjectInputStream(new FileInputStream(new File(file)));
            Trie meAgain=(Trie) ois.readObject();
            System.out.print(meAgain.toString());
        }catch(IOException ex) {
            System.out.print("ERR "+ex.toString());
        }catch(ClassNotFoundException ex) {
            System.out.print("ERR "+ex.toString());
        }
    }
}

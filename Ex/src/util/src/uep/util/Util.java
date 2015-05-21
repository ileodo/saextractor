// $Id: Util.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class Util {
    public static String defaultEncoding="utf-8";
    
    public static void reverse(double[] a) {
        int cnt=a.length/2;
        for(int i=0;i<cnt;i++) {
            double tmp=a[i];
            int idx=a.length-i-1;
            a[i]=a[idx];
            a[idx]=tmp;
        }
    }

    public static void reverse(Object[] a) {
        int cnt=a.length/2;
        for(int i=0;i<cnt;i++) {
            Object tmp=a[i];
            int idx=a.length-i-1;
            a[i]=a[idx];
            a[idx]=tmp;
        }
    }
    
    public static String reverse(String source) {
        int len=source.length();
        StringBuffer b=new StringBuffer(len);
        for(int i=(len-1); i>=0; i--) {
            b.append(source.charAt(i));
        }
        return b.toString();
    }

    public static int bsearch(double[] arr, int low, int high, double key) {
        int lo=low;
        int hi=high;
        while(lo<=hi) {
            int cur=(lo+hi)/2;
            double val=arr[cur];
            if(key>val)
                hi=cur-1;
            else if(key<val)
                lo=cur+1;
            else
                return cur;
        }
        return -lo-1;
    }

    public static void rep(int cnt, char c, StringBuffer buff) {
        for(int i=0;i<cnt;i++)
            buff.append(c);
    }

    public static String escapeJSString(String s) {
        int len=s.length();
        StringBuffer b=new StringBuffer(len+16);
        char c;
        for(int i=0;i<len;i++) {
            c=s.charAt(i);
            switch(c) {
            case '\n':
                b.append("\\n");
                break;
            case '\r':
                b.append("\\r");
                break;
            case '\t':
                b.append("\\t");
                break;
            case '"':
                b.append("\\\"");
                break;
            default:
                b.append(c);
            }
        }
        return b.toString();
    }
    
    public static boolean equalsApprox(double a, double b) {
        return Math.abs(a-b)<1e-10;
    }
    
    public static boolean equalsApprox(double a, double b, double maxDiff) {
        return Math.abs(a-b)<maxDiff;
    }
    
    public static void main(String[] args) {
        double[] xx=new double[] {15,12,10,7,3};
        for(int jj=2;jj<=16;jj++) {
            int idx=bsearch(xx,0,xx.length-1,jj);
            System.out.println(jj+"->"+((idx<0)? ("ins "+(-idx-1)): idx));
        }
    }
    
    public static void logMemStats(Logger log, String label) {
        if((log!=null && log.IFLG(Logger.INF)) || (log==null && Logger.IFLOG(Logger.INF))) {
            long free=Runtime.getRuntime().freeMemory();
            long total=Runtime.getRuntime().totalMemory();
            long max=Runtime.getRuntime().maxMemory();
            String msg=label+": free="+free+", total="+total+", max="+max;
            if(log!=null)
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,msg);
            else
                if(Logger.IFLOG(Logger.INF)) Logger.LOG(Logger.INF, msg);
        }
    }

    public static String loadFileFromJarOrDisc(String file, String enc, Class classFromJar) throws IOException {
        String content=null;
        for(int retry=0; retry<2; retry++) {
            try {
                content=(retry==0)? 
                        readFromJar(file,enc,classFromJar): 
                        readFile(file,enc);
                        break;
            }catch(IOException ex) {
                if(retry==0) {
                    Logger.LOG(Logger.WRN,"File "+file+" not found in jar: "+ex);
                }else {
                    throw new IOException("File "+file+" not found in filesystem: "+ex);
                }
            }
        }
        return content;
    }
    
    public static String readFile(String file, String enc) throws IOException {
        if(enc==null || enc.length()==0)
            enc=defaultEncoding;
        File f=new File(file);
        int sz=(int)f.length();
        StringBuffer content=new StringBuffer(sz);
        int bs=512;
        char[] buff=new char[bs];
        int read=0;
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), enc), bs);
        while((read=br.read(buff,0,bs))>=0) {
            content.append(buff,0,read);
        }
        br.close();
        return content.toString();
    }

    public static String readFromJar(String file, String enc, Class classFromJar) throws IOException {
        if(enc==null || enc.length()==0)
            enc=defaultEncoding;
        StringBuffer buff=new StringBuffer(2048);
        InputStream is=classFromJar.getResourceAsStream(file);
        if(is==null) {
            throw new IOException("File "+file+" was not found in jar!");
        }
        CharBuffer cb=CharBuffer.allocate(1024);
        BufferedReader br=new BufferedReader(new InputStreamReader(is, enc), 512);
        while(br.read(cb)!=-1) {
            cb.flip();
            buff.append(cb);
        }
        return buff.toString();
    }
    
    public static void writeFile(String file, String data, String enc) throws IOException {
        if(enc==null || enc.length()==0)
            enc=defaultEncoding;
        File f=new File(file);
        BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), enc));
        br.write(data);
        br.close();
    }
    
    /** @return Read-only view of a concatenation of source collections. */
    public static Collection<Object> concatView(Collection<Object> c1, Collection<Object> c2) {
        if(c1==null)
            return c2;
        else if(c2==null)
            return c1;
        else {        
            ConcatCollection<Object> cc=new ConcatCollection<Object>(2);
            cc.addAll(c1);
            cc.addAll(c2);
            return cc;
        }
    }
}

/** Read-only view of a concatenation of source collections. */
class ConcatCollection<E> implements Collection<E> {
    ArrayList<Collection<? extends E>> cols;
    public ConcatCollection(int iniSize) {
        cols=new ArrayList<Collection<? extends E>>(iniSize);
    }
    public void append(List<E> col) {
        if(col==null)
            throw new NullPointerException();
        cols.add(col);
    }
    public boolean addAll(Collection<? extends E> c) {
        cols.add(c);
        return true;
    }
    public void clear() {
        cols.clear();
    }
    public boolean isEmpty() {
        return size()==0;
    }
    public int size() {
        int len=0;
        for(Collection<? extends E> c: cols) {
            len+=c.size();
        }
        return len;
    }
    public Iterator<E> iterator() {
        return new ConcIt();
    }
    public boolean contains(Object o) {
        for(Collection<? extends E> c: cols) {
            if(c.contains(o))
                return true;
        }
        return false;
    }
    public boolean containsAll(Collection<?> c) {
        for(Object o: c) {
            if(!contains(o)) {
                return false;
            }
        }
        return true;
    }
    
    class ConcIt implements Iterator<E> {
        Iterator<Collection<? extends E>> cit;
        Iterator<? extends E> it;
        E next;
        boolean haveNext;
        
        public ConcIt() {
            cit=cols.iterator();
            if(cit.hasNext()) {
                Collection<? extends E> c=cit.next();
                it=c.iterator();
            }else {
                it=null;
            }
            prepareNext();
        }
        
        private void prepareNext() {
            if(it==null) {
                return;
            }
            if(it.hasNext()) {
                next=it.next();
                haveNext=true;
                return;
            }
            // find first collection that has some next element
            while(!it.hasNext()) {
                if(cit.hasNext()) {
                    Collection<? extends E> c=cit.next();
                    it=c.iterator();
                }else {
                    it=null;
                    next=null;
                    haveNext=false;
                    return;
                }
            }
            next=it.next();
            haveNext=true;
        }
        
        public boolean hasNext() {
            return haveNext;
        }

        public E next() {
            if(!haveNext) {
                throw new NoSuchElementException();
            }
            E tmp=next;
            prepareNext();
            return tmp;
        }

        public void remove() {
            throw new UnsupportedOperationException();            
        }
    }
    
    /** The rest is unsupported. */
    public boolean add(E o) {
        throw new UnsupportedOperationException();
    }
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }
}
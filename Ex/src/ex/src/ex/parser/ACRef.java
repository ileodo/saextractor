// $Id: ACRef.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import ex.model.*;
import ex.ac.*;

public class ACRef {
    public AC ac;
    public ACRef[] children;
    public ACRef(AC attCand, ACRef[] chldrn) {
        ac=attCand;
        children=chldrn;
    }

    public int setIds(int[] ids, int pos) {
        ids[pos]=ac.hashCode();
        if(children==null)
            return 1;
        int cnt=1;
        for(int i=0;i<children.length;i++)
            cnt+=children[i].setIds(ids, pos+cnt);
        return cnt;
    }

    /**
       Fills into @list all ACs corresponding to the specified AttributeDef, returns the count of these ACs.
     */
    public int getACs(AttributeDef ad, List<AC> lst) {
        if(ac==null)
            return 0;
        int cnt=0;
        if(ad==ac.getAttribute()) {
            lst.add(ac);
            cnt++;
        }
        if(children==null)
            return cnt;
        for(int i=0;i<children.length;i++) {
            cnt+=getACs(ad,lst);
        }
        return cnt;
    }

    public void addChild(ACRef acr) {
        if(children==null) {
            children=new ACRef[1];
            children[0]=acr;
            return;
        }
        ACRef[] tmp=children;
        children=new ACRef[tmp.length+1];
        int j=0;
        for(int i=0;i<children.length;i++) {
            if(j==i && (j==tmp.length || acr.ac.hashCode() < tmp[j].ac.hashCode())) {
                children[i]=acr;
            }else {
                children[i]=tmp[j];
                j++;
            }
        }
    }

    public void toString(StringBuffer buff, int depth) {
        int i;
        for(i=0;i<depth;i++)
            buff.append(' ');
        ac.toString(buff);
        buff.append('\n');
        if(children==null)
            return;
        for(i=0;i<children.length;i++) {
            children[i].toString(buff, depth+1);
        }
    }

    // debugging only
    public String toString(int depth) {
        int i;
        StringBuffer buff=new StringBuffer(128);
        for(i=0;i<depth;i++)
            buff.append(' ');
        buff.append(ac.toString());
        buff.append('\n');
        if(children==null)
            return buff.toString();
        for(i=0;i<children.length;i++) {
            buff.append(children[i].toString(depth+1));
        }
        return buff.toString();
    }

    public void toScript(StringBuffer buff) {
        ac.toScript(buff);
        if(children==null)
            return;
        for(int i=0;i<children.length;i++)
            children[i].toScript(buff);
    }
}

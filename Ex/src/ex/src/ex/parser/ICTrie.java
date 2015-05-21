// $Id: ICTrie.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

import uep.util.Logger;
import ex.util.*;
import ex.ac.*;

/* children of this will still be IntTries */
public class ICTrie extends IntTrie {
    
    private static final long serialVersionUID = 7728565504953281328L;
    protected Logger log;

    public ICTrie() {
        super(null,0);
        log=Logger.getLogger("ICTrie");
    }

    public IC get(IC ic, AC ac) {
        IC foundIC=get2(ic,ac);
        if(foundIC!=null) {
            if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"Found "+ic.toString());
        }else {
            if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"IC not found for "+ic.toString()+" + "+ac.toString());
        }
        return foundIC;
    }

    public IC get2(IC ic, AC ac) {
        int j;
        int c=ac.hashCode();
        IntTrie node=this;

        // find prefix of ids
        for(j=0;j<ic.ids.length;j++) {
            if(c<ic.ids[j]) 
                break;
        }
        if(j>0) {
            node=findLongestPrefix(ic.ids, 0, j);
            if(node==null)
                return null;
        }

        // find inserted AC's id
        node=node.transition(c);
        if(node==null)
            return null;

        // find suffix of ids
        if(j!=ic.ids.length)
            node=node.findLongestPrefix(ic.ids, j, ic.ids.length-j);

        return (node!=null)? (IC)node.data: null;
    }

    public int put(IC ic, boolean overwrite) {
        IntTrie node=findLongestPrefix(ic.ids, 0, -1);
        if(node.depth==ic.ids.length) { // whole string found
            if(!overwrite && node.data!=null)
                return ALREADY_EXISTS;
            node.data=ic;
            return OK;
        }
        // insert the right part of the string
        node=node.insertPath(ic.ids, node.depth, -1);
        node.data=ic;
        return OK;
    }
}

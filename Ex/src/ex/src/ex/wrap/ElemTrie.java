// $Id: ElemTrie.java 1641 2008-09-12 21:53:08Z labsky $
package ex.wrap;

import ex.util.*;

/** An ElemTrie adds contained leaf counts and AC counts.
 */
public class ElemTrie extends IntTrie {
    private static final long serialVersionUID = -7499135091993310136L;
    public int leafCount;
    public int[] acCounts;

    public ElemTrie(IntTrie par, int lab) {
        super(par,lab);
        leafCount=0;
        acCounts=null; // new int[FM.getFMInstance().model.getTotalAttCount()];
    }

    protected ElemTrie newChild(int label) {
        return new ElemTrie(this, label);
    }

    public int put(int[] s, Object o, boolean overwrite) {
        IntTrie node=findLongestPrefix(s, 0, -1);
        if(node.depth==s.length) { // whole string found
            if(!overwrite && node.data!=null)
                return ALREADY_EXISTS;
            node.data=o;
        }else {
            // insert the right part of the string
            node=node.insertPath(s, node.depth, -1);
            node.data=o;
        }
        while(node!=null) {
            ((ElemTrie) node).leafCount++;
            node=node.parent;
        }
        return OK;
    }
}

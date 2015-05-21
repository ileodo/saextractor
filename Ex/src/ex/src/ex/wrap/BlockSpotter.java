// $Id: BlockSpotter.java 1641 2008-09-12 21:53:08Z labsky $
package ex.wrap;

import ex.util.IntTrie;
import ex.reader.*;

public class BlockSpotter {
    /* finds: 
       - blocks directly containing textual/img content, 
       - sequences of such blocks, 
       - and sequences of groups of such blocks 
     */

    public int findBlocks(Document doc) {
        DomPath path=new DomPath(doc.maxDomDepth);
        IntTrie paths=new IntTrie(null,0);
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];
            int depth=path.setPathFrom(ta,-1);
            paths.put(path.nodeIds,null);
        }
        return 0;
    }
}

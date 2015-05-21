// $Id: DomPath.java 1641 2008-09-12 21:53:08Z labsky $
package ex.wrap;

import ex.reader.*;

public class DomPath {
    public TagAnnot[] path;
    public int[] nodeIds;
    public int length;
    public Annot element;

    public DomPath(int cap) {
        path=new TagAnnot[cap];
        nodeIds=new int[cap];
        length=0;
        element=null;
    }

    public int setPathFrom(Annot annot, int maxLen) {
        length=0;
        element=annot;
        Annot anc=annot.parent;
        while(anc!=null) {
            if(length>maxLen)
                break;
            nodeIds[length]=((TagAnnot)anc).getSignature(); // tagId
            path[length]=(TagAnnot)anc;
            anc=anc.parent;
            length++;
        }
        // reverse nodeIds so that we can add path counts from root
        int half=length/2;
        for(int i=0;i<half;i++) {
            int x=nodeIds[i];
            int idx=length-1-i;
            nodeIds[i]=nodeIds[idx];
            nodeIds[idx]=x;
        }
        return length;
    }
}

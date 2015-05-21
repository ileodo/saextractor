// $Id: Trellis.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

import java.util.*;
import ex.reader.Document;
import ex.reader.TokenAnnot;

/* Triangle Trellis
   - sparse trellis allowing for storage of records which contain a list of items
   - records are navigable up the tree - each record is linked to the next record up left and up right
   - navigation down can be added if needed; for now the contained items are navigable down
 */
public class Trellis  {
    public Document doc;
    public Trellis(Document doc) {
        this.doc=doc;
    }
    public void clear() {
        if(doc!=null) {
            for(TokenAnnot ta: doc.tokens) {
                for(TrellisRecord rec: (TrellisRecord[]) ta.userData) {
                    rec.clear();
                    ta.userData=null;
                }
            }
            doc=null;
        }
    }
    public TrellisRecord get(int left, int right) {
        if(left>right)
            throw new IllegalArgumentException("Trellis get: left="+left+" > right="+right);
        // userData[0] points to the first TrellisRecord on the left spoke reaching from TokenAnnot,
        // userData[1] points to the first TrellisRecord on the right spoke

        TrellisRecord[] lr=(TrellisRecord[]) doc.tokens[left].userData; // we choose to go from left..
        TrellisRecord point;
        if(lr==null || (point=lr[1])==null) // ..to right
            return null;
        while(point!=null && point.rightIdx < right) {
            point=point.upRight;
        }
        if(point==null || point.rightIdx > right)
            return null;
        return point;
    }

    public TrellisRecord get2(int left, int right) {
        if(left>right)
            throw new IllegalArgumentException("Trellis get2: left="+left+" > right="+right);
        // userData[0] points to the first TrellisRecord on the left spoke reaching from TokenAnnot,
        // userData[1] points to the first TrellisRecord on the right spoke

        TrellisRecord[] lr=(TrellisRecord[]) doc.tokens[right].userData; // we choose to go from right..
        TrellisRecord point;
        if(lr==null || (point=lr[0])==null) // ..to left
            return null;
        while(point!=null && point.leftIdx > left) {
            point=point.upLeft;
        }
        if(point==null || point.leftIdx < left)
            return null;
        return point;
    }

    public boolean add(ICBase rec) {
        return add(rec.getStartIdx(), rec.getEndIdx(), rec);
    }

    public boolean add(int left, int right, ICBase rec) {
        if(left>right)
            throw new IllegalArgumentException("Trellis add: left="+left+" > right="+right);
        TrellisRecord point=get(left, right);
        if(point!=null) {
            return point.add(rec);
        }
        point=new TrellisRecord(left, right);
        point.add(rec);
        if(!put(left, right, point))
            Parser.log.LGERR("Trellis.add failed: left="+left+", right="+right);
        return true;
    }

    public boolean put(int left, int right, TrellisRecord point) {
        if(left>right)
            throw new IllegalArgumentException("Trellis add: left="+left+" > right="+right);
        // put the new point in spokes, ev. replacing old point, setting upRight and upLeft of point and neighbours

        // first put the new record on right spoke of the start token
        TrellisRecord[] lr=(TrellisRecord[]) doc.tokens[left].userData;
        if(lr==null) {
            doc.tokens[left].userData = lr = new TrellisRecord[2];
            lr[1] = point;
        }else if(lr[1]==null) {
            lr[1] = point;
        }else {
            // put it into the correct position in right spoke
            TrellisRecord pre=null;
            TrellisRecord aft=lr[1];
            while(aft!=null && aft.rightIdx<point.rightIdx) {
                pre=aft;
                aft=aft.upRight;
            }
            if(aft==null) { // end
                pre.upRight=point;
                //point.downLeft=pre;
                point.upRight=null;
            }else if(pre==null) { // beginning
                point.upRight=aft;
                //point.downLeft=null;
                //aft.downLeft=point;
                lr[1]=point;
            }else if(aft.rightIdx==point.rightIdx) { // replace
                if(pre!=null)
                    pre.upRight=point;
                // point.downLeft=pre;
                point.upRight=aft.upRight;
                //if(aft.upRight!=null)
                //    aft.upRight.downLeft=point;
                return false;
            }else { // middle
                pre.upRight=point;
                //point.downLeft=pre;
                point.upRight=aft;
                //aft.downLeft=pre;
            }
        }

        // second put the new receord on left spoke of the end token
        lr=(TrellisRecord[]) doc.tokens[right].userData;
        if(lr==null) {
            doc.tokens[right].userData = lr = new TrellisRecord[2];
            lr[0] = point;
        }else if(lr[0]==null) {
            lr[0] = point;
        }else {
            // put it into the correct position in left spoke
            TrellisRecord pre=null;
            TrellisRecord aft=lr[0];
            while(aft!=null && aft.leftIdx>point.leftIdx) {
                pre=aft;
                aft=aft.upLeft;
            }
            if(aft==null) { // end
                pre.upLeft=point;
                point.upLeft=null;
            }else if(pre==null) { // beginning
                point.upLeft=aft;
                lr[0]=point;
            }else if(aft.leftIdx==point.leftIdx) { // replace
                if(pre!=null)
                    pre.upLeft=point;
                point.upLeft=aft.upLeft;
                return false;
            }else { // middle
                pre.upLeft=point;
                point.upLeft=aft;
            }
        }

        return true;
    }

    public TrellisRecord firstRight(int i) {
        TrellisRecord[] lr=(TrellisRecord[]) doc.tokens[i].userData;
        return (lr!=null)? lr[1]: null;
    }

    /* gets first trellis record on the spoke reaching up left from this   */
    public TrellisRecord firstLeft(int i) {
        TrellisRecord[] lr=(TrellisRecord[]) doc.tokens[i].userData;
        return (lr!=null)? lr[0]: null;
    }

    public int getItems(int leftIdx, int rightIdx, Collection<ICBase> items) { // , ICSelector sel
        int cnt=0;
        if(leftIdx<0||leftIdx>=doc.tokens.length)
            leftIdx=0;
        if(rightIdx<0||rightIdx>=doc.tokens.length)
            rightIdx=doc.tokens.length-1;

        for(int i=leftIdx; i<=rightIdx; i++) {
            TokenAnnot ta=doc.tokens[i];
            TrellisRecord[] fork=(TrellisRecord[]) ta.userData;
            if(fork==null)
                continue;
            // add addable ICs that start here (folowing the right spoke from fork) 
            TrellisRecord tr=fork[1];
            while(tr!=null) {
                for(ICBase ic: tr) {
                    if(!ic.canBeAdded()) { // || !sel.matches(ic)
                        continue;
                    }
                    items.add(ic);
                    cnt++;
                }
                tr=tr.upRight;
            }
        }
        return cnt;
    }
}

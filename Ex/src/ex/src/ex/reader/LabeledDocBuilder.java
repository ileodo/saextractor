// $Id: LabeledDocBuilder.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import java.util.*;
import uep.util.Logger;

class DocumentEdit implements Comparable<DocumentEdit> {
    public int idx; // offset (in orig document) of the character at which to perform the edit operation
    public int delCnt; // number of characters to be deleted, starting with char at idx (>=0)
    public String insStr; // string to be inserted instead of the deleted chars
    public DocumentEdit(int i, int dc, String s) {
        idx=i; delCnt=dc; insStr=s;
    }
    public int compareTo(DocumentEdit o) {
        return idx-o.idx;
    }
    public String toString() {
        return "at "+idx+((delCnt>0)? (" delete "+delCnt):"")+((insStr!=null)? (" insert "+insStr):"");
    }
}

public class LabeledDocBuilder {
    protected char[] chars;
    protected StringBuffer buff; // output buffer
    protected gnu.trove.TIntArrayList deltaIdxs; // stepwise mapping of old indices to new indices:
    protected gnu.trove.TIntArrayList deltaVals; // indices of steps and cumulative delta values
    protected int pos; // pos in chars (how many original chars have been already copied)

    public LabeledDocBuilder(String orig) {
        buff=new StringBuffer(orig.length()+2048);
        deltaIdxs=new gnu.trove.TIntArrayList(64);
        deltaVals=new gnu.trove.TIntArrayList(64);
        setData(orig);
    }

    public void setData(String orig) {
        chars=orig.toCharArray();
        pos=0;
        buff.setLength(0);
        buff.ensureCapacity(chars.length+2048);
        deltaIdxs.clear();
        deltaVals.clear();
    }

    public void insert(String s) {
        int len=deltaIdxs.size();
        if(len>0 && deltaIdxs.get(len-1)==pos) { // successive inserts called
            deltaVals.set(len-1, deltaVals.get(len-1)+s.length());
        }else {
            deltaIdxs.add(pos);
            deltaVals.add(s.length());
        }
        buff.append(s);
    }

    public void proceedTo(int idx) {
        if(idx>=pos) {
            buff.append(chars,pos,idx-pos);
            pos=idx;            
        }else {
            Logger.LOG(Logger.ERR,"Not labeling out-of-order tokens: pos="+pos+" tok="+idx);
        }
    }

    public String toString() {
        return buff.toString();
    }

    protected int oldIdx2newIdx(int oldIdx) {
        int lastSmallerStepIdx=-1, len=deltaIdxs.size();
        // e.g., if deltaIdx[1]=5 & deltaVal[1]=10 then starting at char 5, everything is shifted 10 chars to the right
        for(int i=0;i<len;i++) {
            int idx=deltaIdxs.get(i);
            if(idx>oldIdx)
                break;
            //lastSmallerStepIdx=idx;
            lastSmallerStepIdx=i;
        }
        int newIdx=oldIdx + ((lastSmallerStepIdx==-1)? 0: deltaVals.get(lastSmallerStepIdx));
        return newIdx;
    }

    public void applyEdits(List<DocumentEdit> edits) {
        int len=edits.size();
        if(len==0)
            return;
        Collections.sort(edits);
        chars=buff.toString().toCharArray();
        buff.setLength(0);
        buff.ensureCapacity(chars.length+len*128);
        int pos2=0;
        for(int i=0;i<len;i++) {
            DocumentEdit de=(DocumentEdit) edits.get(i);
            int newIdx=oldIdx2newIdx(de.idx);
            if(newIdx==-1) {
                Logger.LOG(Logger.ERR,"Discarded invalid edit \""+de+"\" (written="+pos2+" inserted="+newIdx+" origlen="+chars.length+")");
                continue;
            }
            buff.append(chars, pos2, newIdx-pos2);
            pos2=newIdx;
            if(de.insStr!=null)
                buff.append(de.insStr);
            pos2+=de.delCnt;
        }
        buff.append(chars, pos2, chars.length-pos2);
    }
}


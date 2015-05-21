// $Id: $
package medieq.iet.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

public class EvalInstRecord {
    String name;
    public int goldInstCnt;
    public int autoInstCnt;
    
    public int goldLinkCnt;
    public int matchedGoldLinkCnt; /** of gold links, how many were recovered in automatic output */
    public int autoLinkCnt;
    public int matchedAutoLinkCnt; /** of auto links, how many of them really exist in gold input */

    public HashMap<String,DocEir> docStats;

    // whether to dump diffs of correct vs. auto instances
    public static int SHOW_PROBLEMS=0;
    
    public EvalInstRecord(String name) {
        this.name=name;
        docStats=new HashMap<String,DocEir>();
    }
    
    public void clear() {
        goldInstCnt=0;
        autoInstCnt=0;
        goldLinkCnt=0;
        autoLinkCnt=0;
        matchedGoldLinkCnt=0;
        matchedAutoLinkCnt=0;
        docStats.clear();
    }
    
    public void add(EvalInstRecord other) {
        goldInstCnt+=other.goldInstCnt;
        autoInstCnt+=other.autoInstCnt;
        goldLinkCnt+=other.goldLinkCnt;
        autoLinkCnt+=other.autoLinkCnt;
        matchedGoldLinkCnt+=other.matchedGoldLinkCnt;
        matchedAutoLinkCnt+=other.matchedAutoLinkCnt;
        docStats.putAll(other.docStats);
    }
    
    public double getPrec() {
        return (autoLinkCnt==0)? 1.0: ((double)matchedGoldLinkCnt/(double)autoLinkCnt);
    }

    public double getRecall() {
        return (goldLinkCnt==0)? 1.0: ((double)matchedAutoLinkCnt/(double)goldLinkCnt);
    }
    
    public String toString() {
        String ret="Instances("+name+"): gold="+goldInstCnt+", auto="+autoInstCnt+
            ", villain prec="+matchedGoldLinkCnt+"/"+autoLinkCnt+"="+EvalAttRecord.fmtNum(getPrec())+
            ", villain recall="+matchedAutoLinkCnt+"/"+goldLinkCnt+"="+EvalAttRecord.fmtNum(getRecall())+
            ", F="+EvalAttRecord.fmtNum(EvalAttRecord.fmeasure(getPrec(),getRecall()))+"\n";
        if(SHOW_PROBLEMS>0) {
            ArrayList<DocEir> lst=new ArrayList<DocEir>(docStats.values());
            Collections.sort(lst);
            ret+="Documents by error count:\n";
            for(int i=0;i<lst.size();i++) {
                DocEir de=lst.get(i);
                ret+=" "+(i+1)+"/"+de;
            }
        }
        return ret;
    }

    public void addErrorCountForDoc(String file, int errCnt, String debi) {
        DocEir de=docStats.get(file);
        if(de==null) {
            de=new DocEir(file, errCnt, debi);
            docStats.put(file, de);
        }else {
            de.errCnt+=errCnt;
            if(debi!=null) {
                de.addDebugInfo(debi);
            }
        }
    }
}

class DocEir implements Comparable<DocEir> {
    String file;
    int errCnt;
    String debi;

    public DocEir(String file, int errCnt, String debi) {
        this.file=file;
        this.errCnt=errCnt;
        this.debi=debi;
    }
    public void addDebugInfo(String debi) {
        if(this.debi==null) {
            this.debi=debi;
        }else {
            this.debi+="\n"+debi;
        }
    }
    public int compareTo(DocEir other) {
        int rc=other.errCnt-errCnt;
        if(rc==0)
            rc=hashCode()-other.hashCode();
        return rc;
    }
    public String toString() {
        StringBuffer s=new StringBuffer(128);
        s.append(errCnt);
        s.append("/");
        s.append(file);
        s.append("\n");
        if(EvalInstRecord.SHOW_PROBLEMS>1 && debi!=null && debi.length()>0) {
            s.append(debi);
        }
        return s.toString();
    }
}

// $Id: LabelGroup.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import java.util.ArrayList;
import java.lang.String;
import java.lang.StringBuffer;

/* represented by a <span id="_LBG1"> that contains one child <span id="_LBG1.1"> 
   representing the single shown label of the group */
public class LabelGroup {
    public int id; // 1 ... N in document
    public ArrayList<Label> labels;
    public int startIdx; // char pos within document

    public LabelGroup(int i, int si) {
        id=i;
        startIdx=si;
        labels=new ArrayList<Label>(8);
    }

    protected String esc(String s) {
        return s.replaceAll("'","\\'");
    }

    public void toJS(StringBuffer buff) {
        buff.append("{id:"+id+",labels:[");
        int cnt=labels.size();
        for(int i=0;i<cnt;i++) {
            if(i>0)
                buff.append(',');
            Label lab=(Label) labels.get(i);
            // buff.append("{id:"+lab.id+",s:"+lab.startIdx+",e:"+lab.endIdx+",st:'"+esc(lab.style)+"',ti:'"+esc(lab.title)+"'}");
            buff.append("{id:"+lab.id+",idxs:[");
            // label can now have multiple pairs of start & end indices, max. one pair per each member token
            for(int j=0;j<lab.idxs.length;j+=2) {
                if(j>0)
                    buff.append(',');
                buff.append(lab.idxs[j]+","+lab.idxs[j+1]);
            }
            buff.append("],st:'"+esc(lab.style)+"',ti:'"+esc(lab.title)+"'}");
        }
        buff.append("]}");
    }
}

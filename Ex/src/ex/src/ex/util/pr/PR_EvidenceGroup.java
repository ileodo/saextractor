// $Id: PR_EvidenceGroup.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util.pr;

import java.util.LinkedList;
import java.util.List;

public class PR_EvidenceGroup extends PR_Evidence {
    public static int GRP_OR=1;
    public static int GRP_AND=2;
    
    public int type;
    public List<PR_Evidence> evList;
    public PR_EvidenceGroup(String name, int type, double prec, double recall, byte defaultValue, int idx) {
        super(name, prec, recall, defaultValue, type);
        this.type=type;
        evList=new LinkedList<PR_Evidence>();
    }
}

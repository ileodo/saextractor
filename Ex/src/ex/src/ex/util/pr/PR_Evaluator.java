// $Id: PR_Evaluator.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util.pr;

import java.util.List;

public class PR_Evaluator {
    protected static PR_Example ex;
    protected static PR_Class emptyCls;
    static {
        ex=new PR_Example("EvaluatorExample", null, false, null);
        PR_Evidence[] empty={};
        emptyCls=new PR_Class("EvaluatorClass", -1.0, empty);
    }
    
    public synchronized static double eval(PR_Class cls, byte[] eVals) {
        ex.cls=cls;
        ex.eVals=eVals;
        ex.custEvs=null;
        if(ex.eVals.length!=ex.cls.evs.length)
            throw new IllegalArgumentException("Evidence values="+ex.eVals.length+"; evidence count="+ex.cls.evs.length);
        double p=ex.condProb();
        return p;
    }
    
    public synchronized static double eval(PR_Class cls, List<PR_Evidence> custEvs, byte[] eVals) {
        ex.cls=cls;
        ex.eVals=eVals;
        ex.custEvs=custEvs;
        if(ex.eVals.length!=ex.cls.evs.length+ex.custEvs.size())
            throw new IllegalArgumentException("Evidence values="+ex.eVals.length+"; class evidence count="+ex.cls.evs.length+"; custom evidence count="+custEvs.size());
        double p=ex.condProb();
        return p;
    }
    
    public synchronized static double eval(double clsPrior, PR_Evidence[] evs1, List<PR_Evidence> evs2, byte[] eVals) {
        emptyCls.prior=clsPrior;
        emptyCls.evs=evs1;
        ex.cls=emptyCls;
        ex.eVals=eVals;
        ex.custEvs=evs2;
        if(ex.eVals.length!=(((ex.custEvs!=null)? ex.custEvs.size(): 0)+((emptyCls.evs!=null)? emptyCls.evs.length: 0)))
            throw new IllegalArgumentException("Evidence values="+ex.eVals.length+"; custom evidence count="+ex.custEvs.size());
        double p=ex.condProb();
        return p;
    }
}

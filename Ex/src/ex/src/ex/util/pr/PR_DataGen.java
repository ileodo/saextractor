// $Id: PR_DataGen.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util.pr;

/** 
 * Randomly generates examples according to a generative
 * model derived from evidence precision/recall model.
 * Examples are generated in the form [A, E_1,...,E_n]
 * where n=|Phi_A|, the set of all evidence known for attribute A.
 * Only binary patterns (which either hold or not) are supported.
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.Logger;

public class PR_DataGen {
    private Logger log;

    public PR_DataGen() {
        log=Logger.getLogger();
    }

    public int generate(PR_Class cls, PR_Example[] examples, int n) {
        if(examples.length<n) {
            log.LG(Logger.ERR,"Not enough space for "+n+" examples");
            return -1;
        }
        for(int i=0;i<n;i++)
            examples[i]=PR_Example.generate(cls,"[x"+(i+1)+"]");
        return 0;
    }

    public int generate(PR_Class cls, HashMap<String,PR_Example> examples, int n) {
        for(int i=0;i<n;i++) {
            PR_Example ex=PR_Example.generate(cls,"[x"+(i+1)+"]");
            String key=ex.toValues();
            if(examples.containsKey(key))
                ((PR_Example)examples.get(key)).count++;
            else
                examples.put(ex.toValues(), ex);
        }
        return 0;
    }

    public static void main(String[] args) {
        PR_Evidence[] evs=null;
        double prior;
        int num=0;

        if(args.length==0) {
            // default P(A) with 2 evidences
            prior=0.1;
            PR_Evidence e1=new PR_Evidence("monitor name: <X>", 0.5, 0.5, (byte)0, 0);
            PR_Evidence e2=new PR_Evidence("lcd <manufacturer> <alphanum>", 0.5, 0.5, (byte)0, 1);
            evs=new PR_Evidence[2]; evs[0]=e1; evs[1]=e2;
        }else if((args.length-2)%2==0) {
            num=Integer.parseInt(args[0]);
            prior=Double.parseDouble(args[1]);
            int evCnt=(args.length-1)/2;
            evs=new PR_Evidence[evCnt];
            int cnt=0;
            for(int i=2;i<args.length;i+=2) {
                try {
                    double prec=Double.parseDouble(args[i]);
                    double recall=Double.parseDouble(args[i+1]);
                    evs[cnt]=new PR_Evidence("e"+cnt,prec,recall,(byte)0,cnt);
                    cnt++;
                }catch(NumberFormatException ex) {
                    System.out.println(ex.toString());
                }
            }
        }else {
            System.out.println("Expecting 2+N*2 args, e.g.: cnt, prior,  e1_prec, e1_rec,  e2_prec, e2_rec");
            return;
        }

        PR_DataGen gen=new PR_DataGen();
        PR_Class cls=new PR_Class("monitor_name",prior,evs);
        cls.dumpGenerativeModel();
        PR_Example[] examples=null;
        int rc;
        if(true) { // only generate counts of examples, not individual objects
            HashMap<String,PR_Example> map=new HashMap<String,PR_Example>(20);
            rc=gen.generate(cls, map, num);
            Collection<PR_Example> prototypes=map.values();
            examples=new PR_Example[prototypes.size()];
            Iterator<PR_Example> it=prototypes.iterator(); int i=0;
            while(it.hasNext()) {
                examples[i++]=(PR_Example) it.next();
            }
        }else {
            examples=new PR_Example[num];
            rc=gen.generate(cls, examples, num);
        }
        if(rc!=0) {
            Logger.LOGERR("Error generating examples");
            return;
        }
        Arrays.sort(examples);

        System.out.println();
        for(int i=0;i<examples.length;i++) {
            PR_Example ex=examples[i];
            System.out.println(ex.toString());
            System.out.println("P(CLS|evs)="+ex.condProb());
            System.out.println();
        }
    }

}

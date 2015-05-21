// $Id: CountConstraintFactory.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uep.util.Logger;

import ex.model.ClassDef;
import ex.model.Model;
import ex.util.search.PathConstraint;
import ex.util.search.PathConstraintFactory;

/** A factory for instance count constraints of selected classes. */
public class CountConstraintFactory implements PathConstraintFactory {
    Model model;
    int[] clsId2Idx; // maps clsId (0..N-1) to constrained class index
    ClassDef[] conClasses; // constrained classes (by constrained class index)
    //CountConstraint[][] constraints; // cache of used constraint singletons; matrix organized as [clsId][count]
    Map<CountConstraint, CountConstraint> cache; // cache mapping query constraint -> singleton
    CountConstraint query; // only used to search in map
    boolean minCountsExist;
    
    public boolean hasConstraints() {
        return conClasses!=null;
    }
    
    /** Creates a factory for instance count constraints based on the class definitions from model. */
    public CountConstraintFactory(Model model) {
        this.model=model;
        int constrainedClassCnt=0;
        int expCacheSz=0;
        minCountsExist=false;
        List<ClassDef> conClassList=new ArrayList<ClassDef>(16);
        for(ClassDef cls: model.classArray) {
            if(cls.countDist!=null) {
                int len=Math.max(cls.countDist.min, cls.countDist.max);
                if(len>0) {
                    constrainedClassCnt++;
                    expCacheSz+=Math.min(len,100);
                    conClassList.add(cls);
                    if(cls.countDist.min>0) {
                        minCountsExist=true;
                    }
                }
            }
        }
        if(constrainedClassCnt>0) {
            query=new CountConstraint(new int[constrainedClassCnt]);
            cache=new HashMap<CountConstraint, CountConstraint>(expCacheSz);
            conClasses=new ClassDef[constrainedClassCnt];
            clsId2Idx=new int[model.classArray.length];
            for(int i=0;i<model.classArray.length;i++) {
                clsId2Idx[i]=-1;
            }
            for(int cidx=0;cidx<conClassList.size();cidx++) {
                ClassDef cls=conClassList.get(cidx);
                clsId2Idx[cls.id]=cidx;
                conClasses[cidx]=cls;
            }
        }        
    }
    
    /** Only allows paths with instance counts of selected types not exceeding a threshold */
    public PathConstraint createNextConstraint(Object nextObject, PathConstraint prevConstraint) {
        CountConstraint prev=(CountConstraint) prevConstraint;
        PathConstraint con=(CountConstraint) prevConstraint; // by default do not change the previous constraint
        if(nextObject!=null) {
            if(nextObject instanceof ICBase) {
                ICBase ic=(ICBase) nextObject;
                ClassDef cls=ic.clsDef;
                int idx=clsId2Idx[cls.id];
                if(idx!=-1) { // constrained class
                    int cnt=(prev!=null)? (prev.counts[idx]+1): 1;
                    if(cnt>cls.countDist.max && cls.countDist.max>0) {
                        con=PathConstraint.FORBIDDEN;
                    }else {
                        // get cached constraint
                        if(prev!=null) {
                            System.arraycopy(prev.counts, 0, query.counts, 0, prev.counts.length);
                        }else {
                            Arrays.fill(query.counts, 0);
                        }
                        query.counts[idx]=cnt;
                        con=getCachedConstraint(query);
                    }
                }
            }
        }
        return con;
    }

    /** Ensures constraint instances are reused. */
    private PathConstraint getCachedConstraint(CountConstraint query) {
        CountConstraint con=cache.get(query);
        if(con==null) {
            int[] copy=new int[query.counts.length];
            System.arraycopy(query.counts, 0, copy, 0, query.counts.length);
            con=new CountConstraint(copy);
            cache.put(query, con);
        }
        return con;
    }

    /** Checks whether all classes of the model have reached their minimal instance counts on the full path. */
    public boolean isValidFinal(PathConstraint finalConstraint) {
        if(!minCountsExist)
            return true;
        if(finalConstraint==null)
            return false; // no instance of constrained class with minCnt>0 is on path
        CountConstraint fin=(CountConstraint) finalConstraint;
        for(ClassDef cls: conClasses) {
            if(fin.counts[clsId2Idx[cls.id]] < cls.countDist.min)
                return false;
        }
        return true;
    }
}

/** Test constraint expressing the instance counts of different classes on the path leading to this constraint. */
class CountConstraint implements PathConstraint {
    public int[] counts; // indexed by class IDs
    
    public CountConstraint(int[] counts) {
        this.counts=counts;
        //Logger.LOG(Logger.ERR,"********* CC = "+this);
    }
    
    /** CountConstraints should be ordered in opposite order
     * when there are limits for minimal or maximal counts. 
     * So we do not prefer any ordering and we always return 0. */
    public int compareTo(PathConstraint o) {
        return 0;
    }
 
    public int hashCode() {
        int sum=0;
        for(int cls=0;cls<counts.length;cls++) {
            sum+=(cls+1)*counts[cls];
        }
        return sum;
    }
    
    public boolean equals(Object o) {
        if(!(o instanceof CountConstraint))
            return false;
        CountConstraint other=(CountConstraint) o;
        if(other.counts.length!=counts.length)
            return false;
        for(int i=0;i<counts.length;i++) {
            if(counts[i]!=other.counts[i])
                return false;
        }
        return true;
    }
    
    public String toString() {
        StringBuffer s=new StringBuffer(counts.length*2);
        for(int cls=0;cls<counts.length;cls++) {
            if(cls>0)
                s.append(',');
            s.append(counts[cls]);
        }
        return s.toString();
    }
}

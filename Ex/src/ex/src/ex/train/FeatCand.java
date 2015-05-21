// $Id: FeatCand.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import ex.features.ClassificationF;
import ex.features.TokenNgramF;
import ex.model.ModelElement;

public class FeatCand implements Comparable<FeatCand> {
    private static int lastId=0;
    public int id;
    // public String name;
    public ModelElement elem;
    public byte pos;
    public List<AbstractPhrase> ngrams; // phrases that define this feature
    public double maxPWMI; // max pointwise mutual information between this feature and the most correlated model element
    public int occCnt; // total occurrence count of listed ngrams

    FeatCand() {
        ngrams=new LinkedList<AbstractPhrase>();
        maxPWMI=0;
        occCnt=0;
        elem=null;
        pos=(byte)0;
        id=++lastId;
    }
    
    public int compareTo(FeatCand o) {
        FeatCand f2=(FeatCand) o;
        double miDiff = maxPWMI - f2.maxPWMI;
        if(miDiff>0.1)
            return 1;
        else if(miDiff<0.1)
            return -1;
        else {
            int cntDiff = ngrams.size() - f2.ngrams.size();
            if(cntDiff>0)
                return 1;
            else if(cntDiff<0)
                return -1;
            else if(miDiff!=0)
                return (miDiff>0)? 1:-1;
        }
        return 0;
    }
    public void toString(StringBuffer b) {
        b.append("f=");
        b.append(id);

//        if(name!=null) {
//            b.append("-");
//            b.append(name);
//        }
        b.append("-");
        b.append(((elem==null)? ClassificationF.BG: elem.name)+"-"+TokenNgramF.pos2string(pos));
        
        b.append(",n=");
        b.append(occCnt);
        b.append(",i=");
        b.append(nf.format(maxPWMI));
    }
    public String toString() {
        StringBuffer b=new StringBuffer(32);
        toString(b);
        return b.toString();
    }
    public static java.text.NumberFormat nf=java.text.NumberFormat.getInstance(Locale.ENGLISH);
    static {
        nf.setMaximumFractionDigits(5);
        nf.setGroupingUsed(false);
    }
}

class FeatCandList extends LinkedList<FeatCand> {
    private static final long serialVersionUID = -3227429188508532865L;
    public FeatCandList() {
        super();
    }
    public FeatCandList(Collection<FeatCand> items) {
        super(items);
    }
    public String toString() {
        StringBuffer b=new StringBuffer(128); 
        Iterator<FeatCand> fit=this.iterator();
        int i=0;
        while(fit.hasNext()) {
            if(i!=0) {
                b.append('|');
            }
            FeatCand fc=fit.next();
            fc.toString(b);
            i++;
        }
        //b.append('\n');
        return b.toString();
    }
}

class FeatureListBookAdapter implements PhraseBookValueAdapter<List<FeatCand>,FeatCand> {
    public List<FeatCand> addValue(List<FeatCand> oldValue, FeatCand newValue, NgramCounts bookCounts) {
        if(newValue==null) {
            oldValue=null;
        }else {
            if(oldValue==null) {
                oldValue=new FeatCandList();
            }
            oldValue.add(newValue);
        }
        return oldValue; 
    }
}

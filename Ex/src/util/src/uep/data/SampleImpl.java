// $Id: $
package uep.data;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public class SampleImpl implements Sample, Comparable<SampleImpl> {
    protected Map<Integer,String> featValues;
    protected int weight;
    protected Map<String,Integer> srcPhrases;
    
    public SampleImpl() {
        featValues=new TreeMap<Integer,String>();
        weight=1;
    }
    
    public Sample clone() throws CloneNotSupportedException {
        SampleImpl copy=new SampleImpl();
        copy.setWeight(weight);
        for(Map.Entry<Integer, String> en: featValues.entrySet()) {
            copy.setFeatureValue(en.getKey(), en.getValue());
        }
        if(srcPhrases!=null) {
            copy.srcPhrases=new TreeMap<String,Integer>();
            for(Map.Entry<String, Integer> en: srcPhrases.entrySet()) {
                copy.srcPhrases.put(en.getKey(), en.getValue());
            }
        }
        return copy;
    }
    
    public String getFeatureValue(int id) {
        return featValues.get(id);
    }

    public void setFeatureValue(int id, String val) {
        if(val==null)
            featValues.remove(id);
        else
            featValues.put(id, val.intern());
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight=weight;
    }
    
    public int incWeight(int weight) {
        this.weight+=weight;
        return this.weight;
    }

    public int decWeight(int weight) {
        this.weight-=weight;
        return this.weight;
    }
    
    public String toString() {
        return toString(new StringBuffer(3*featValues.size())).toString();
    }
    
    public StringBuffer toString(StringBuffer buff) {
        int i=0;
        for(Map.Entry<Integer, String> en: featValues.entrySet()) {
          if(i>0)
              buff.append(",");
          buff.append(en.getKey());
          buff.append("=");
          buff.append(en.getValue());
          i++;
      }
      return buff;
    }

    public boolean equals(Object other) {
        if(! (other instanceof SampleImpl))
            return false;
        return compareTo((SampleImpl)other)==0;
    }

    public int compareTo(SampleImpl other) {
        Iterator<Map.Entry<Integer, String>> it1=featValues.entrySet().iterator();
        Iterator<Map.Entry<Integer, String>> it2=((SampleImpl)other).featValues.entrySet().iterator();
        while(it1.hasNext()) {
            if(!it2.hasNext()) {
                return 1;
            }else {
                Map.Entry<Integer, String> e1=it1.next();
                Map.Entry<Integer, String> e2=it2.next();
                int diffId=e1.getKey()-e2.getKey();
                if(diffId==0) {
                    int diffVal=e1.getValue().compareTo(e2.getValue());
                    if(diffVal==0) {
                        continue;
                    }else {
                        return diffVal;
                    }
                }else {
                    return diffId;
                }
            }
        }
        if(it2.hasNext()) {
            return -1;
        }
        return 0;
    }

    public void clear() {
        clearDebugInfo();
        featValues.clear();
        weight=0;
    }

    public int getEnabledFeatureCount() {
        return featValues.size();
    }

    public Iterator<Entry<Integer, String>> iterator() {
        return featValues.entrySet().iterator();
    }
    
    public Map<String,Integer> getDebugInfo() {
        return srcPhrases;
    }
    
    public void addDebugInfo(String phr) {
        if(srcPhrases==null) {
            srcPhrases=new TreeMap<String,Integer>();
        }
        Integer cnt=srcPhrases.get(phr);
        if(cnt==null)
            cnt=1;
        else
            cnt++;
        srcPhrases.put(phr, cnt);
    }
    
    public void clearDebugInfo() {
        if(srcPhrases!=null) {
            srcPhrases.clear();
            srcPhrases=null;
        }
    }
}

class UnclonableSampleImpl extends SampleImpl {
    public UnclonableSampleImpl() {
        super();
    }
    
    public Sample clone() throws CloneNotSupportedException {
        return this;
    }
}

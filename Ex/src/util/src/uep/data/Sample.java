// $Id: $
package uep.data;

import java.util.Map;

public interface Sample extends Iterable<Map.Entry<Integer,String>> {
    public static final Sample EOF=new UnclonableSampleImpl();
    public static final Sample EOS=new UnclonableSampleImpl();
    
    public String toString();
    /** @return a shallow copy of this sample. */
    public Sample clone() throws CloneNotSupportedException;
    public boolean equals(Object other);
    public StringBuffer toString(StringBuffer buff);
    /** @return weight of this sample. Also used to represent sample frequency in unique sample sets. */
    public int getWeight();
    public void setWeight(int weight);
    public int incWeight(int weight);
    public int decWeight(int weight);
    /** @return value of the feature with the given id, null if the value is missing. */
    public String getFeatureValue(int id);
    /** Sets value of the feature with given id. */
    public void setFeatureValue(int id, String val);
    /** @return number of observed features for this sample. */
    public int getEnabledFeatureCount();
    /** @return clears all members of this sample. */
    public void clear();
    /** @return map of debug strings with counts. */
    public Map<String,Integer> getDebugInfo();
    public void addDebugInfo(String phr);
    public void clearDebugInfo();
}

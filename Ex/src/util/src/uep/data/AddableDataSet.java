// $Id: $
package uep.data;

import java.io.BufferedWriter;
import java.io.IOException;

/** Interface for data sets that can be added together. */
public interface AddableDataSet {
    /** Name of this data set. */
    public String getName();
    /** Sets name of this data set. */
    public void setName(String name);
    /** Adds the whole content of dataSet to this data Set. */ 
    public void addAll(AddableDataSet dataSet);
    /** Number of items in this data set */
    public int size();
    /** Clears contents of this data set. */
    public void clear();
    /** Dumps contents of this data set to a writer. */
    public void writeTo(BufferedWriter out) throws IOException;
}

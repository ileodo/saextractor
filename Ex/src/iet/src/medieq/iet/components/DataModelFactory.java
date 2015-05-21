// $Id: DataModelFactory.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import medieq.iet.api.IETException;
import medieq.iet.generic.DataModelImpl;
import medieq.iet.model.DataModel;
import uep.util.*;

public class DataModelFactory {
    /** Reads a data model from file */
    public static DataModel readDataModelFromFile(String fileName) throws IETException {
        DataModel dm=new DataModelImpl(fileName, "noname");
        Logger.LOG(Logger.TRC,"Read data model "+dm.getName()+" from "+fileName);
        return dm;
    }

    /** Writes a data model to a file */
    public static void writeDataModelToFile(DataModel dm) throws IETException {
        String fn=dm.getUrl();
        Logger.LOG(Logger.TRC,"Saving data model "+dm.getName()+" to "+fn);
    }

    /** Creates an empty data model with the given name. */
    public static DataModel createEmptyDataModel(String name) {
        DataModel dm=new DataModelImpl(null, name);
        Logger.LOG(Logger.TRC,"Creating empty data model "+dm.getName());
        return dm;
    }
}

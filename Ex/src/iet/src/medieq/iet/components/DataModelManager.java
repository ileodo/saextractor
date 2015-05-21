// $Id: DataModelManager.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.util.*;
import java.io.IOException;
import medieq.iet.model.*;

/** Maintains a collection of loaded IET data models, loads and saves them */
public interface DataModelManager {
    public List<DataModel> getDataModels();
    public DataModel getDataModelByName(String name);
    public DataModel readDataModel(String fileName) throws IOException;
    public void writeDataModel(DataModel model, String fileName) throws IOException;
    public void uninitialize();
}

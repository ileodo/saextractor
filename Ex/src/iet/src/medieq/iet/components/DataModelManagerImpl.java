// $Id: DataModelManagerImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import medieq.iet.api.IETApi;
import medieq.iet.model.DataModel;

/* (non-Javadoc)
 * @see medieq.iet.components.DataModelManager
 */
public class DataModelManagerImpl implements DataModelManager {
    protected List<DataModel> dataModels;
    protected IETApi iet;
    
    public DataModelManagerImpl(IETApi iet) {
        this.iet=iet;
        dataModels=new ArrayList<DataModel>(8);
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.components.DataModelManager#getDataModels()
     */
    public List<DataModel> getDataModels() {        
        return dataModels;
    }

    /* (non-Javadoc)
     * @see medieq.iet.components.DataModelManager#getDataModelByName(java.lang.String)
     */
    public DataModel getDataModelByName(String name) {
        Iterator<DataModel> dit=dataModels.iterator();
        while(dit.hasNext()) {
            DataModel dm=dit.next();
            if(dm.getName().equals(name))
                return dm;
        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.components.DataModelManager#readDataModel(java.lang.String)
     */
    public DataModel readDataModel(String fileName) throws IOException {
        throw new IOException("Not implemented");
    }

    /* (non-Javadoc)
     * @see medieq.iet.components.DataModelManager#writeDataModel(medieq.iet.model.DataModel, java.lang.String)
     */
    public void writeDataModel(DataModel model, String fileName) throws IOException {
        throw new IOException("Not implemented");
    }

    /* (non-Javadoc)
     * @see medieq.iet.components.DataModelManager#uninitialize()
     */
    public void uninitialize() {
        ;
    }
}

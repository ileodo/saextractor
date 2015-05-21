// $Id: IETApi.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.api;

import java.util.List;

import medieq.iet.components.Configurable;
import medieq.iet.components.DataModelManager;
import medieq.iet.components.Engine;
import medieq.iet.components.Evaluator;
import medieq.iet.components.TaskManager;

/** High-level API to IET */
public interface IETApi {
    /** Initializes IET, loads IE engines according to config file. 
     * Call this once prior to all other methods. This call is synchronous.
     * @param cfgFile filepath to the IET config file.
     * @return number of initialized IE engines.
     * @throws IETException */
    public int initialize(String cfgFile) throws IETException;
    
    /** Deallocates all extraction engines. Terminates any running extraction tasks.
     * This call is synchronous. */
    public void uninitialize() throws IETException;
    
    /** Returns the IET's DataModelManager component */
    public DataModelManager getDataModelManager();
    
    /** Returns the IET's TaskManager component */
    public TaskManager getTaskManager();

    /** Returns the IET's Evaluator component */
    public Evaluator getEvaluator();

    /** Returns an IE engine by name */
    public Configurable getEngineByName(String engineName);
    
    /** Returns all registered IE engines */
    public List<Configurable> getEngines();
}

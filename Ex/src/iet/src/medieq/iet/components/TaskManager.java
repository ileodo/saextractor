// $Id: TaskManager.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.util.List;
import java.util.Map;

import medieq.iet.api.IETException;
import medieq.iet.model.Document;
import medieq.iet.model.TaskListener;

/** TaskManager creates extraction Tasks, runs them in multiple threads and 
 *  maintains lists of user-defined listeners. Tasks can be stopped at any time. */

public interface TaskManager {
    /** Creates and runs an extraction task containing the given documents and based
     * on the specified extraction model.
     * @param taskId Name of the task
     * @param inputDocumentList list of name-value maps representing documents
     * @param annotationList list to be filled by name-value maps representing annotations
     * @param dataModel data model to use for all documents. If null, 
     *        IET attempts to determine the appropriate model based on each document's classification.
     *        The mapping of classifications to data models is defined in IET config file.
     * @param processedDocumentList list of medieq.iet.Document objects containing 
     *        highlighted document source, extracted attribute and instance lists.
     * @param sync whether this call should block until all documents are processed or return immediately
     * @return task handle that uniquely identifies the running task
     * @throws IETException
     */
    public int processTask(String taskId,
            List<Map<String, String>> procedures,
            List<Map<String,Object>> inputDocumentList,
            List<Map<String,Object>> annotationList,
            String dataModelUrl,
            List<Document> processedDocumentList,
            boolean sync
           ) throws IETException;
    
    /** Same as processTask above, but fetches the task description from a file. */
    public int processTask(String taskFile, 
            List<Document> processedDocumentList, 
            boolean sync
            ) throws IETException;
    
    /** Terminates an extraction task.
     * @param taskHandle task handle returned by processTask
     * @param sync whether this call should block until the extraction task is terminated 
     * @return true if the task was found and terminated */
    public boolean stopTask(int taskHandle, boolean sync) throws IETException;
    
    /** Terminates all extraction tasks.
     * @param sync whether this call should block until all extraction tasks are terminated 
     * @return true if any tasks were found and terminated */
    public boolean stopAllTasks(boolean sync) throws IETException;
    
    /** Registers a listener which gets notified when each document's processing finishes.
     * The listener will be called for a specific task or for any task if taskHandle==-1. */
    public void registerListener(int taskHandle, TaskListener taskListener);
    
    /** Unregisters a previously registered listener. */
    public void unregisterListener(TaskListener taskListener);

    /** Terminates any running tasks and unregisters all listeners. */
    public void uninitialize() throws IETException;
}

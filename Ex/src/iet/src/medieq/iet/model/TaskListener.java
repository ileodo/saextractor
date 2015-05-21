// $Id: TaskListener.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

public interface TaskListener {
	public void onDocumentProcessed(Task task, int idx, Document doc);
	public void onStateChange(Task task, int state);
}

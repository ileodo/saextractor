// $Id: IETWin.java 1989 2009-04-22 18:48:45Z labsky $
package medieq.iet.ui;

import uep.util.Logger;
import medieq.iet.api.IETApi;
import medieq.iet.api.IETApiImpl;
import medieq.iet.api.IETException;
import medieq.iet.components.*;

public class IETWin {
    static IETApi iet;
    
	/**
	 * @param args
	 */
	public static void main(String[] args) throws IETException {
		System.err.println("Hello from IET");
        // Logger.init("iet.log", -1, 0, null); // let IETApiImpl initialize logger based on cfg 
        
        String cfg="iet.cfg";
        iet=new IETApiImpl();
        iet.initialize(cfg);
        
//		Logger.LOG(Logger.INF,"Initializing engines...");
//		EngineFactory factory=new EngineFactory();
//		Engine e1=factory.createEngine("ex.api.Ex");
//		boolean rc=e1.initialize("config.cfg");
//		if(!rc) {
//		    Logger.LOG(Logger.ERR,"Could not initialize engine from cfg");
//          return;
//		}
		
        
		MainWindow mw=new MainWindow(iet);
//		mw.addEngine(e1);
		mw.setVisible(true);
		
		/*
		TaskFactory fact=new TaskFactory();
		Task task=fact.readTask("c:/projekty3/iet/data/all_contacts.task");
		if(task!=null) {
			try {
				fact.writeTask(task, "c:/projekty3/iet/data/all_contacts_written.task");
			}catch(IOException ex) {
				System.err.println("Cannot write: "+ex);
			}
		}
		task=fact.readTask("c:/projekty3/iet/data/all_contacts_written.task");
		if(task!=null) {
			try {
				fact.writeTask(task, "c:/projekty3/iet/data/all_contacts_written2.xml");
			}catch(IOException ex) {
				System.err.println("Cannot write: "+ex);
			}
		}
		*/
		// System.out.println("Good bye from IET");
	}

}

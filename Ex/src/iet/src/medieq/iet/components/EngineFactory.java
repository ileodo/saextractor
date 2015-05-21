// $Id: EngineFactory.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import uep.util.Logger;
import medieq.iet.api.IETException;

public class EngineFactory {
    public Configurable createEngine(String className) throws IETException {
        Configurable engine=null;
        String msg=null;
        Throwable e=null;
        try {
            Class engineClass=Class.forName(className);
            Logger.LOG(Logger.TRC, "Found engine class "+className);
            engine=(Configurable) engineClass.newInstance();
            Logger.LOG(Logger.TRC, "Instantiated engine "+className);
        }catch(ClassNotFoundException ex) {
            e=ex;
            msg="Cannot find engine class on CLASSPATH: "+className+": "+ex;
        }catch(NoClassDefFoundError ex) {
            e=ex;
            msg="Cannot find engine class on CLASSPATH: "+className+": "+ex;
        }catch(InstantiationException ex) {
            e=ex;
            msg="Cannot instantiate engine "+className+": "+ex;
        }catch(IllegalAccessException ex) {
            e=ex;
            msg="Not allowed to instantiate engine "+className+": "+ex;
        }
        if(msg!=null)
            Logger.LOG(Logger.ERR, msg);
        if(engine==null)
            throw new IETException(msg, e);
        return engine;
    }
    public void disposeEngine(Engine engine) {
        return;
    }
}

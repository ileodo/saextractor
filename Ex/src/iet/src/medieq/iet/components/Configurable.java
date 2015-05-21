// $Id: Configurable.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.util.*;
import java.io.IOException;
import java.io.InputStream;

public interface Configurable {
    public boolean initialize(String configFile) throws IOException;
    public void uninitialize();
    public Object getParam(String name);
    public void setParam(String name, Object value);
    public void configure(Properties params);
    public void configure(InputStream cfgFile) throws IOException;
    
    public String getName();
    public boolean cancel(int cancelType);
}

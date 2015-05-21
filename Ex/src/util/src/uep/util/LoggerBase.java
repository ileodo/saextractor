// $Id: LoggerBase.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

public abstract class LoggerBase {
    // possible log levels
    protected static final String[] logLevels={"USR","ERR","WRN","INF","TRC","MML"};

    // logging methods
    public abstract void LG(String msg);
    public abstract void LGERR(String msg);
    public abstract void LG(int level, String msg);
}

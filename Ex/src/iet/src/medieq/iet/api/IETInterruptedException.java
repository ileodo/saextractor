// $Id: IETInterruptedException.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.api;

public class IETInterruptedException extends IETException {
    private static final long serialVersionUID = -6805890495832840939L;
    public IETInterruptedException(String message) {
        super(message);
    }
    public IETInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}

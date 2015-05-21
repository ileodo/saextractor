// $Id: IETException.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.api;

public class IETException extends Exception {
    private static final long serialVersionUID = -5742386660718624585L;
    public IETException(String message) {
        super(message);
    }
    public IETException(String message, Throwable cause) {
        super(message, cause);
    }
}

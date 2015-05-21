// $Id: ObjectRef.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

public class ObjectRef<E> {
    public ObjectRef(E data) {
        this.data=data;
    }
    public E data;
}

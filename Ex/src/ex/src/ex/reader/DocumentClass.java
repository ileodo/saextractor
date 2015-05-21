// $Id: DocumentClass.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

public class DocumentClass {
    public DocumentClass(String name, double prob) {
        this.name=name;
        this.prob=prob;
    }
    public String name;
    public double prob;
    public String toString() {
        return name+" "+String.format("%.3f", prob);
    }
}

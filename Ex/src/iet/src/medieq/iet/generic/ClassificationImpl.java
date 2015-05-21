// $Id: ClassificationImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.generic;

public class ClassificationImpl implements medieq.iet.model.Classification {
	protected String className;
	protected double conf;
	
	public ClassificationImpl(String className, double conf) {
		this.className=className;
		this.conf=conf;
	}
	
	/* Classification impl */
	public String getClassName() {
		return className;
	}

	public double getConfidence() {
		return conf;
	}
	
	/* write access methods */
	public void getClassName(String className) {
		this.className=className;
	}

	public void setConfidence(double conf) {
		this.conf=conf;
	}
	
	public String toString() {
	    return className+"("+String.format("%.2f", conf)+")";
	}
}

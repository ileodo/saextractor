// $Id: Evaluator.java,v 1.1 2007/02/21 09:59import java.io.IOException;

package medieq.iet.components;

import java.io.IOException;
import java.util.List;

import medieq.iet.model.*;

public interface Evaluator {
    public void eval(Document goldDoc, Document autoDoc, EvalResult res) throws IOException;
    public void eval(DocumentSet goldDocs, DocumentSet autoDocs, EvalResult res) throws IOException;
    public void setDataModel(DataModel model);
    public DataModel getDataModel();
    public void setDocumentReader(DocumentReader reader);
    public DocumentReader getDocumentReader();
    /** Computes micro-averaged results from macro-averaged EvalResult that each Document in docs has. */
    public void getMicroResults(List<Document> docs, MicroResult micro);
}

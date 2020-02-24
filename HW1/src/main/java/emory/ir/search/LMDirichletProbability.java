package emory.ir.search;

import emory.ir.index.DocField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

import java.util.HashMap;
import java.util.Map;

public class LMDirichletProbability {

    private double mu;
    private Map<Integer, Integer> docLenMap = null;
    private Map<String, Double> termGlobalProbMap = null;
    private Map<String, HashMap<Integer, Double>> termProbMap = null;  // (term, docId) -> prob

    private boolean debug = false;
    private Double unseenTermProb = 2e-5;

    public LMDirichletProbability(double mu) {this.mu = mu;}

    public void initializeProb(IndexReader reader, ScoreDoc[] hits) throws Exception {
        docLenMap = new HashMap<>();
        termGlobalProbMap = new HashMap<>();
        termProbMap = new HashMap<>();
        long numAllTokensAcrossDocs = reader.getSumTotalTermFreq(DocField.TEXT);

        // Calculate term prob
        for(int i = 0; i < hits.length; ++i) {
            Integer docId = hits[i].doc;
            Terms termVec = reader.getTermVector(hits[i].doc, DocField.TEXT);
            long docLen = termVec.getSumTotalTermFreq();
            long numUniqueTokens = termVec.size();
            docLenMap.put(docId, (int)docLen);

            TermsEnum terms = termVec.iterator();
            BytesRef term = null;
            while((term = terms.next()) != null) {
                String termText = term.utf8ToString();
                termProbMap.putIfAbsent(termText, new HashMap<>());

                // Get term global prob
                double globalTermProb = termGlobalProbMap.getOrDefault(termText, -1D);
                if(globalTermProb < 0) {
                    globalTermProb = reader.totalTermFreq(new Term(DocField.TEXT, term)) / (double)numAllTokensAcrossDocs;
                    termGlobalProbMap.put(termText, globalTermProb);
                }

                PostingsEnum posting = terms.postings(null, PostingsEnum.FREQS);
                posting.nextDoc();
                int termFreq = posting.freq();
                double dirichletProb = (termFreq + mu * globalTermProb) / (docLen + mu);
                termProbMap.get(termText).put(docId, dirichletProb);
                if(debug)
                    System.out.println(String.format("term: %s, docLen: %d, termFreq: %d, globalTermProb: %e; dirichletProb: %e",
                            termText, docLen, termFreq, globalTermProb, dirichletProb));
            }
        }
    }

    public double getTermProb(String termText, int docId) {
        Map<Integer, Double> docMap = termProbMap.getOrDefault(termText, null);
        if(docMap == null) {
            // Unseen word w.r.t the whole collection
            return unseenTermProb;
        } else {
            double prob = docMap.getOrDefault(docId, -1D);
            if(prob < 0) {
                // Unseen word w.r.t the current document
                return (mu * termGlobalProbMap.get(termText)) / (docLenMap.get(docId) + mu);
            } else {
                return docMap.get(docId);
            }
        }
    }

    /**
     * Get marginal prob of the term across top k document.
     */
    public double getTermProbSumAcrossDocs(String termText, ScoreDoc[] hits, int k) {
        Map<Integer, Double> docMap = termProbMap.getOrDefault(termText, null);
        if(docMap == null) {
            return unseenTermProb * hits.length;
        } else {
            double probSum = 0;
            double globalProb = termGlobalProbMap.get(termText);
            for(int i = 0; i < k; ++i) {
                Integer docId = hits[i].doc;
                double prob = docMap.getOrDefault(docId, -1D);
                if(prob < 0)
                    probSum += (mu * globalProb) / (docLenMap.get(docId) + mu);
                else
                    probSum += prob;
            }
            return probSum;
        }
    }

}

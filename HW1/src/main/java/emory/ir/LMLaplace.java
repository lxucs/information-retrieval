package emory.ir;


import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.LMSimilarity;


public class LMLaplace extends LMSimilarity {

    @Getter
    @Setter
    private int vocabSize = 0;

    public LMLaplace() {
    }

    public LMLaplace(int vocabSize) {
        this.vocabSize = vocabSize;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    protected double score(BasicStats stats, double freq, double docLen) {
        if(vocabSize == 0) {
            // No smoothing
            return freq / docLen;
        } else {
            // With smoothing
            return (freq + 1) / (docLen + vocabSize);
        }
    }
}

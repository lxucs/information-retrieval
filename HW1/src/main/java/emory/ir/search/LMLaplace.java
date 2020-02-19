package emory.ir.search;


import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.LMSimilarity;
import org.apache.lucene.search.CollectionStatistics;


public  class LMLaplace extends LMSimilarity {


    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    protected double score(BasicStats stats, double freq, double docLen) {

        // add 1 to every count and then normalize
        ((LMStats)stats).getCollectionProbability();
        return (double)(1 + freq) / (doclen + stats.getNumberOfFieldTokens());
    }
}

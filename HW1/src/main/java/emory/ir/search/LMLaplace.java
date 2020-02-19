package emory.ir.search;


import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.LMSimilarity;



public  class LMLaplace extends LMSimilarity {


    @Override
    public String getName() {
        return null;
    }

    @Override
    protected double score(BasicStats stats, double freq, double docLen) {

        // add 1 to every count and then normalize

        return 1;
    }
}

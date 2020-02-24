package emory.ir.search;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.Math;
import emory.ir.index.DocField;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class SearchFiles {

    private static boolean debug = false;
    private static String unkownToken = "#UNKNOWN#";

    private SearchFiles() {
    }

    public static void main(String[] args) throws Exception {

        String usage = "Usage: [algorithm --> BM25, LMLaplace, RM1 or RM3]\n" +
                       "       [Index Files --> Absolute Path to index folder]\n" +
                       "       [Queries --> Absolute Path to queries file]\n" +
                       "       [Results --> Absolute Path to file where you want to have the results saved]\n";

        if(args.length < 4){
            System.out.println(usage);
            System.exit(0);
        }

        String algorithm = args[0];
        String index = args[1];
        String queries = args[2];
        String result = args[3];

        String[] resul_split = result.split("\\.");

        result = resul_split[0] + "_" + algorithm + "." + resul_split[1];

        String field = DocField.TEXT;
        int numRetrievedDocs = 1000;
        String userId = "lxu85 & tpsanto";
        int rmK = 25, rmN = 50;  // Params for RM
        double lambda = 0.8;  // Param for RM3

        assert algorithm.equalsIgnoreCase("BM25") || algorithm.equalsIgnoreCase("RM1")
                || algorithm.equalsIgnoreCase("RM3") || algorithm.equalsIgnoreCase("LMLaplace");

        // Get queries
        ArrayList<String> queryList = parseQueries(Paths.get(queries));
        if(debug)
            System.out.printf("Total %d queries\n\n", queryList.size());

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        if(!algorithm.equalsIgnoreCase("BM25"))
            searcher.setSimilarity(new LMDirichletSimilarity());
        Analyzer analyzer = new StandardAnalyzer();
        QueryParser parser = new QueryParser(field, analyzer);

        // Search for each query
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < queryList.size(); ++i) {
            Query query = parser.parse(QueryParser.escape(queryList.get(i)));
            System.out.println("Searching for: " + query.toString(field));

            TopDocs topDocs = doSearch(searcher, query, numRetrievedDocs);  // BM25
            if(algorithm.equalsIgnoreCase("RM1"))
                topDocs.scoreDocs = reRank(reader, query, topDocs, rmK, rmN);  // RM1
            else if(algorithm.equalsIgnoreCase("RM3"))
                topDocs.scoreDocs = reRank(reader, query, topDocs, rmK, rmN, lambda);  // RM3
//             String newQueryStr = reRank(reader, query, topDocs, rmK , rmN, lambda);
//             Query newQuery = parser.parse(QueryParser.escape(newQueryStr));
//             topDocs = doSearch(searcher, newQuery, numRetrievedDocs);  // For debug
            printTopDocs(sb, searcher, topDocs, i + 351, userId);
        }
        reader.close();

        // Save results
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(result));
        writer.write(sb.toString());
        writer.close();
        System.out.println("Done");
    }

    /**
     * RM1
     * @param k : use top k documents for expansion
     * @param n : use top n terms for expansion
     * @return
     */
    public static ScoreDoc[] reRank(IndexReader reader, Query query, TopDocs topDocs, int k, int n) throws Exception{
        return reRank(reader, query, topDocs, k, n, -1);
    }

    /**
     * RM3
     * @param lambda: > 0 for RM3 interpolation
     */
    public static ScoreDoc[] reRank(IndexReader reader, Query query, TopDocs topDocs, int k, int n, double lambda) throws Exception{
        ScoreDoc[] hits = topDocs.scoreDocs;
        assert k <= hits.length;
        String queryText = query.toString();
        Map<String, HashMap<Integer , Double>> termProbMap = new HashMap<>();  // (term, docId) -> prob

        // Build up termProbMap
        for(int i = 0; i < hits.length; ++i) {
            Integer docId = hits[i].doc;
            Terms termVec = reader.getTermVector(hits[i].doc, DocField.TEXT);
            long docLen = termVec.getSumTotalTermFreq();
            long numUniqueTokens = termVec.size();

            TermsEnum terms = termVec.iterator();
            BytesRef term = null;
            while((term = terms.next()) != null) {
                String termText = term.utf8ToString();
                termProbMap.putIfAbsent(termText, new HashMap<>());

                PostingsEnum posting = terms.postings(null, PostingsEnum.FREQS);
                posting.nextDoc();
                int termFreq = posting.freq();
                double termProb = (termFreq + 1.0) / (docLen + numUniqueTokens + 1);  // Term Prob
                termProbMap.get(termText).put(docId, termProb);
                if(debug)
                    System.out.println(String.format("term: %s, docLen: %d, numUniqueTokens: %d, termFreq: %d; termProb: %e",
                            termText, docLen, numUniqueTokens, termFreq, termProb));
            }
            double unknownTokenProb = 1.0 / (docLen + numUniqueTokens + 1);
            // Add prob for unknown token for smoothing
            termProbMap.putIfAbsent(unkownToken, new HashMap<>());
            termProbMap.get(unkownToken).put(docId, unknownTokenProb);
        }

        // Calculate p(q|D)
        String[] queryTerms = queryText.split("\\s+");
        for(int i = 0; i < queryTerms.length; ++i)
            queryTerms[i] = queryTerms[i].split(":")[1];
        Map<Integer, Double> queryProbMap = getQueryProbMap(queryTerms, termProbMap, k, topDocs);

        // Re-weight term per document
        Map<String, HashMap<Integer , Double>> reweightedTermProbMap = new HashMap<>();
        for(int i = 0; i < k; ++i) {
            Integer docId = hits[i].doc;
            Terms termVec = reader.getTermVector(hits[i].doc, DocField.TEXT);
            TermsEnum terms = termVec.iterator();
            BytesRef term = null;
            while((term = terms.next()) != null) {
                String termText = term.utf8ToString();
                reweightedTermProbMap.putIfAbsent(termText, new HashMap<>());

                double termProb = termProbMap.get(termText).get(docId);
                double newTermProb = termProb * queryProbMap.get(docId);
                reweightedTermProbMap.get(termText).put(docId, newTermProb);
                if(debug)
                    System.out.println(String.format("term: %s, newTermProb: %e", termText, newTermProb));
            }
            double termProb = termProbMap.get(unkownToken).get(docId);
            double newTermProb = termProb * queryProbMap.get(docId);
            reweightedTermProbMap.putIfAbsent(unkownToken, new HashMap<>());
            reweightedTermProbMap.get(unkownToken).put(docId, newTermProb);
        }

        // Get term prob across documents
        Map<String, Double> termProbAcrossDocMap = new HashMap<>();
        reweightedTermProbMap.forEach((termText, docMap) -> {
            try {
                double termProbAcrossDoc = 0;
                for(int i = 0; i < k; ++i) {
                    Integer docId = hits[i].doc;
                    // Use unknown token prob if this query term not exists in current doc
                    termProbAcrossDoc += docMap.getOrDefault(docId, reweightedTermProbMap.get(unkownToken).get(docId));
                }
                termProbAcrossDocMap.put(termText, termProbAcrossDoc);
                if(debug)
                    System.out.println(String.format("term: %s, termProbAcrossDoc: %e", termText, termProbAcrossDoc));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Normalize termProbAcrossDoc
        Map<String, Double> normalizedTermProbMap = new HashMap<>();
        double demoninator = 0;
        for(double prob: termProbAcrossDocMap.values())
            demoninator += prob;
        for(String termText: termProbAcrossDocMap.keySet())
            normalizedTermProbMap.put(termText, termProbAcrossDocMap.get(termText) / demoninator);

        // Interpolation - RM3
        Map<String, Double> originalQueryLM = new HashMap<>();
        if(lambda > 0) {
            int queryLen = queryTerms.length;
            Map<String, Integer> queryTermFreq = new HashMap<>();
            Arrays.stream(queryTerms).forEach(queryTerm -> {
                queryTermFreq.put(queryTerm, queryTermFreq.getOrDefault(queryTerm, 0) + 1);
            });

            queryTermFreq.entrySet().forEach(entry -> {
                originalQueryLM.put(entry.getKey(), entry.getValue() / (double)queryLen);  // No smoothing here
            });

            double coefMle = 1 - lambda, coefRm1 = lambda;
            originalQueryLM.entrySet().forEach(entry -> {
                double newProb = coefMle * entry.getValue() + coefRm1 * normalizedTermProbMap.getOrDefault(entry.getKey(), normalizedTermProbMap.get(unkownToken));
                normalizedTermProbMap.put(entry.getKey(), newProb);
            });
        }
        // End of Interpolation - RM3

        // Get n highest prob terms
        List<String> sorted = normalizedTermProbMap.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(n)
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());

        // Expand query
        String[] newQueryTerms = Stream.concat(Arrays.stream(queryTerms), sorted.stream())
                .distinct()
                .toArray(String[]::new);
        if(debug)
            System.out.println("New query: " + Arrays.toString(newQueryTerms));

        // Recalculate queryProb
        Map<Integer, Double> newQueryProbMap = getQueryProbMap(newQueryTerms, termProbMap, topDocs.scoreDocs.length, topDocs);

        // Sort docs by new queryProb
        int numQueryTerms = newQueryTerms.length;
        ScoreDoc[] rankedDocs = newQueryProbMap.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .map(entry -> {
                    float newProb = (float)Math.pow(entry.getValue(), 1.0 / numQueryTerms) ;  // Normalize prob
                    // System.out.println("newProb: " + newProb);
                    return new ScoreDoc(entry.getKey(), newProb);})
                .toArray(ScoreDoc[]::new);
        //return Arrays.toString(newQueryTerms);
        return rankedDocs;
    }

    private static Map<Integer, Double> getQueryProbMap(String[] queryTerms, Map<String, HashMap<Integer , Double>> termProbMap,
                                                       int k, TopDocs topDocs) throws IOException {
        Map<Integer, Double> queryProbMap = new HashMap<>();
        ScoreDoc[] hits = topDocs.scoreDocs;
        for(int i = 0; i < k; ++i) {
            double queryProb = 1;
            Integer docId = hits[i].doc;

            for(String queryTerm: queryTerms) {
                double termProb = 1;
                // Use unknown token prob if this query term not exists in current doc
                try {
                    termProb = termProbMap.get(queryTerm).get(docId);
                } catch (Exception e) {
                    termProb = termProbMap.get(unkownToken).get(docId);
                }
                queryProb *= termProb;
            }
            queryProbMap.put(docId, queryProb);
        }
        return queryProbMap;
    }

    public static TopDocs doSearch(IndexSearcher searcher, Query query, int numRetrievedDocs) throws IOException {
        TopDocs topDocs = searcher.search(query, numRetrievedDocs);

        int numTotalHits = Math.toIntExact(topDocs.totalHits.value);
        // System.out.println(numTotalHits + " total matching documents");

        return topDocs;
    }

    public static void printTopDocs(StringBuilder sb, IndexSearcher searcher, TopDocs topDocs, int queryId, String userId) throws Exception {
        ScoreDoc[] hits = topDocs.scoreDocs;
        for(int i = 0; i < hits.length; ++i) {
            String docNo = searcher.doc(hits[i].doc).get(DocField.DOC_NO);
            String str = String.format("%d \t Q0 \t %s \t %d \t %.4f \t %s\n", queryId, docNo, i + 1, hits[i].score, userId);
            // System.out.print(str);
            sb.append(str);
        }
    }

    public static ArrayList<String> parseQueries(Path file) {
        ArrayList<String> queries = new ArrayList<>();
        try(BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if(!line.startsWith("<top>"))
                    continue;
                // New topic/query
                line = reader.readLine();
                line = reader.readLine();
                line = reader.readLine();
                assert line.startsWith("<title>"): "Wrong parsing for title";
                String title = line.substring(8, line.length()).trim();

                line = reader.readLine();
                line = reader.readLine();
                assert line.startsWith("<desc>"): "Wrong parsing for description";
                StringBuilder sb = new StringBuilder(reader.readLine());
                while (true) {
                    line = reader.readLine();
                    if(line.trim().isEmpty() || line.startsWith("<"))
                        break;
                    else
                        sb.append(line + " ");
                }
                String description = sb.toString();

                String query = title + " " + description;
                queries.add(query);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return queries;
    }
}

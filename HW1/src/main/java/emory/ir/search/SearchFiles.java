package emory.ir.search;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import emory.ir.LMLaplace;
import emory.ir.index.DocField;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMSimilarity;
import org.apache.lucene.store.FSDirectory;

public class SearchFiles {

    private SearchFiles() {
    }

    public static void main(String[] args) throws Exception {
        String usage =
                "Usage:\t[-index dir] [-field f] [-queries file] [-query string] [-result string] [-num_retrieved_docs int]\n\n";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = null;
        String field = DocField.TEXT;
        String queries = null;
        String queryString = null;
        int numRetrievedDocs = 1000;
        String userId = "emory";
        String result = null;

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            } else if ("-field".equals(args[i])) {
                field = args[i + 1];
                i++;
            } else if ("-queries".equals(args[i])) {
                queries = args[i + 1];
                i++;
            } else if ("-query".equals(args[i])) {
                queryString = args[i + 1];
                i++;
            } else if ("-result".equals(args[i])) {
                result = args[i + 1];
                i++;
            } else if ("-num_retrieved_docs".equals(args[i])) {
                numRetrievedDocs = Integer.parseInt(args[i + 1]);
                i++;
            }
        }

        // Get queries
        ArrayList<String> queryList = null;
        if(queries != null)
            queryList = parseQueries(Paths.get(queries));
        else if(queryString != null) {
            queryList = new ArrayList<>();
            queryList.add(queryString);
        }
        // System.out.printf("Total %d queries\n\n", queryList.size());

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        // searcher.setSimilarity(new LMDirichletSimilarity());
        Analyzer analyzer = new StandardAnalyzer();
        QueryParser parser = new QueryParser(field, analyzer);

        // Search for each query
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < queryList.size(); ++i) {
            Query query = parser.parse(QueryParser.escape(queryList.get(i)));
            System.out.println("Searching for: " + query.toString(field));

            TopDocs topDocs = doSearch(searcher, query, numRetrievedDocs);
            printTopDocs(sb, searcher, topDocs, i + 351, userId);
        }
        reader.close();

        // Save results
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(result));
        writer.write(sb.toString());
        writer.close();
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
            String str = String.format("%d \t Q0 \t %s \t %d \t %.1f \t %s\n", queryId, docNo, i + 1, hits[i].score, userId);
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
                        sb.append(line);
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

package emory.ir.index;

import emory.ir.search.Util;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Date;


public class IndexFiles {

    private IndexFiles() {
    }

    /**
     * Index all text files under a directory.
     */
    public static void run(String[] args) {
        String usage = "Usage: indexing\n" +
                "       [Similarity --> BM25 or LM]\n" +
                "       [Doc Dir --> Absolute Path to data folder]\n" +
                "       [Index Dir --> Absolute Path to index folder to be stored]";

        if(args.length < 4 || !args[0].equalsIgnoreCase("indexing")
                || (!args[1].equalsIgnoreCase("BM25") && !args[1].equalsIgnoreCase("LM"))){
            System.out.println(usage);
            System.exit(0);
        }

        String similarity = args[1];
        String docsPath = args[2];
        String indexPath = args[3];
        boolean create = true;

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer(new CharArraySet(Arrays.asList(Util.getStopWords()), true));
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            if(similarity.equalsIgnoreCase("LM"))
                iwc.setSimilarity(new LMDirichletSimilarity());

            if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexFiles(writer, docDir);

            writer.close();

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        } catch (Exception e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
    }

    public static void indexFiles(final IndexWriter writer, Path path) throws Exception {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        indexDocs(writer, file);
                    } catch (Exception e) {
                        // e.printStackTrace();
                        System.out.println("Skip");
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else
            indexDocs(writer, path);
    }

    public static void indexDocs(IndexWriter writer, Path file) throws Exception {
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        // Ignore non-document file
        String filename = file.getFileName().toString();
        if(filename.endsWith(".z") || filename.contains("read") || (!filename.contains("fb") && !filename.contains("ft") && !filename.contains("la"))) {
            System.out.println("Ignore file: " + file);
            return;
        }

        try(BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // Read each line to identify each document
            String line = null;
            while((line = reader.readLine()) != null) {
                if(line.trim().isEmpty())
                    continue;

                if(line.trim().equals("<DOC>")) {
                    // New document
                    StringBuilder sb = new StringBuilder();
                    sb.append(line);
                    while(true) {
                        line = reader.readLine();
                        if(line.contains("<F P=") || line.contains("</F>") || line.contains("<FIG") || line.contains("</FIG>"))  // Filter out invalid format
                            continue;
                        sb.append(line);
                        if(line.trim().equals("</DOC>")) {
                            // Parse the document
                            String docStr = sb.toString();
                            InputStream docStream = new ByteArrayInputStream(docStr.getBytes(StandardCharsets.UTF_8));
                            org.w3c.dom.Document docDom = db.parse(docStream);

                            // Index document
                            Document doc = indexDoc(docDom);
                            if(doc == null) {
                                System.out.println("Skip: no DOCNO or TEXT in document");
                                break;
                            }

                            // Write document
                            if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                                System.out.println("adding " + doc.get(DocField.DOC_NO));
                                writer.addDocument(doc);
                            } else {
                                System.out.println("updating " + doc.get(DocField.DOC_NO));
                                writer.updateDocument(new Term("path", file.toString()), doc);
                            }
                            break;
                        }
                    }
                } else
                    throw new RuntimeException("Invalid doc start tag: " + file);
            }
        }
    }

    private static Document indexDoc(org.w3c.dom.Document docDom) {
        Document doc = new Document();

        String docNo = null, text = null;

        Node node = docDom.getElementsByTagName("DOCNO").item(0);
        if(node != null)
            docNo = node.getTextContent().trim();

        node = docDom.getElementsByTagName("TEXT").item(0);
        if(node != null)
            text = node.getTextContent().trim();

        if(docNo == null || text == null)
            return null;

        FieldType type = new FieldType();
        type.setStored(false);
        type.setTokenized(true);
        type.setStoreTermVectors(true);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        Field textField = new Field(DocField.TEXT, text, type);

        Field docIdField = new StringField(DocField.DOC_NO, docNo, Field.Store.YES);

        doc.add(docIdField);
        doc.add(textField);

        return doc;
    }
}

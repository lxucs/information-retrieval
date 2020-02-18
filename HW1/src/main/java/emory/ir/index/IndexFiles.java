package emory.ir.index;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Node;

/**
 * Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {

    private IndexFiles() {
    }

    /**
     * Index all text files under a directory.
     */
    public static void main(String[] args) {
        String usage = "java org.apache.lucene.demo.IndexFiles"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles";
        String indexPath = "index";
        String docsPath = null;
        boolean create = true;
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i + 1];
                i++;
            } else if ("-docs".equals(args[i])) {
                docsPath = args[i + 1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            }
        }

        if (docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

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
                        e.printStackTrace();
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

        try(InputStream stream = Files.newInputStream(file)) {
            // Read each line to identify each document
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
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
                        if(line.contains("<F P=") || line.contains("</F>"))
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

        Field docIdField = new StringField(DocField.DOC_NO, docNo, Field.Store.YES);
        Field textField = new TextField(DocField.TEXT, text, Field.Store.NO);

        doc.add(docIdField);
        doc.add(textField);

        return doc;
    }
}

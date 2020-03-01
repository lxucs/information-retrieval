# Homework 1 - Information Retrieval

This repository is for the implementation of 4 different ranking methods using Lucene API. More explanation and details can be found below and in our code itself.

Students: Liyan Xu (lxu85) and Thiago Santos (tpsanto)

# How to run our code

1.  Indexing: `java -jar HW1.jar indexing [similarity] [doc-dir] [index-dir]`

    * [similarity]: BM25 or LM
	* [doc-dir]: absolute path to the data directory
	* [index-dir]: absolute path to the index directory for storing index files
		
	Examples:
	* Baseline: `java -jar HW1.jar indexing BM25 /home/docs /home/index_bm25`
	* LM-based: `java -jar HW1.jar indexing LM /home/docs /home/index_lm`

2. Searching: `java -jar HW1.jar [algorithm] [index-dir] [query-path] [result-path]`

    * [algorithm]: BM25, LMLaplace, RM1 or RM3
    * [index-dir]: absolute path to index dir
    * [query-path]: absolute path to query file
    * [result-path]: absolute path to save result
		
	Examples:
	* Baseline: `java -jar HW1.jar BM25 /home/index_bm25 /home/query.txt /home/result.txt`
	* RM3: `java -jar HW1.jar RM3 /home/index_lm /home/query.txt /home/result.txt` 

# Ranking Methods Implementation

The project uses Maven as build tool. See the configuration in pom file.

We remove stop words in both indexing phase and searching phase.

Top 1000 documents are retrieved.

### Parsing:
* Doc file parsing: `IndexFiles.indexDocs()` is responsible for doc file parsing. We read the file line by line and segment each documents. We treat each documents as XML file and perform some cleaning because of some format issue (I raised format issue question on Canvas and solved it myself).
* Query file parsing: `SearchFiles.parseQueries()` is responsible for query file parsing.

### BM25
This is the most basic model, similar to the demo provided by Lucene. It's a very strong baseline and we got reasonable results.

### LM similarity model
This is also a very basic model, with a small change. We decide to use Direchlet Similarity in our Language Model. It can be done with this simple line of code:
* `iwc.setSimilarity(new LMDirichletSimilarity());`
* `searcher.setSimilarity(new LMDirichletSimilarity());`

Extra credits?

We also tried to implement our own LMD Laplace Similarity Class, as it was assigned in the beginning of the homework. Our solution can be found in the class `LMLaplace`.

### RM1
We follow the process described in the homework, and perform re-ranking after we expand the query.
1. The initial search is done by `SearchFiles.doSearch()`. `SearchFiles.rerank()` is responsible for the entire re-ranking process.
2. Re-ranking is slow, because we implement the LM probability with Dirichlet Smoothing ourselves using stats (e.g. collection probability, term freq, etc.) obtained from various APIs. We build a complete term probability map with Dirichlet Smoothing in the `LMDirichletProbability` class.
3. We select the top 70 new-weighted terms and expand to the query.
4. The re-ranking is simply to sort by the probability of generating the new query for each document.
5. Just to see the probability better, we apply a normalization to the final query probability based on query length. It doesn't affect the final ranking and we can see the final scores better.

### RM3
It is a simple interpolation between original query LM and the RM1 LM. `lambda` is used to control the interpolation coefficient.

# Findings - Extra Credits?
In the class I brought up the issue of re-ranking vs. re-query. The initial finding is that re-ranking is way worse than the re-query. However, later we found that it is because we used Laplace Smoothing in the re-ranking, which doesn't consider the global prior probability. Then we implemented the Dirichlet Smoothing and the performance is better. The conclusion is that we need to match the smoothing method in both initial search phase and the re-ranking phase to keep the performance.

In our empirical results, RM3 shows consistent improvement over the BM25 baseline and the out-of-the-box LM similarity.

Let me know if you have any questions!

Baseline (BM25):
`BM25:
 num_q          	all	50
 num_ret        	all	50000
 num_rel        	all	4674
 num_rel_ret    	all	1702
 map            	all	0.1236
 gm_ap          	all	0.0658
 R-prec         	all	0.1900
 bpref          	all	0.1615
 recip_rank     	all	0.6426
 ircl_prn.0.00  	all	0.6835
 ircl_prn.0.10  	all	0.3384
 ircl_prn.0.20  	all	0.2467
 ircl_prn.0.30  	all	0.1741
 ircl_prn.0.40  	all	0.1056
 ircl_prn.0.50  	all	0.0588
 ircl_prn.0.60  	all	0.0347
 ircl_prn.0.70  	all	0.0176
 ircl_prn.0.80  	all	0.0044
 ircl_prn.0.90  	all	0.0026
 ircl_prn.1.00  	all	0.0000
 P5             	all	0.3960
 P10            	all	0.3400
 P15            	all	0.3267
 P20            	all	0.3000
 P30            	all	0.2607
 P100           	all	0.1504
 P200           	all	0.1001
 P500           	all	0.0560
 P1000          	all	0.0340`

Dirichlet LM Similarity:
`num_q          	all	50
 num_ret        	all	50000
 num_rel        	all	4674
 num_rel_ret    	all	1696
 map            	all	0.1323
 gm_ap          	all	0.0674
 R-prec         	all	0.1906
 bpref          	all	0.1694
 recip_rank     	all	0.6285
 ircl_prn.0.00  	all	0.6713
 ircl_prn.0.10  	all	0.3576
 ircl_prn.0.20  	all	0.2514
 ircl_prn.0.30  	all	0.1718
 ircl_prn.0.40  	all	0.1238
 ircl_prn.0.50  	all	0.0802
 ircl_prn.0.60  	all	0.0558
 ircl_prn.0.70  	all	0.0320
 ircl_prn.0.80  	all	0.0060
 ircl_prn.0.90  	all	0.0024
 ircl_prn.1.00  	all	0.0000
 P5             	all	0.4120
 P10            	all	0.3560
 P15            	all	0.3160
 P20            	all	0.3010
 P30            	all	0.2620
 P100           	all	0.1510
 P200           	all	0.1020
 P500           	all	0.0544
 P1000          	all	0.0339`
 
 RM3:
 `num_q          	all	50
  num_ret        	all	50000
  num_rel        	all	4674
  num_rel_ret    	all	1696
  map            	all	0.1391
  gm_ap          	all	0.0703
  R-prec         	all	0.2034
  bpref          	all	0.1753
  recip_rank     	all	0.6401
  ircl_prn.0.00  	all	0.6836
  ircl_prn.0.10  	all	0.3691
  ircl_prn.0.20  	all	0.2694
  ircl_prn.0.30  	all	0.1852
  ircl_prn.0.40  	all	0.1302
  ircl_prn.0.50  	all	0.0817
  ircl_prn.0.60  	all	0.0558
  ircl_prn.0.70  	all	0.0330
  ircl_prn.0.80  	all	0.0058
  ircl_prn.0.90  	all	0.0020
  ircl_prn.1.00  	all	0.0000
  P5             	all	0.4120
  P10            	all	0.3660
  P15            	all	0.3347
  P20            	all	0.3090
  P30            	all	0.2680
  P100           	all	0.1580
  P200           	all	0.1070
  P500           	all	0.0581
  P1000          	all	0.0339`
 
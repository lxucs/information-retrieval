# Homework 1 - Information Retrieval
	This repository is for a implementation of 4 different ranking methods using Lucene API. More explanation and details can be found below and in our code itself.

	Students: Lyan Xu(lxu85) and Thiago Santos(tpsanto)

# How to run our code

	1) Indexing
		For indexing, you need to provide the index folder of where to save the index files and also the documents folder, where all documents must be. 
		Example of how to provide arguments:
		-index /local/home/IR_data/index -docs /local/home/IR_data/text

	2) Searching
		For searching, you need to provide 4 arguments.
			1) Algorithm to be used. BM25, LMLaplace, RM1 or RM3
			2) Index Files --> Absolute Path to index folder
			3) Queries --> Absolute Path to queries file
			4) Results --> Absolute Path to file where you want to have the results saved
		Example of how to provide arguments:
		RM3 /local/home/IR_data/index /local/home/IR_data/eval/topics.351-400 /local/home/IR_data/results/results.txt 

		All the output will be saved in "Path Results" + "_algorithm" + ".extension"


# Ranking Methods  Implementation

	1) BM25
		This is the most basic model, without any modification from the demo provided by Lucene. It's a very strong baseline and we got reasonable results(More details on the results section)

	2) LM similarity model
		This is also a very basic model, with a small change. We decide to use Direchlet Similarity in our Language Model. It can be done with this simple line of code:

		 * searcher.setSimilarity(new LMDirichletSimilarity());

		## Extra Credits ## ?

		We also tried to implement our own LMD Laplace Similarity Class, as it was assigned in the begining of the homework. Our solution can be found in the class LMLaplace

	3) RM1
		This is the most "complex" part of the homework. Our solution is implemented in method reRank() and it can be found in Class SearchFiles. Code is provided with comments for better understanding. 
		The whole idea is that we go over all documents and all terms in the document and calculate the term probability. We also add Laplace Smothing so that we don't get zero probabilities. 

		Once that's done, we calculate p(q|D) and Re-weight term per document by getting the terms probability across all documents. We only take in consideration up to K documents and N terms. We are using K=30 and N=60

		At the end, after normalization, we then get the N highest probability terms and expend our query with those new terms. 

		As next step, with our expended query, we Recalculate our query probability. This is our Re-Raking part(More Details in the Re-Ranking section).

		Finally, we sort documents by the new query probabilities(Re-ranked).


	4) RM3
		It's very similar to RM1. The main diference now is that we don't want to just compute the probability of all terms and simply do a re-ranking by a dot product across terms. We want to give, in some how, higher weights to the original terms, from the original/initial language model.
		This is done by using a fine tuned Lambda. In our case, we're using Lambda=0.8
		Our implementation is in the same method reRank() and can be found between the comments/tags:
		// Interpolation - RM3  ......... // End of Interpolation - RM3

	5) Re-Ranking
		Our implementation can be found in method getQueryProbMap() in Class SearchFiles. It's a very simple and naive Re-Rank aproach, where we do a dot product across terms. The Formula is the follow:
			P(Q*|D,R) --> A4 (Product of P(t | D, R) )

# Results and Findings - Extra Credits ?
	Hyperparameters --> K=25, N=50 and Lambda=0.8
	Results are in the following sequence of magnitude:
	BM25 > LMDirichletSimilarity > RM3 > RM1

	BM25: num_q: 50, num_ret: 50000, num_rel: 4674, num_rel_ret: 1387, map: 0.0866, gm_ap: 0.0358, R-prec: 0.1475 bpref: 0.1293
	LMDirichlet: num_q: 50 num_ret: 50000 num_rel: 4674 num_rel_ret: 1361 map: 0.0841, gm_ap: 0.0355, R-prec: 0.1411 bpref: 0.1279
	RM3: num_q: 50 num_ret: 50000 num_rel: 4674 num_rel_ret: 1361 map: 0.0273, gm_ap: 0.0085, R-prec: 0.057 bpref: 0.0666
	RM1: num_q: 50 num_ret: 50000 num_rel: 4674 num_rel_ret: 1361 map: 0.0269, gm_ap: 0.0082, R-prec: 0.0550 bpref: 0.0658

	As expected, BM25 got really solid results, since it's a very solid baseline. Also, as expected, RM3 got better results than RM1, since it gives some consideration to the original Langue Model. 

	Findings

	However, with the expended query, our theory was that RM1 and RM3 should have got results not to far from the baseline, for better or worst. To expand our theory, we used the expended query and executed a new search by passing this new query. As we initially expected, we got better results than the baseline.
	With a new Search we get:
	RM3: num_q: 50 num_ret: 50000 num_rel: 4674 num_rel_ret: 1125 map: 0.0658, gm_ap: 0.0120, R-prec: 0.1010 bpref: 0.1206

	This suggest to us that our approach for Re-Ranking is not really efficient and the best approach. 

	Continue.... 





/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.search.function.valuesource;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.QueryContext;
import org.apache.solr.search.function.FuncValues;

import java.io.IOException;

/**
 * Function that returns {@link TFIDFSimilarity #idf(long, long)}
 * for every document.
 * <p/>
 * Note that the configured Similarity for the field must be
 * a subclass of {@link TFIDFSimilarity}
 *
 * @lucene.internal
 */
public class IDFValueSource extends DocFreqValueSource {
  public IDFValueSource(String field, String val, String indexedField, BytesRef indexedBytes) {
    super(field, val, indexedField, indexedBytes);
  }

  @Override
  public String name() {
    return "idf";
  }

  @Override
  public void createWeight(QueryContext context) throws IOException {
    IndexSearcher searcher = context.indexSearcher();
    TFIDFSimilarity sim = asTFIDF(searcher.getSimilarity(), field);
    if (sim == null) {
      throw new UnsupportedOperationException("requires a TFIDFSimilarity (such as DefaultSimilarity)");
    }
    int docfreq = searcher.getIndexReader().docFreq(new Term(indexedField, indexedBytes));
    float idf = sim.idf(docfreq, searcher.getIndexReader().maxDoc());
    FuncValues result = new ConstDoubleDocValues(idf, this);
    context.put(this, result);
  }

  @Override
  public FuncValues getValues(QueryContext context, AtomicReaderContext readerContext) throws IOException {
    return (FuncValues) context.get(this);
  }

  // tries extra hard to cast the sim to TFIDFSimilarity
  static TFIDFSimilarity asTFIDF(Similarity sim, String field) {
    while (sim instanceof PerFieldSimilarityWrapper) {
      sim = ((PerFieldSimilarityWrapper) sim).get(field);
    }
    if (sim instanceof TFIDFSimilarity) {
      return (TFIDFSimilarity) sim;
    } else {
      return null;
    }
  }
}


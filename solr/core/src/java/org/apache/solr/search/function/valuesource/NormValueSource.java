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
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.solr.search.QueryContext;
import org.apache.solr.search.function.FuncValues;
import org.apache.solr.search.function.ValueSource;
import org.apache.solr.search.function.funcvalues.FloatFuncValues;

import java.io.IOException;

/**
 * Function that returns {@link TFIDFSimilarity#decodeNormValue(long)}
 * for every document.
 * <p/>
 * Note that the configured Similarity for the field must be
 * a subclass of {@link TFIDFSimilarity}
 *
 * @lucene.internal
 */
public class NormValueSource extends ValueSource {
  protected final String field;

  public NormValueSource(String field) {
    this.field = field;
  }

  public String name() {
    return "norm";
  }

  @Override
  public String description() {
    return name() + '(' + field + ')';
  }

  @Override
  public FuncValues getValues(QueryContext context, AtomicReaderContext readerContext) throws IOException {
    IndexSearcher searcher = context.indexSearcher();
    final TFIDFSimilarity similarity = IDFValueSource.asTFIDF(searcher.getSimilarity(), field);
    if (similarity == null) {
      throw new UnsupportedOperationException("requires a TFIDFSimilarity (such as DefaultSimilarity)");
    }
    final NumericDocValues norms = readerContext.reader().getNormValues(field);

    if (norms == null) {
      return new ConstDoubleDocValues(0.0, this);
    }

    return new FloatFuncValues(this) {
      @Override
      public float floatVal(int doc) {
        return similarity.decodeNormValue((byte) norms.get(doc));
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this.getClass() != o.getClass()) return false;
    return this.field.equals(((NormValueSource) o).field);
  }

  @Override
  public int hashCode() {
    return this.getClass().hashCode() + field.hashCode();
  }
}



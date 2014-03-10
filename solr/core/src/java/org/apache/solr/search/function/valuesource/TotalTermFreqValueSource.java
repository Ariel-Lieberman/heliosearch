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
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.QueryContext;
import org.apache.solr.search.function.FuncValues;
import org.apache.solr.search.function.ValueSource;
import org.apache.solr.search.function.funcvalues.LongDocValues;

import java.io.IOException;

/**
 * <code>TotalTermFreqValueSource</code> returns the total term freq
 * (sum of term freqs across all documents).
 * Returns -1 if frequencies were omitted for the field, or if
 * the codec doesn't support this statistic.
 *
 * @lucene.internal
 */
public class TotalTermFreqValueSource extends ValueSource {
  protected final String field;
  protected final String indexedField;
  protected final String val;
  protected final BytesRef indexedBytes;

  public TotalTermFreqValueSource(String field, String val, String indexedField, BytesRef indexedBytes) {
    this.field = field;
    this.val = val;
    this.indexedField = indexedField;
    this.indexedBytes = indexedBytes;
  }

  public String name() {
    return "totaltermfreq";
  }

  @Override
  public String description() {
    return name() + '(' + field + ',' + val + ')';
  }

  @Override
  public FuncValues getValues(QueryContext context, AtomicReaderContext readerContext) throws IOException {
    return (FuncValues) context.get(this);
  }

  @Override
  public void createWeight(QueryContext context) throws IOException {
    long totalTermFreq = 0;
    for (AtomicReaderContext readerContext : context.indexSearcher().getTopReaderContext().leaves()) {
      long val = readerContext.reader().totalTermFreq(new Term(indexedField, indexedBytes));
      if (val == -1) {
        totalTermFreq = -1;
        break;
      } else {
        totalTermFreq += val;
      }
    }
    final long ttf = totalTermFreq;
    context.put(this, new LongDocValues(this) {
      @Override
      public long longVal(int doc) {
        return ttf;
      }
    });
  }

  @Override
  public int hashCode() {
    return getClass().hashCode() + indexedField.hashCode() * 29 + indexedBytes.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this.getClass() != o.getClass()) return false;
    TotalTermFreqValueSource other = (TotalTermFreqValueSource) o;
    return this.indexedField.equals(other.indexedField) && this.indexedBytes.equals(other.indexedBytes);
  }
}

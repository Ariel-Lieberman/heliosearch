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

package org.apache.solr.search;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.solr.search.function.FuncValues;
import org.apache.solr.search.function.ValueSourceScorer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.solr.search.function.ValueSourceRangeFilter;

import java.io.IOException;

// This class works as either a normal constant score query, or as a PostFilter using a collector
public class FunctionRangeQuery extends SolrConstantScoreQuery implements PostFilter {
  final ValueSourceRangeFilter rangeFilt;

  public FunctionRangeQuery(ValueSourceRangeFilter filter) {
    super(filter);
    this.rangeFilt = filter;
  }

  @Override
  public DelegatingCollector getFilterCollector(IndexSearcher searcher) {
    QueryContext fcontext = QueryContext.newContext(searcher);
    return new FunctionRangeCollector(fcontext);
  }

  class FunctionRangeCollector extends DelegatingCollector {
    final QueryContext fcontext;
    ValueSourceScorer scorer;
    int maxdoc;

    public FunctionRangeCollector(QueryContext fcontext) {
      this.fcontext = fcontext;
    }

    @Override
    public void collect(int doc) throws IOException {
      if (doc<maxdoc && scorer.matches(doc)) {
        delegate.collect(doc);
      }
    }

    @Override
    public void setNextReader(AtomicReaderContext context) throws IOException {
      maxdoc = context.reader().maxDoc();
      FuncValues dv = rangeFilt.getValueSource().getValues(fcontext, context);
      scorer = dv.getRangeScorer(context, rangeFilt.getLowerVal(), rangeFilt.getUpperVal(), rangeFilt.isIncludeLower(), rangeFilt.isIncludeUpper(), rangeFilt.isMatchMissing());
      super.setNextReader(context);
    }
  }
}

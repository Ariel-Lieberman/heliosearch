package org.apache.solr.search.facet;

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

import java.io.IOException;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QueryContext;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.function.FuncValues;
import org.apache.solr.search.function.ValueSource;
import org.apache.solr.search.mutable.MutableValueInt;


// abstract class or interface?
public abstract class AggValueSource extends ValueSource {
  protected String name;

  public AggValueSource(String name) {
    this.name = name;
  }

  public String name() {
    return this.name;
  }

  public ValueSource[] getChildren() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    return this.getClass() == o.getClass() && name.equals(((AggValueSource) o).name);
  }

  @Override
  public FuncValues getValues(QueryContext context, AtomicReaderContext readerContext) throws IOException {
    // FUTURE
    throw new UnsupportedOperationException("NOT IMPLEMENTED " + name + " " + this);
  }

  // TODO: make abstract
  public SlotAcc createSlotAcc(MutableValueInt slot, QueryContext qContext, SolrQueryRequest req, int numDocs, int numSlots) throws IOException {
    throw new UnsupportedOperationException("NOT IMPLEMENTED " + name + " " + this);
  }

}


package org.apache.solr.search.function.funcvalues;

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

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.solr.search.function.FuncValues;
import org.apache.solr.search.function.ValueSource;
import org.apache.solr.search.function.ValueSourceScorer;
import org.apache.solr.search.mutable.MutableValue;
import org.apache.solr.search.mutable.MutableValueDouble;

/**
 * Abstract {@link org.apache.solr.search.function.FuncValues} implementation which supports retrieving double values.
 * Implementations can control how the double values are loaded through {@link #doubleVal(int)}}
 */
public abstract class DoubleFuncValues extends FuncValues {
  protected final ValueSource vs;

  public DoubleFuncValues(ValueSource vs) {
    this.vs = vs;
  }

  @Override
  public float floatVal(int doc) {
    return (float) doubleVal(doc);
  }

  @Override
  public int intVal(int doc) {
    return (int) doubleVal(doc);
  }

  @Override
  public long longVal(int doc) {
    return (long) doubleVal(doc);
  }

  @Override
  public boolean boolVal(int doc) {
    return doubleVal(doc) != 0;
  }

  @Override
  public abstract double doubleVal(int doc);

  @Override
  public String strVal(int doc) {
    return Double.toString(doubleVal(doc));
  }

  @Override
  public Object objectVal(int doc) {
    return exists(doc) ? doubleVal(doc) : null;
  }

  @Override
  public String toString(int doc) {
    return vs.description() + '=' + strVal(doc);
  }

  @Override
  public ValueSourceScorer getRangeScorer(AtomicReaderContext readerContext, String lowerVal, String upperVal, boolean includeLower, boolean includeUpper, boolean matchMissing) {
    return getDoubleRangeScorer(this, readerContext, lowerVal, upperVal, includeLower, includeUpper,  matchMissing);
  }



  @Override
  public ValueFiller getValueFiller() {
    return new ValueFiller() {
      private final MutableValueDouble mval = new MutableValueDouble();

      @Override
      public MutableValue getValue() {
        return mval;
      }

      @Override
      public void fillValue(int doc) {
        mval.value = doubleVal(doc);
        mval.exists = exists(doc);
      }
    };
  }

}

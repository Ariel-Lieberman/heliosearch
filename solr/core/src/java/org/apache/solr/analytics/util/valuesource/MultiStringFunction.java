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

package org.apache.solr.analytics.util.valuesource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.solr.search.QueryContext;
import org.apache.solr.search.function.FuncValues;
import org.apache.solr.search.function.ValueSource;
import org.apache.solr.search.function.funcvalues.StrFuncValues;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.mutable.MutableValue;
import org.apache.solr.search.mutable.MutableValueStr;

/**
 * Abstract {@link ValueSource} implementation which wraps multiple ValueSources
 * and applies an extendible string function to their values.
 **/
public abstract class MultiStringFunction extends ValueSource {
  protected final ValueSource[] sources;
  
  public MultiStringFunction(ValueSource[] sources) {
    this.sources = sources;
  }

  abstract protected String name();
  abstract protected CharSequence func(int doc, FuncValues[] valsArr);

  @Override
  public String description() {
    StringBuilder sb = new StringBuilder();
    sb.append(name()).append('(');
    boolean firstTime=true;
    for (ValueSource source : sources) {
      if (firstTime) {
        firstTime=false;
      } else {
        sb.append(',');
      }
      sb.append(source);
    }
    sb.append(')');
    return sb.toString();
  }

  @Override
  public FuncValues getValues(QueryContext context, AtomicReaderContext readerContext) throws IOException {
    final FuncValues[] valsArr = new FuncValues[sources.length];
    for (int i=0; i<sources.length; i++) {
      valsArr[i] = sources[i].getValues(context, readerContext);
    }

    return new StrFuncValues(this) {
      @Override
      public String strVal(int doc) {
        CharSequence cs = func(doc, valsArr);
        return  cs != null ? cs.toString() : null;
      }
      
      @Override
      public boolean exists(int doc) {
        boolean exists = true;
        for (FuncValues val : valsArr) {
          exists = exists & val.exists(doc);
        }
        return exists;
      }
      
      @Override
      public boolean bytesVal(int doc, BytesRef bytes) {
        CharSequence cs = func(doc, valsArr);
        if( cs != null ){
          bytes.copyChars(func(doc,valsArr));
          return true;
        } else {
          bytes.bytes = BytesRef.EMPTY_BYTES;
          bytes.length = 0;
          bytes.offset = 0;
          return false;
        }
      }
      
      @Override
      public String toString(int doc) {
        StringBuilder sb = new StringBuilder();
        sb.append(name()).append('(');
        boolean firstTime=true;
        for (FuncValues vals : valsArr) {
          if (firstTime) {
            firstTime=false;
          } else {
            sb.append(',');
          }
          sb.append(vals.toString(doc));
        }
        sb.append(')');
        return sb.toString();
      }

      @Override
      public ValueFiller getValueFiller() {
        return new ValueFiller() {
          private final MutableValueStr mval = new MutableValueStr();

          @Override
          public MutableValue getValue() {
            return mval;
          }

          @Override
          public void fillValue(int doc) {
            mval.exists = bytesVal(doc, mval.value);
          }
        };
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (getClass() != o.getClass()) return false;
    MultiStringFunction other = (MultiStringFunction)o;
    return this.name().equals(other.name())
            && Arrays.equals(this.sources, other.sources);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(sources) + name().hashCode();
  }

}

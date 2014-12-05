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

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Future;


import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.search.BitsFilteredDocIdSet;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CloseHook;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.StrField;
import org.apache.solr.schema.TrieField;
import org.apache.solr.core.SolrCore;



import org.apache.lucene.search.Query;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.NumericDocValues;

import org.apache.lucene.util.BytesRef;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.util.plugin.SolrCoreAware;

/**
* syntax fq={!hash workers=11 worker=4 keys=field1,field2}
* */

public class HashQParserPlugin extends QParserPlugin {

  public static final String NAME = "hash";
  private static Semaphore semaphore = new Semaphore(8,true);
  private static ExecutorService threadPool = Executors.newCachedThreadPool();
  private static boolean init = true;

  private static synchronized void closeHook(SolrCore core) {
    if(init) {
      init = false;
      core.addCloseHook(new CloseHook() {
        @Override
        public void preClose(SolrCore core) {
          threadPool.shutdown();
          //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void postClose(SolrCore core) {
          //To change body of implemented methods use File | Settings | File Templates.
        }
      });
    }
  }

  public void init(NamedList params) {

  }

  public QParser createParser(String query, SolrParams localParams, SolrParams params, SolrQueryRequest request) {
    closeHook(request.getSearcher().getCore());
    return new HashQParser(query, localParams, params, request);
  }

  private class HashQParser extends QParser {

    public HashQParser(String query, SolrParams localParams, SolrParams params, SolrQueryRequest request) {
      super(query, localParams, params, request);
    }

    public Query parse() {
      int workers = localParams.getInt("workers");
      int worker = localParams.getInt("worker");
      String keys = params.get("partitionKeys");
      return new HashQuery(keys, workers, worker);
    }
  }

  private class HashQuery extends ExtendedQueryBase implements PostFilter {

    private String keysParam;
    private int workers;
    private int worker;

    public boolean getCache() {
      if(getCost() > 99) {
        return false;
      } else {
        return super.getCache();
      }
    }

    public int hashCode() {
      return keysParam.hashCode()+workers+worker+(int)getBoost();
    }

    public boolean equals(Object o) {
      if (o instanceof HashQuery) {
        HashQuery h = (HashQuery)o;
        if(keysParam.equals(h.keysParam) && workers == h.workers && worker == h.worker && getBoost() == h.getBoost()) {
          return true;
        }
      }

      return false;
    }

    public HashQuery(String keysParam, int workers, int worker) {
      this.keysParam = keysParam;
      this.workers = workers;
      this.worker = worker;
    }

    public Weight createWeight(IndexSearcher searcher) throws IOException {

      String[] keys = keysParam.split(",");
      SolrIndexSearcher solrIndexSearcher = (SolrIndexSearcher)searcher;
      IndexReaderContext context = solrIndexSearcher.getTopReaderContext();

      List<AtomicReaderContext> leaves =  context.leaves();

      ArrayBlockingQueue queue = new ArrayBlockingQueue(leaves.size());


      for(AtomicReaderContext leaf : leaves) {
        try {
          semaphore.acquire();
          SegmentPartitioner segmentPartitioner = new SegmentPartitioner(leaf,worker,workers, keys, solrIndexSearcher, queue,semaphore);
          threadPool.execute(segmentPartitioner);
        } catch(Exception e) {
          throw new IOException(e);
        }
      }

      FixedBitSet[] fixedBitSets = new FixedBitSet[leaves.size()];
      for(int i=0; i<leaves.size(); i++) {
        try {
          SegmentPartitioner segmentPartitioner = (SegmentPartitioner)queue.take();
          fixedBitSets[segmentPartitioner.context.ord] = segmentPartitioner.docs;
        }catch(Exception e) {
          throw new IOException(e);
        }
      }

      ConstantScoreQuery constantScoreQuery = new ConstantScoreQuery(new BitsFilter(fixedBitSets));
      return constantScoreQuery.createWeight(searcher);
    }

    public class BitsFilter extends Filter {
      private FixedBitSet[] bitSets;
      public BitsFilter(FixedBitSet[] bitSets) {
        this.bitSets = bitSets;
      }

      public DocIdSet getDocIdSet(AtomicReaderContext context, Bits bits) {
        return BitsFilteredDocIdSet.wrap(bitSets[context.ord], bits);
      }
    }


    class SegmentPartitioner implements Runnable {

      public AtomicReaderContext context;
      private int worker;
      private int workers;
      private HashKey k;
      private Semaphore sem;
      private ArrayBlockingQueue queue;
      public FixedBitSet docs;
      public SegmentPartitioner(AtomicReaderContext context,
                                int worker,
                                int workers,
                                String[] keys,
                                SolrIndexSearcher solrIndexSearcher,
                                ArrayBlockingQueue queue, Semaphore sem) {
        this.context = context;
        this.worker = worker;
        this.workers = workers;
        this.queue = queue;
        this.sem = sem;

        HashKey[] hashKeys = new HashKey[keys.length];
        IndexSchema schema = solrIndexSearcher.getSchema();
        for(int i=0; i<keys.length; i++) {
          String key = keys[i];
          FieldType ft = schema.getField(key).getType();
          HashKey h = null;
          if(ft instanceof StrField) {
            h = new BytesHash(key);
          } else {
            h = new NumericHash(key);
          }
          hashKeys[i] = h;
        }

        k = (hashKeys.length > 1) ? new CompositeHash(hashKeys) : hashKeys[0];
      }

      public void run() {
        AtomicReader reader = context.reader();
        try {
          k.setNextReader(context);
          this.docs = new FixedBitSet(reader.maxDoc());
          int maxDoc = reader.maxDoc();
          for(int i=0; i<maxDoc; i++) {
            if((k.hashCode(i) & 0x7FFFFFFF) % workers == worker) {
              docs.set(i);
            }
          }
        }catch(Exception e) {
         throw new RuntimeException(e);
        } finally {
          sem.release();
          queue.add(this);
        }
      }
    }

    public DelegatingCollector getFilterCollector(IndexSearcher indexSearcher) {
      String[] keys = keysParam.split(",");
      HashKey[] hashKeys = new HashKey[keys.length];
      SolrIndexSearcher searcher = (SolrIndexSearcher)indexSearcher;
      IndexSchema schema = searcher.getSchema();
      for(int i=0; i<keys.length; i++) {
        String key = keys[i];
        FieldType ft = schema.getField(key).getType();
        HashKey h = null;
        if(ft instanceof StrField) {
          h = new BytesHash(key);
        } else {
          h = new NumericHash(key);
        }
        hashKeys[i] = h;
      }
      HashKey k = (hashKeys.length > 1) ? new CompositeHash(hashKeys) : hashKeys[0];
      return new HashCollector(k, workers, worker);
    }
  }

  private class HashCollector extends DelegatingCollector {
    private int worker;
    private int workers;
    private HashKey hashKey;

    public HashCollector(HashKey hashKey, int workers, int worker) {
      this.hashKey = hashKey;
      this.workers = workers;
      this.worker = worker;
    }

    public void setScorer(Scorer scorer) throws IOException{
      delegate.setScorer(scorer);
    }

    public void setNextReader(AtomicReaderContext context) throws IOException {
      this.hashKey.setNextReader(context);
      delegate.setNextReader(context);
    }

    public void collect(int doc) throws IOException {
      if((hashKey.hashCode(doc) & 0x7FFFFFFF) % workers == worker) {
        delegate.collect(doc);
      }
    }
  }

  private interface HashKey {
    public void setNextReader(AtomicReaderContext reader) throws IOException;
    public long hashCode(int doc);
  }

  private class BytesHash implements HashKey {

    private SortedDocValues values;
    private String field;

    public BytesHash(String field) {
      this.field = field;
    }

    public void setNextReader(AtomicReaderContext context) throws IOException {
      values = context.reader().getSortedDocValues(field);
    }

    public long hashCode(int doc) {
      BytesRef ref = values.get(doc);
      return ref.hashCode();
    }
  }

  private class NumericHash implements HashKey {

    private NumericDocValues values;
    private String field;

    public NumericHash(String field) {
      this.field = field;
    }

    public void setNextReader(AtomicReaderContext context) throws IOException {
      values = context.reader().getNumericDocValues(field);
    }

    public long hashCode(int doc) {
      long l = values.get(doc);
      return l;
    }
  }

  private class ZeroHash implements HashKey {

    public long hashCode(int doc) {
      return 0;
    }

    public void setNextReader(AtomicReaderContext context) {

    }
  }

  private class CompositeHash implements HashKey {

    private HashKey key1;
    private HashKey key2;
    private HashKey key3;
    private HashKey key4;

    public CompositeHash(HashKey[] hashKeys) {
      key1 = hashKeys[0];
      key2 = hashKeys[1];
      key3 = (hashKeys.length > 2) ? hashKeys[2] : new ZeroHash();
      key4 = (hashKeys.length > 3) ? hashKeys[3] : new ZeroHash();
    }

    public void setNextReader(AtomicReaderContext context) throws IOException {
      key1.setNextReader(context);
      key2.setNextReader(context);
      key3.setNextReader(context);
      key4.setNextReader(context);
    }

    public long hashCode(int doc) {
      return key1.hashCode(doc)+key2.hashCode(doc)+key3.hashCode(doc)+key4.hashCode(doc);
    }
  }
}
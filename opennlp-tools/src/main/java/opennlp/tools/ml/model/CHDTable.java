/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.ml.model;

import opennlp.tools.ml.perfect.CHD;
import opennlp.tools.ml.perfect.PerfectHashFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link opennlp.tools.ml.model.CHDTable} is a hash table which maps entries
 * of an array to their index in the array. All entries in the array must
 * be unique otherwise a well-defined mapping is not possible.
 * <p>
 * The entry objects must implement {@link Object#equals(Object)} and
 * {@link Object#hashCode()} otherwise the behavior of this class is
 * undefined.
 * <p>
 * The implementation uses a hash table with open addressing and linear probing.
 * <p>
 * The table is thread safe and can concurrently accessed by multiple threads,
 * thread safety is achieved through immutability. Though its not strictly immutable
 * which means, that the table must still be safely published to other threads.
 */
public class CHDTable<T> implements ObjIntTable<T> {
    private final int size;
    private final PerfectHashFunction builder;
    private final int[] values;
    private final int[] key;

    /**
     * Initializes the current instance. The specified array is copied into the
     * table and later changes to the array do not affect this table in any way.
     *
     * @param mapping the values to be indexed, all values must be unique otherwise a
     *                well-defined mapping of an entry to an index is not possible
     * @throws IllegalArgumentException if the entries are not unique
     */
    public CHDTable(T[] mapping) {
        builder = CHD.newBuilder((String[]) mapping)
                .verbose()
                .buckets(mapping.length / 5 + 31).ratio(1.3f).build();
        size = mapping.length;
        values = new int[builder.getM()];
        key = new int[builder.getM()];
        for (int i = 0; i < mapping.length; i++) {
            String val = (String) mapping[i];
            int hash = builder.hash(val);
            values[hash] = i;
            key[hash] = builder.simpleHash(val);
        }
    }

    /**
     * Retrieves the index for the specified key.
     *
     * @param key
     * @return the index or -1 if there is no entry to the keys
     */
    @Override
    public int get(T key) {
        String k = (String) key;
        int hash = builder.hash(k);
        int sh = builder.simpleHash(k);
        if (this.key[hash] == sh)
            return values[hash];
        return -1;
    }

    @Override
    public int[] getAll(T[] keys) {
        int[] res = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            String k = (String) keys[i];
            int h = builder.hash(k);
            if (this.key[h] != builder.simpleHash(k))
                res[i] = -1;
            else
                res[i] = h;
        }
        for (int i = 0; i < res.length; i++) {
            if (res[i] == -1) continue;
            res[i] = this.values[res[i]];
        }
        return res;
    }

    /**
     * Retrieves the size.
     *
     * @return the number of elements in this map.
     */
    @Override
    public int size() {
        return size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T[] toArray(T array[]) {
        throw new UnsupportedOperationException();
//        for (Map.Entry<T, Integer> each : keys.entrySet()) {
//            array[each.getValue()] = each.getKey();
//        }
//        return array;
    }
}

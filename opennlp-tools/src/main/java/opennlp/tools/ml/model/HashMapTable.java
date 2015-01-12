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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@link opennlp.tools.ml.model.HashMapTable} is a hash table which maps entries
 * of an array to their index in the array. All entries in the array must
 * be unique otherwise a well-defined mapping is not possible.
 * <p/>
 * The entry objects must implement {@link Object#equals(Object)} and
 * {@link Object#hashCode()} otherwise the behavior of this class is
 * undefined.
 * <p/>
 * The implementation uses a hash table with open addressing and linear probing.
 * <p/>
 * The table is thread safe and can concurrently accessed by multiple threads,
 * thread safety is achieved through immutability. Though its not strictly immutable
 * which means, that the table must still be safely published to other threads.
 */
public class HashMapTable<T> implements ObjIntTable<T> {
    private final Map<T, Integer> keys;
    private final int size;

    /**
     * Initializes the current instance. The specified array is copied into the
     * table and later changes to the array do not affect this table in any way.
     *
     * @param mapping the values to be indexed, all values must be unique otherwise a
     *                well-defined mapping of an entry to an index is not possible
     * @throws IllegalArgumentException if the entries are not unique
     */
    public HashMapTable(T[] mapping) {

        int len = mapping.length;
        keys = new HashMap<>(len);
        size = len;
        for (int i = 0; i < mapping.length; i++) {
            keys.put(mapping[i], i);
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
        Integer k = keys.get(key);
        return k == null ? -1 : k;
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
        for (Map.Entry<T, Integer> each : keys.entrySet()) {
            array[each.getValue()] = each.getKey();
        }
        return array;
    }
}

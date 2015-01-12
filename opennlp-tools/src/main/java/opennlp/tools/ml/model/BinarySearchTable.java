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

/**
 * The {@link BinarySearchTable} is a hash table which maps entries
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
public class BinarySearchTable<T> implements ObjIntTable<T> {
    private final Object[] keys;
    private final int[] values;

    private final int size;

    /**
     * Initializes the current instance. The specified array is copied into the
     * table and later changes to the array do not affect this table in any way.
     *
     * @param mapping the values to be indexed, all values must be unique otherwise a
     *                well-defined mapping of an entry to an index is not possible
     * @throws IllegalArgumentException if the entries are not unique
     */
    public BinarySearchTable(T[] mapping) {

        int len = mapping.length;
        keys = new Object[len];
        values = new int[len];
        size = len;
        System.arraycopy(mapping, 0, keys, 0, len);
        Arrays.sort(keys);

        for (int i = 0; i < mapping.length; i++) {
            T each = mapping[i];
            int pos = Arrays.binarySearch(keys, each);
            if (pos >= 0) {
                values[pos] = i;
            }
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
        int pos = Arrays.binarySearch(keys, key);
        if (pos >= 0) return values[pos];
        else return -1;
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
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null)
                array[values[i]] = (T) keys[i];
        }

        return array;
    }
}

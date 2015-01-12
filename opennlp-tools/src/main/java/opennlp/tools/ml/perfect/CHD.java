
package opennlp.tools.ml.perfect;

import com.google.common.base.Stopwatch;
import com.google.common.hash.HashFunction;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.hash.Hashing.murmur3_128;

/**
 * Created by Stas on 1/5/15.
 */
public class CHD implements PerfectHashFunction {

    private static final int GOOD_FAST_HASH_SEED = (int) System.currentTimeMillis();
    private static final Charset CHARSET = Charset.forName("UTF-8");
    private static final HashFunction HASH_FUNCTION = murmur3_128(GOOD_FAST_HASH_SEED);

    private final int len;
    private final int buckets;
//    private final SimplyCompressedArray hashFunctions;
    private final int[] hashFunctions;

    public CHD(int len, int buckets, int[] hashFunctions) {
        this.len = len;
        this.buckets = buckets;
        this.hashFunctions = hashFunctions;
    }

    public static CHD.Builder newBuilder(String[] objects) {
        return new CHD.Builder(objects);
    }

    public int hash(String in) {
        return (int) (hash(hashFunctions[(int) (hash(0, in) % buckets)], in) % len);
    }


    public int simpleHash(String in) {
        return (int) hash(0, in);
    }

    @Override
    public int getM() {
        return len;
    }

    public static class Builder {
        private final String[] obj;
        private int buckets;
        private float ratio = 1.0f;
        private boolean verbose;

        Builder(String[] obj) {
            this.obj = obj;
            this.buckets = obj.length / 5;
        }

        public Builder buckets(int b) {
            this.buckets = b;
            return this;
        }

        public Builder ratio(float r) {
            this.ratio = r;
            return this;
        }

        public Builder verbose() {
            this.verbose = true;
            return this;
        }

        public PerfectHashFunction build() {
            int len = obj.length;
            int targetLen = Math.round(len * ratio);

            int r = buckets;
            int[] hashFunctions = new int[r];
            Node[] buckets = new Node[r];
            BigBitSet mapped = new BigBitSet(targetLen);

            for (String each : obj) {
                long hashCode = hash(0, each);
                int bucketPos = (int) (hashCode % r);
                Node bucket = buckets[bucketPos];
                buckets[bucketPos] = new Node(bucket, each, bucket == null ? 1 : bucket.size + 1, bucketPos);
            }

            Arrays.sort(buckets, (o1, o2) -> o1 == o2 ? 0 : o1 == null ? -1 :
                    o2 == null ? 1 :
                            o2.size - o1.size);

            Stopwatch s = Stopwatch.createStarted();
            int collisions = 0;
            double collisionsSum = 0;
            int maxD = 0;

            for (int i = 0; i < buckets.length; i++) {
                Node node = buckets[i];
                if (node == null) continue;
                int d = 1;

                if (this.verbose && i % 10000 == 0) {
                    System.out.println("Processed : "
                            + i
                            + " of "
                            + buckets.length
                            + " (took "
                            + s.elapsed(TimeUnit.SECONDS)
                            + "s, collisions = "
                            + collisions
                            + ")");

                    s.reset();
                    s.start();
                    collisionsSum += collisions;
                    collisions = 0;
                }

                Node n = node;
                BitSet current = new BitSet(node.size);
                int[] hashes = new int[node.size];

                while (n != null) {
                    int h = (int) (hash(d, n.str) % targetLen);
                    if (!mapped.get(h) && !current.get(h)) {
                        hashes[n.size - 1] = h;
                        current.set(h);
                    } else {
                        current.clear();
                        n = node;
                        d++;
                        collisions++;
                        continue;
                    }
                    n = n.next;
                }
                hashFunctions[node.original] = d;

                maxD = Math.max(maxD, d);
                for (int hash : hashes) {
                    mapped.set(hash);
                }
            }
            if (verbose) {
                System.out.println("Hash functions = " + hashFunctions.length);
                Set<Object> func = new HashSet<>();
                for (int each : hashFunctions) {
                    func.add(each);
                }
                System.out.println("Unique hash functions = " + func.size());
                System.out.println("Avg collisions per bucket = " + collisionsSum / hashFunctions.length);
            }

//            SimplyCompressedArray arr = CompressionUtils.simpleCompression(hashFunctions);
//            if (verbose)
//                System.out.println("Compressed hash functions from " + (hashFunctions.length * 4) / 1024 + "Kb to " + arr.size() / 1024 + "Kb");

            return new CHD(targetLen, this.buckets, hashFunctions);
        }

        static class Node {
            final Node next;
            final String str;
            final int size;
            final int original;

            Node(Node next, String obj, int size, int original) {
                this.next = next;
                this.str = obj;
                this.size = size;
                this.original = original;
            }
        }
    }

    //    private static long hash(int d, String hash) {
//        byte[] bytes = hash.getBytes(CHARSET);
//        if (d == 0) {
//            return CityHashFunctions.cityHash64(bytes, 0, bytes.length) & Long.MAX_VALUE;
//        }
//        long[] longs = CityHashFunctions.cityHash128WithSeed(bytes, 0, bytes.length, new long[]{d, CityHashFunctions.K0});
//        return longs[1] & Long.MAX_VALUE;
//    }
//
//    private static long hash(int d, String hash) {
//        if (d == 0)
//            return HASH_FUNCTION.hashString(hash, CHARSET).asLong() & Long.MAX_VALUE;
//        int base = 0x11FACE8D;
//        return murmur3_128(d * base).hashString(hash, CHARSET).asLong() & Long.MAX_VALUE;
//    }
    private static long hash(int d, String hash) {
        if (d == 0) d = 0x01000193;
        for (int i = 0; i < hash.length(); i++)
            d = ((d * 0x01000193) ^ hash.charAt(i)) & 0x7fffffff;
        return d;
    }
}

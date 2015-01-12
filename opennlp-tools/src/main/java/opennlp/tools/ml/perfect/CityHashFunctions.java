package opennlp.tools.ml.perfect;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedInts;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static com.google.common.base.Preconditions.checkPositionIndexes;

/**
 * Adapted from the C++ CityHash implementation from Google at
 * http://code.google.com/p/cityhash/source/browse/trunk/src/city.cc.
 *
 * @author Geoff Pike
 * @author Jyrki Alakuijala
 * @author Louis Wasserman
 */
final class CityHashFunctions {
    private CityHashFunctions() {
    }

    public static final long K0 = 0xc3a5c85c97cb3127L;
    public static final long K1 = 0xb492b66fbe98f273L;
    public static final long K2 = 0x9ae16a3b2f90404fL;
    public static final long K3 = 0xc949d7c7509e6557L;

    static final boolean PLATFORM_IS_LITTLE_ENDIAN =
            ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN);

    private interface ArrayGetter {
        long getLongLittleEndian(byte[] array, int offset);

        int getIntLittleEndian(byte[] array, int offset);
    }

    /*
     * Borrowing the trick from UnsignedBytes.lexicographicalComparator(),
     * we deliberately set it up so that we only attempt to get the Unsafe
     * when the UnsafeLongGetter class is being loaded.
     */
    @SuppressWarnings("unused") // the class is accessed reflectively!
    private enum UnsafeArrayGetter implements ArrayGetter {
        UNSAFE_LITTLE_ENDIAN {
            @Override
            public long getLongLittleEndian(byte[] array, int offset) {
                return theUnsafe.getLong(array, (long) offset + BYTE_ARRAY_BASE_OFFSET);
            }

            @Override
            public int getIntLittleEndian(byte[] array, int offset) {
                return theUnsafe.getInt(array, (long) offset + BYTE_ARRAY_BASE_OFFSET);
            }
        },
        UNSAFE_BIG_ENDIAN {
            @Override
            public long getLongLittleEndian(byte[] array, int offset) {
                long bigEndian = theUnsafe.getLong(array, (long) offset + BYTE_ARRAY_BASE_OFFSET);
                // The hardware is big-endian, so we need to reverse the order of the bytes.
                return Long.reverseBytes(bigEndian);
            }

            @Override
            public int getIntLittleEndian(byte[] array, int offset) {
                int bigEndian = theUnsafe.getInt(array, (long) offset + BYTE_ARRAY_BASE_OFFSET);
                // The hardware is big-endian, so we need to reverse the order of the bytes.
                return Integer.reverseBytes(bigEndian);
            }
        };

        static final Unsafe theUnsafe;

        /**
         * The offset to the first element in a byte array.
         */
        static final int BYTE_ARRAY_BASE_OFFSET;

        static {
            theUnsafe = (Unsafe) AccessController.doPrivileged(
                    new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            try {
                                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                                f.setAccessible(true);
                                return f.get(null);
                            } catch (NoSuchFieldException e) {
                                // It doesn't matter what we throw;
                                // it's swallowed in getBestComparator().
                                throw new Error();
                            } catch (IllegalAccessException e) {
                                throw new Error();
                            }
                        }
                    });

            BYTE_ARRAY_BASE_OFFSET = theUnsafe.arrayBaseOffset(byte[].class);

            // sanity check - this should never fail
            if (theUnsafe.arrayIndexScale(byte[].class) != 1) {
                throw new AssertionError();
            }
        }
    }

    private enum JavaArrayGetter implements ArrayGetter {
        INSTANCE {
            @Override
            public long getLongLittleEndian(byte[] src, int off) {
                return Longs.fromBytes(
                        src[off + 7],
                        src[off + 6],
                        src[off + 5],
                        src[off + 4],
                        src[off + 3],
                        src[off + 2],
                        src[off + 1],
                        src[off]);
            }

            @Override
            public int getIntLittleEndian(byte[] src, int off) {
                return Ints.fromBytes(
                        src[off + 3],
                        src[off + 2],
                        src[off + 1],
                        src[off]);
            }
        };
    }

    private static final ArrayGetter arrayGetter;

    static final String UNSAFE_ARRAY_GETTER_NAME =
            CityHashFunctions.class.getName() + "$UnsafeArrayGetter";

    static {
        ArrayGetter theGetter;
        try {
            Class<?> theClass = Class.forName(UNSAFE_ARRAY_GETTER_NAME);

            ArrayGetter[] getters = (ArrayGetter[]) theClass.getEnumConstants();

            theGetter = PLATFORM_IS_LITTLE_ENDIAN ? getters[0] : getters[1];
        } catch (Throwable t) { // ensure we really catch *everything*
            theGetter = JavaArrayGetter.INSTANCE;
        }
        arrayGetter = theGetter;
    }

    private static long shiftMix(long val) {
        return val ^ (val >>> 47);
    }

    private static long hashLen16(long u, long v) {
        long a = shiftMix((v ^ u) * K_MUL);
        return shiftMix((v ^ a) * K_MUL) * K_MUL;
    }

    private static final long K_MUL = 0x9ddfea08eb382d69L;

    private static long getLong(byte[] src, int off) {
        return arrayGetter.getLongLittleEndian(src, off);
    }

    private static int getInt(byte[] src, int off) {
        return arrayGetter.getIntLittleEndian(src, off);
    }

    private static long hashLen0To16(byte[] src, int off, int len) {
        if (len > 8) {
            long a = getLong(src, off);
            long b = getLong(src, off + len - 8);
            return hashLen16(a, Long.rotateRight(b + len, len)) ^ b;
        } else if (len >= 4) {
            long a = UnsignedInts.toLong(getInt(src, off));
            long b = UnsignedInts.toLong(getInt(src, off + len - 4));
            return hashLen16(len + (a << 3), b);
        } else if (len > 0) {
            byte a = src[off];
            byte b = src[off + (len >> 1)];
            byte c = src[off + len - 1];
            int y = UnsignedBytes.toInt(a) + (UnsignedBytes.toInt(b) << 8);
            int z = len + (UnsignedBytes.toInt(c) << 2);
            return shiftMix(y * K2 ^ z * K3) * K2;
        }
        return K2;
    }

    private static long hashLen17To32(byte[] src, int off, int len) {
        long a = getLong(src, off) * K1;
        long b = getLong(src, off + 8);
        long c = getLong(src, off + len - 8) * K2;
        long d = getLong(src, off + len - 16) * K0;
        return hashLen16(Long.rotateRight(a - b, 43) + Long.rotateRight(c, 30) + d,
                a + Long.rotateRight(b ^ K3, 20) - c + len);
    }

    private static void weakHashLen32WithSeeds(long[] result,
                                               long w, long x, long y, long z, long a, long b) {
        a += w;
        b = Long.rotateRight(b + a + z, 21);
        long c = a;
        a += x + y;
        b += Long.rotateRight(a, 44);
        result[0] = a + z;
        result[1] = b + c;
    }

    private static void weakHashLen32WithSeeds(
            long[] result,
            byte[] src, int off,
            long a, long b) {
        weakHashLen32WithSeeds(
                result,
                getLong(src, off),
                getLong(src, off + 8),
                getLong(src, off + 16),
                getLong(src, off + 24),
                a,
                b);
    }

    private static long hashLen33To64(byte[] src, int off, int len) {
        long z = getLong(src, off + 24);
        long a = getLong(src, off) + (len + getLong(src, off + len - 16)) * K0;
        long b = Long.rotateRight(a + z, 52);
        long c = Long.rotateRight(a, 37);
        a += getLong(src, off + 8);
        c += Long.rotateRight(a, 7);
        a += getLong(src, off + 16);
        long vf = a + z;
        long vs = b + Long.rotateRight(a, 31) + c;
        a = getLong(src, off + 16) + getLong(src, off + len - 32);
        z = getLong(src, off + len - 8);
        b = Long.rotateRight(a + z, 52);
        c = Long.rotateRight(a, 37);
        a += getLong(src, off + len - 24);
        c += Long.rotateRight(a, 7);
        a += getLong(src, off + len - 16);
        long wf = a + z;
        long ws = b + Long.rotateRight(a, 31) + c;
        long r = shiftMix((vf + ws) * K2 + (wf + vs) * K0);
        return shiftMix(r * K0 + vs) * K2;
    }

    public static long cityHash64(byte[] src, int off, int len) {
        if (len <= 32) {
            if (len <= 16) {
                return hashLen0To16(src, off, len);
            } else {
                return hashLen17To32(src, off, len);
            }
        } else if (len <= 64) {
            return hashLen33To64(src, off, len);
        }

        long x = getLong(src, off + len - 40);
        long y = getLong(src, off + len - 16) + getLong(src, off + len - 56);
        long z = hashLen16(getLong(src, off + len - 48) + len, getLong(src, off + len - 24));

        long[] v = new long[2];
        long[] w = new long[2];

        weakHashLen32WithSeeds(v, src, off + len - 64, len, z);
        weakHashLen32WithSeeds(w, src, off + len - 32, y + K1, x);
        x = x * K1 + getLong(src, off);

        len = (len - 1) & (~63);
        do {
            x = Long.rotateRight(x + y + v[0] + getLong(src, off + 8), 37) * K1;
            y = Long.rotateRight(y + v[1] + getLong(src, off + 48), 42) * K1;
            x ^= w[1];
            y += v[0] + getLong(src, off + 40);
            z = Long.rotateRight(z + w[0], 33) * K1;
            weakHashLen32WithSeeds(v, src, off, v[1] * K1, x + w[0]);
            weakHashLen32WithSeeds(w, src, off + 32, z + w[1], y + getLong(src, off + 16));

            long tmp = x;
            x = z;
            z = tmp;

            len -= 64;
            off += 64;
        } while (len != 0);
        return hashLen16(hashLen16(v[0], w[0]) + shiftMix(y) * K1 + z,
                hashLen16(v[1], w[1]) + x);
    }

    private static void cityMurmur(
            long[] result,
            byte[] src, int off, int len,
            long[] seed) {
        long a = seed[0];
        long b = seed[1];
        long c = 0;
        long d = 0;

        int l = len - 16;
        if (l <= 0) {
            a = shiftMix(a * K1) * K1;
            c = b * K1 + hashLen0To16(src, off, len);
            d = shiftMix(a + ((len >= 8) ? getLong(src, off) : c));
        } else {
            c = hashLen16(getLong(src, off + len - 8) + K1, a);
            d = hashLen16(b + len, c + getLong(src, off + len - 16));
            a += d;
            do {
                a ^= shiftMix(getLong(src, off) * K1) * K1;
                a *= K1;
                b ^= a;
                c ^= shiftMix(getLong(src, off + 8) * K1) * K1;
                c *= K1;
                d ^= c;
                len -= 16;
                off += 16;
            } while (len > 16);
        }
        a = hashLen16(a, c);
        b = hashLen16(d, b);

        result[0] = a ^ b;
        result[1] = hashLen16(b, a);
    }

    public static long[] cityHash128WithSeed(
            byte[] src, int off, int len,
            long[] seed) {
        long[] result = new long[2];

        if (len < 128) {
            cityMurmur(result, src, off, len, seed);
            return result;
        }

        long x = seed[0];
        long y = seed[1];
        long z = len * K1;

        long[] v = new long[2];
        v[0] = Long.rotateRight(y ^ K1, 49) * K1 + getLong(src, off);
        v[1] = Long.rotateRight(v[0], 42) * K1 + getLong(src, off + 8);

        long[] w = new long[2];
        w[0] = Long.rotateRight(y + z, 35) * K1 + x;
        w[1] = Long.rotateRight(x + getLong(src, off + 88), 53) * K1;

        do {
            for (int k = 0; k < 2; k++) {
                x = Long.rotateRight(x + y + v[0] + getLong(src, off + 8), 37) * K1;
                y = Long.rotateRight(y + v[1] + getLong(src, off + 48), 42) * K1;
                x ^= w[1];
                y += v[0] + getLong(src, off + 40);
                z = Long.rotateRight(z + w[0], 33) * K1;
                weakHashLen32WithSeeds(v, src, off, v[1] * K1, x + w[0]);
                weakHashLen32WithSeeds(w, src, off + 32, z + w[1], y + getLong(src, off + 16));

                long tmp = x;
                x = z;
                z = tmp;

                len -= 64;
                off += 64;
            }
        } while (len >= 128);
        x += Long.rotateRight(v[0] + z, 49) * K0;
        z += Long.rotateRight(w[0], 37) * K0;
        for (int tail = 0; tail < len; ) {
            tail += 32;
            y = Long.rotateRight(x + y, 42) * K0 + v[1];
            w[0] += getLong(src, off + len - tail + 16);
            x = x * K0 + w[0];
            z += w[1] + getLong(src, off + len - tail);
            w[1] += v[0];
            weakHashLen32WithSeeds(
                    v,
                    src,
                    off + len - tail,
                    v[0] + z,
                    v[1]);
        }
        x = hashLen16(x, v[0]);
        y = hashLen16(y + z, w[0]);
        result[0] = hashLen16(x + v[1], w[1]) + y;
        result[1] = hashLen16(x + w[1], y + v[1]);
        return result;
    }

    private static byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static long[] cityHash128(byte[] src, int off, int len) {
        if (len >= 16) {
            long[] seed = {getLong(src, off) ^ K3, getLong(src, off + 8)};
            return cityHash128WithSeed(src, off + 16, len - 16, seed);
        } else if (len >= 8) {
            long[] seed = {
                    getLong(src, off) ^ (len * K0),
                    getLong(src, off + len - 8) ^ K1
            };
            return cityHash128WithSeed(EMPTY_BYTE_ARRAY, 0, 0, seed);
        } else {
            long[] seed = {K0, K1};
            return cityHash128WithSeed(src, off, len, seed);
        }
    }

}
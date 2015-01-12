package opennlp.tools.ml.model;

/**
 * Created by Stas on 1/9/15.
 */
public class ObjIntTableFactory {
    private static final boolean OPTIMIZE = Boolean.getBoolean("optimize_parser");

    static {
        if (OPTIMIZE)
            System.out.println("Optimized parser mode is on...");
    }

    public static <E> ObjIntTable<E> create(E[] arr, double loadfactor) {
        return OPTIMIZE
                ? new CHDTable<>(arr)
                : new IndexHashTable<>(arr, loadfactor);
    }
}

package opennlp.tools.ml.model;

/**
 * Created by Stas on 1/9/15.
 */
public interface ObjIntTable<T> {
    int get(T key);

    int size();

    default int[] getAll(T[] keys) {
        return new int[0];
    }

    @SuppressWarnings("unchecked")
    T[] toArray(T array[]);
}

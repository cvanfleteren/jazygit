package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.util.function.UnaryOperator;

/**
 * What the features keep in the {@link Model}, each under its own {@link Slice}. A slice that holds what it holds
 * initially is not kept, so two models whose slices hold the same are equal, whatever was changed and changed back.
 *
 * @param values what each slice that was changed holds, by the slice; only {@link #map} puts a value in, of the type
 *               of its slice
 */
public record Slices(Map<Slice<?>, Object> values) {

    public static final Slices NONE = new Slices(HashMap.empty());

    /**
     * What {@code slice} holds.
     */
    @SuppressWarnings("unchecked") // map keeps a T under a Slice<T> only
    public <T> T get(Slice<T> slice) {
        return values.get(slice).map(value -> (T) value).getOrElse(slice::initial);
    }

    /**
     * These slices with {@code f} applied to what {@code slice} holds; these themselves when that changes nothing.
     */
    public <T> Slices map(Slice<T> slice, UnaryOperator<T> f) {
        T before = get(slice);
        T after = f.apply(before);
        if (after.equals(before)) {
            return this;
        }
        return new Slices(after.equals(slice.initial()) ? values.remove(slice) : values.put(slice, after));
    }
}

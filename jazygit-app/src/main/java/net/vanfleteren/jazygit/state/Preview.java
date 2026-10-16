package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import java.util.function.UnaryOperator;

/**
 * What is read for the item highlighted in a pane, e.g. the log of the highlighted branch.
 *
 * @param key      what is highlighted, which identifies what is shown
 * @param value    what was read for it
 * @param previous the last fully read preview of another item, kept while this one is loading so the UI can
 *                 keep showing it instead of flashing a placeholder
 * @param <K>      what identifies the highlighted item
 * @param <V>      what is read for it
 */
public record Preview<K, V>(K key, Loadable<V> value, Option<Preview<K, V>> previous) {

    public Preview(K key, Loadable<V> value) {
        this(key, value, Option.none());
    }

    /**
     * A preview of {@code key} that is loading, falling back to {@code shown}, the one that was shown before.
     */
    public static <K, V> Preview<K, V> loading(K key, Option<Preview<K, V>> shown) {
        Option<Preview<K, V>> previous = shown.flatMap(
                preview -> preview.value() instanceof Loadable.Loaded<V> ? Option.of(preview) : preview.previous());
        return new Preview<>(key, Loadable.loading(), previous);
    }

    /**
     * Applies {@code f} to {@code preview} if it is the preview of {@code key}.
     */
    public static <K, V> Option<Preview<K, V>> updated(
            Option<Preview<K, V>> preview, K key, UnaryOperator<Preview<K, V>> f) {
        return preview.map(shown -> shown.key().equals(key) ? f.apply(shown) : shown);
    }

    /**
     * Like {@link Loadable#reload}, returns this instance when the value is unchanged.
     */
    public Preview<K, V> reload(V loaded) {
        Loadable<V> reloaded = value.reload(loaded);
        return reloaded == value ? this : new Preview<>(key, reloaded);
    }

    /**
     * This preview, failed to load for {@code failure}.
     */
    public Preview<K, V> failed(Failure failure) {
        return new Preview<>(key, new Loadable.Failed<>(failure));
    }

    /**
     * What to show: the previous preview while this one is loading, if there is one.
     */
    public Preview<K, V> shown() {
        return value instanceof Loadable.Loading<V> ? previous.getOrElse(this) : this;
    }
}

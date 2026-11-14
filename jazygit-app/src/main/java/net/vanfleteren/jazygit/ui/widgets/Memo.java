package net.vanfleteren.jazygit.ui.widgets;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import java.util.function.Function;

/**
 * The value last computed from a key, given again while the key stays equal: for what a pane would otherwise work out
 * anew on every render of the same model. The value given again is the same instance, so a list that is shown a value
 * keeps what it built from it.
 */
public final class Memo<K, V> {

    private Option<Tuple2<K, V>> last = Option.none();

    /**
     * The value computed from {@code key}: the one computed last, if that was from a key equal to it.
     */
    public V get(K key, Function<? super K, ? extends V> compute) {
        Tuple2<K, V> kept =
                last.filter(previous -> previous._1().equals(key)).getOrElse(() -> Tuple.of(key, compute.apply(key)));
        last = Option.of(kept);
        return kept._2();
    }
}

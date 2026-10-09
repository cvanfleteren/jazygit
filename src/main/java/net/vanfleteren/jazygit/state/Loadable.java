package net.vanfleteren.jazygit.state;

import java.util.function.Function;

/**
 * A value that is read in the background: still loading, loaded, or failed to load.
 */
public sealed interface Loadable<T> {

    record Loading<T>() implements Loadable<T> {
    }

    record Loaded<T>(T value) implements Loadable<T> {
    }

    record Failed<T>(String message) implements Loadable<T> {
    }

    static <T> Loadable<T> loading() {
        return new Loading<>();
    }

    default <R> Loadable<R> map(Function<? super T, ? extends R> f) {
        return switch (this) {
            case Loading<T>() -> new Loading<>();
            case Loaded<T>(T value) -> new Loaded<>(f.apply(value));
            case Failed<T>(String message) -> new Failed<>(message);
        };
    }

    /**
     * Returns this instance when it already holds an equal value, so unchanged data keeps its
     * identity and views can skip rebuilding it.
     */
    default Loadable<T> reload(T value) {
        if (this instanceof Loaded<T>(T current) && current.equals(value)) {
            return this;
        }
        return new Loaded<>(value);
    }
}

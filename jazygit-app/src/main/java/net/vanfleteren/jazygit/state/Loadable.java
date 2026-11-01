package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import java.util.function.Function;

/**
 * A value that is read in the background: still loading, loaded, or failed to load.
 */
public sealed interface Loadable<T> {

    static <T> Loadable<T> loading() {
        return new Loading<>();
    }

    default <R> Loadable<R> map(Function<? super T, ? extends R> f) {
        return switch (this) {
            case Loading<T>() -> new Loading<>();
            case Loaded<T>(T value) -> new Loaded<>(f.apply(value));
            case Failed<T>(Failure failure) -> new Failed<>(failure);
        };
    }

    /**
     * The value, once it is loaded.
     */
    default Option<T> toOption() {
        return this instanceof Loaded<T>(T value) ? Option.of(value) : Option.none();
    }

    /**
     * The value once it is loaded, {@code otherwise} until then or when it failed to load.
     */
    default T getOrElse(T otherwise) {
        return toOption().getOrElse(otherwise);
    }

    /**
     * Returns this instance when it already holds an equal value, so unchanged data keeps its
     * identity and views can skip rebuilding it.
     */
    default Loadable<T> reload(T value) {
        return toOption().contains(value) ? this : new Loaded<>(value);
    }

    /**
     * Why reading again failed: a value that was loaded stays, as reading it again can fail for a while, say
     * on a timeout.
     */
    default Loadable<T> failed(Failure failure) {
        return this instanceof Loaded<T> ? this : new Failed<>(failure);
    }

    /**
     * Whether reading failed and nothing was loaded before.
     */
    default boolean isFailed() {
        return this instanceof Failed<T>;
    }

    record Loading<T>() implements Loadable<T> {}

    record Loaded<T>(T value) implements Loadable<T> {}

    record Failed<T>(Failure failure) implements Loadable<T> {}
}

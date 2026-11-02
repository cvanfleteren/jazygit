package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Where an {@code A} sits in an {@code S}, if it is there: reads it, and changes it. A change that changes nothing
 * gives back the same {@code S}, so that unchanged data keeps its identity and views can skip rebuilding it.
 *
 * @param find   what is there, if anything
 * @param change {@code S} with a function applied to what is there; the same {@code S} when nothing is
 * @param <S>    what holds it
 * @param <A>    what is held
 */
public record Lens<S, A>(Function<S, Option<A>> find, BiFunction<S, UnaryOperator<A>, S> change) {

    /**
     * The field that {@code get} reads and {@code set} sets: it is always there.
     */
    public static <S, A> Lens<S, A> of(Function<S, A> get, BiFunction<S, A, S> set) {
        return new Lens<>(s -> Option.of(get.apply(s)), (s, f) -> changed(s, get.apply(s), f, set));
    }

    /**
     * What the optional field that {@code get} reads and {@code set} sets holds, when it is the one {@code wanted}.
     */
    public static <S, A> Lens<S, A> where(
            Function<S, Option<A>> get, BiFunction<S, Option<A>, S> set, Predicate<A> wanted) {
        return new Lens<>(
                s -> get.apply(s).filter(wanted),
                (s, f) -> get.apply(s)
                        .filter(wanted)
                        .map(before -> changed(s, before, f, (held, after) -> set.apply(held, Option.of(after))))
                        .getOrElse(s));
    }

    /**
     * What the optional field that {@code get} reads and {@code set} sets holds, whatever it is.
     */
    public static <S, A> Lens<S, A> some(Function<S, Option<A>> get, BiFunction<S, Option<A>, S> set) {
        return where(get, set, held -> true);
    }

    /**
     * What is there in {@code s}, if anything.
     */
    public Option<A> get(S s) {
        return find.apply(s);
    }

    /**
     * {@code s} with {@code f} applied to what is there; {@code s} itself when nothing is, or {@code f} changed nothing.
     */
    public S map(S s, UnaryOperator<A> f) {
        return change.apply(s, f);
    }

    /**
     * What {@code inner} finds in what this finds.
     */
    public <B> Lens<S, B> andThen(Lens<A, B> inner) {
        return new Lens<>(s -> get(s).flatMap(inner::get), (s, f) -> map(s, held -> inner.map(held, f)));
    }

    /**
     * What this finds, changed together with what {@code other} finds: the same value kept in two places.
     */
    public Lens<S, A> alongWith(Lens<S, A> other) {
        return new Lens<>(find, (s, f) -> other.map(map(s, f), f));
    }

    private static <S, A> S changed(S s, A before, UnaryOperator<A> f, BiFunction<S, A, S> set) {
        A after = f.apply(before);
        return after == before ? s : set.apply(s, after);
    }
}

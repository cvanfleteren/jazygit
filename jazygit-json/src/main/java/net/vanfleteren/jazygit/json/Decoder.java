package net.vanfleteren.jazygit.json;

import io.vavr.Function2;
import io.vavr.Function3;
import io.vavr.Function4;
import io.vavr.Function5;
import io.vavr.Function6;
import io.vavr.Function7;
import io.vavr.Function8;
import io.vavr.Tuple;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.json.Json.JArray;
import net.vanfleteren.jazygit.json.Json.JBool;
import net.vanfleteren.jazygit.json.Json.JNull;
import net.vanfleteren.jazygit.json.Json.JNumber;
import net.vanfleteren.jazygit.json.Json.JObject;
import net.vanfleteren.jazygit.json.Json.JString;

/**
 * Turns a {@link Json} into a {@code T}, or says where and why it cannot, stopping at the first error (ADR 42).
 * Decoders are made of the ones here: a field that is missing fails, and {@code null} is only taken where
 * {@link #nullable} takes it.
 */
@FunctionalInterface
public interface Decoder<T> {

    Either<DecodeError, T> decode(Json json);

    /**
     * {@code text} parsed as JSON and decoded.
     */
    default Either<DecodeError, T> parse(String text) {
        return Json.parse(text).flatMap(this::decode);
    }

    default <R> Decoder<R> map(Function<? super T, ? extends R> f) {
        return json -> decode(json).map(f);
    }

    /**
     * Decodes the same value again with the decoder {@code f} picks from what this one decoded, like {@link #fail}
     * for a value that is no good.
     */
    default <R> Decoder<R> flatMap(Function<? super T, ? extends Decoder<? extends R>> f) {
        return json -> decode(json).flatMap(t -> Either.narrow(f.apply(t).decode(json)));
    }

    static Decoder<String> string() {
        return json -> json instanceof JString(String value) ? Either.right(value) : expected("a string", json);
    }

    static Decoder<Boolean> bool() {
        return json -> json instanceof JBool(boolean value) ? Either.right(value) : expected("a boolean", json);
    }

    static Decoder<Integer> integer() {
        return json -> json instanceof JNumber(var value)
                ? Try.of(value::intValueExact).toEither(() -> DecodeError.of("expected an int, got " + value))
                : expected("a number", json);
    }

    static Decoder<Long> longValue() {
        return json -> json instanceof JNumber(var value)
                ? Try.of(value::longValueExact).toEither(() -> DecodeError.of("expected a long, got " + value))
                : expected("a number", json);
    }

    /**
     * An instant as ISO 8601 text, like GitHub's {@code 2026-10-26T22:00:02Z}.
     */
    static Decoder<Instant> instant() {
        return string().flatMap(text -> Try.of(() -> Instant.parse(text))
                .fold(e -> fail("expected an instant, got \"" + text + "\""), Decoder::succeed));
    }

    /**
     * The constant of {@code type} a string names.
     */
    static <E extends Enum<E>> Decoder<E> enumOf(Class<E> type) {
        List<E> constants = List.of(type.getEnumConstants());
        return string().flatMap(name -> constants
                .find(constant -> constant.name().equals(name))
                .fold(
                        () -> fail("expected one of " + constants.mkString(", ") + ", got \"" + name + "\""),
                        Decoder::succeed));
    }

    /**
     * The field {@code name} of an object, decoded; it fails when the object has no such field.
     */
    static <T> Decoder<T> field(String name, Decoder<T> decoder) {
        return json -> json instanceof JObject(var fields)
                ? fields.get(name)
                        .toEither(() -> DecodeError.of("missing"))
                        .flatMap(decoder::decode)
                        .mapLeft(error -> error.inField(name))
                : expected("an object", json);
    }

    /**
     * The field at {@code path}, its names separated by dots, like {@code commits.totalCount}.
     */
    static <T> Decoder<T> at(String path, Decoder<T> decoder) {
        return List.of(path.split("\\.", -1)).foldRight(decoder, Decoder::field);
    }

    /**
     * None for {@code null}, else the value decoded.
     */
    static <T> Decoder<Option<T>> nullable(Decoder<T> decoder) {
        return json -> json instanceof JNull
                ? Either.right(Option.none())
                : decoder.decode(json).map(Option::some);
    }

    /**
     * Each element of an array, decoded; it fails at the first that does not decode.
     */
    static <T> Decoder<List<T>> list(Decoder<T> decoder) {
        return json -> json instanceof JArray(var elements)
                ? Either.sequenceRight(elements.zipWithIndex()
                                .map(element ->
                                        decoder.decode(element._1()).mapLeft(error -> error.atIndex(element._2()))))
                        .map(List::ofAll)
                : expected("an array", json);
    }

    /**
     * Each field of an object, decoded, by its name and in its order: for an object whose names are data, like the
     * hosts gh is logged in to or the aliases of a GraphQL query. It fails at the first that does not decode.
     */
    static <T> Decoder<LinkedHashMap<String, T>> dict(Decoder<T> decoder) {
        return json -> json instanceof JObject(var fields)
                ? Either.sequenceRight(fields.map(field -> decoder.decode(field._2())
                                .map(value -> Tuple.of(field._1(), value))
                                .mapLeft(error -> error.inField(field._1()))))
                        .map(LinkedHashMap::ofEntries)
                : expected("an object", json);
    }

    static <T> Decoder<T> succeed(T value) {
        return json -> Either.right(value);
    }

    static <T> Decoder<T> fail(String message) {
        return json -> Either.left(DecodeError.of(message));
    }

    /**
     * Decodes an object with the decoder for what its string field {@code name} says, and with {@code otherwise}
     * for what no decoder is given for: a GraphQL union by its {@code __typename}, as a node of another type
     * answers none of the fields a fragment asks for (ADR 42).
     */
    static <T> Decoder<T> byField(String name, Map<String, Decoder<T>> decoders, Decoder<T> otherwise) {
        return field(name, string()).flatMap(value -> decoders.get(value).getOrElse(otherwise));
    }

    static <A, B, R> Decoder<R> map2(Decoder<A> a, Decoder<B> b, Function2<? super A, ? super B, ? extends R> f) {
        return json -> a.decode(json).flatMap(va -> b.decode(json).map(vb -> f.apply(va, vb)));
    }

    static <A, B, C, R> Decoder<R> map3(
            Decoder<A> a, Decoder<B> b, Decoder<C> c, Function3<? super A, ? super B, ? super C, ? extends R> f) {
        return map2(map2(a, b, Tuple::of), c, (ab, vc) -> f.apply(ab._1(), ab._2(), vc));
    }

    static <A, B, C, D, R> Decoder<R> map4(
            Decoder<A> a,
            Decoder<B> b,
            Decoder<C> c,
            Decoder<D> d,
            Function4<? super A, ? super B, ? super C, ? super D, ? extends R> f) {
        return map2(map3(a, b, c, Tuple::of), d, (abc, vd) -> f.apply(abc._1(), abc._2(), abc._3(), vd));
    }

    static <A, B, C, D, E, R> Decoder<R> map5(
            Decoder<A> a,
            Decoder<B> b,
            Decoder<C> c,
            Decoder<D> d,
            Decoder<E> e,
            Function5<? super A, ? super B, ? super C, ? super D, ? super E, ? extends R> f) {
        return map2(
                map4(a, b, c, d, Tuple::of), e, (abcd, ve) -> f.apply(abcd._1(), abcd._2(), abcd._3(), abcd._4(), ve));
    }

    static <A, B, C, D, E, F, R> Decoder<R> map6(
            Decoder<A> a,
            Decoder<B> b,
            Decoder<C> c,
            Decoder<D> d,
            Decoder<E> e,
            Decoder<F> f,
            Function6<? super A, ? super B, ? super C, ? super D, ? super E, ? super F, ? extends R> g) {
        return map2(
                map5(a, b, c, d, e, Tuple::of),
                f,
                (abcde, vf) -> g.apply(abcde._1(), abcde._2(), abcde._3(), abcde._4(), abcde._5(), vf));
    }

    static <A, B, C, D, E, F, G, R> Decoder<R> map7(
            Decoder<A> a,
            Decoder<B> b,
            Decoder<C> c,
            Decoder<D> d,
            Decoder<E> e,
            Decoder<F> f,
            Decoder<G> g,
            Function7<? super A, ? super B, ? super C, ? super D, ? super E, ? super F, ? super G, ? extends R> h) {
        return map2(
                map6(a, b, c, d, e, f, Tuple::of),
                g,
                (abcdef, vg) ->
                        h.apply(abcdef._1(), abcdef._2(), abcdef._3(), abcdef._4(), abcdef._5(), abcdef._6(), vg));
    }

    static <A, B, C, D, E, F, G, H, R> Decoder<R> map8(
            Decoder<A> a,
            Decoder<B> b,
            Decoder<C> c,
            Decoder<D> d,
            Decoder<E> e,
            Decoder<F> f,
            Decoder<G> g,
            Decoder<H> h,
            Function8<
                            ? super A,
                            ? super B,
                            ? super C,
                            ? super D,
                            ? super E,
                            ? super F,
                            ? super G,
                            ? super H,
                            ? extends R>
                    i) {
        return map2(
                map7(a, b, c, d, e, f, g, Tuple::of),
                h,
                (abcdefg, vh) -> i.apply(
                        abcdefg._1(),
                        abcdefg._2(),
                        abcdefg._3(),
                        abcdefg._4(),
                        abcdefg._5(),
                        abcdefg._6(),
                        abcdefg._7(),
                        vh));
    }

    private static <T> Either<DecodeError, T> expected(String what, Json json) {
        return Either.left(DecodeError.of("expected " + what + ", got " + json.kind()));
    }
}

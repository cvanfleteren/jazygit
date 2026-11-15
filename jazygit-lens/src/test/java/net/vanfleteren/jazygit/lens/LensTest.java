package net.vanfleteren.jazygit.lens;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * Reading and changing what sits where in a value.
 */
class LensTest {

    private record Pair(String left, Option<String> right) {

        Pair withLeft(String left) {
            return new Pair(left, right);
        }

        Pair withRight(Option<String> right) {
            return new Pair(left, right);
        }
    }

    private record Holder(Option<Pair> pair) {

        Holder withPair(Option<Pair> pair) {
            return new Holder(pair);
        }
    }

    private static final Lens<Pair, String> LEFT = Lens.of(Pair::left, Pair::withLeft);

    private static final Lens<Pair, String> RIGHT_OF_A =
            Lens.where(Pair::right, Pair::withRight, right -> right.startsWith("a"));

    @Test
    void aFieldIsAlwaysThere() {
        Pair pair = new Pair("x", Option.none());

        assertEquals(Option.of("x"), LEFT.get(pair));
        assertEquals(new Pair("X", Option.none()), LEFT.map(pair, String::toUpperCase));
    }

    @Test
    void anOptionalFieldIsOnlyThereWhenItHoldsTheOneWanted() {
        Pair wanted = new Pair("x", Option.of("abc"));
        Pair other = new Pair("x", Option.of("bcd"));
        Pair empty = new Pair("x", Option.none());

        assertEquals(Option.of("abc"), RIGHT_OF_A.get(wanted));
        assertEquals(new Pair("x", Option.of("ABC")), RIGHT_OF_A.map(wanted, String::toUpperCase));
        assertEquals(Option.none(), RIGHT_OF_A.get(other));
        assertSame(other, RIGHT_OF_A.map(other, String::toUpperCase));
        assertEquals(Option.none(), RIGHT_OF_A.get(empty));
        assertSame(empty, RIGHT_OF_A.map(empty, String::toUpperCase));
    }

    @Test
    void aChangeThatChangesNothingGivesBackTheSameValue() {
        Holder holder = new Holder(Option.of(new Pair("x", Option.of("abc"))));
        Lens<Holder, String> right = Lens.some(Holder::pair, Holder::withPair).andThen(RIGHT_OF_A);

        assertSame(holder, right.map(holder, same -> same));
        assertSame(holder.pair().get(), LEFT.map(holder.pair().get(), same -> same));
    }

    @Test
    void anInnerLensFindsWhatIsInWhatTheOuterOneFinds() {
        Lens<Holder, String> left = Lens.some(Holder::pair, Holder::withPair).andThen(LEFT);
        Holder holder = new Holder(Option.of(new Pair("x", Option.none())));
        Holder empty = new Holder(Option.none());

        assertEquals(Option.of("x"), left.get(holder));
        assertEquals(new Holder(Option.of(new Pair("X", Option.none()))), left.map(holder, String::toUpperCase));
        assertEquals(Option.none(), left.get(empty));
        assertSame(empty, left.map(empty, String::toUpperCase));
    }

    @Test
    void alongWithAnotherReadsTheFirstAndChangesBoth() {
        Lens<Pair, String> both = LEFT.alongWith(Lens.some(Pair::right, Pair::withRight));
        Pair pair = new Pair("x", Option.of("y"));

        assertEquals(Option.of("x"), both.get(pair));
        assertEquals(new Pair("X", Option.of("Y")), both.map(pair, String::toUpperCase));
        assertEquals(new Pair("X", Option.none()), both.map(new Pair("x", Option.none()), String::toUpperCase));
    }
}

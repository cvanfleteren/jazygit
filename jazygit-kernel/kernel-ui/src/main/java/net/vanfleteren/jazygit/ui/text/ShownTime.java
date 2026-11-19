package net.vanfleteren.jazygit.ui.text;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

/**
 * A time a view shows, which moves on as time goes by: when it changes next after an instant, before another (ADR 124).
 * The app draws a frame when the first of those it shows changes, and not otherwise.
 */
@FunctionalInterface
public interface ShownTime {

    /**
     * How far ahead a change is looked for: what does not change within it is drawn again by then.
     */
    Duration HORIZON = Duration.ofDays(1);

    /**
     * The first instant after {@code now}, and before {@code before}, at which it shows something else; none when it
     * shows the same until then.
     */
    Option<Instant> changes(Instant now, Instant before);

    /**
     * The time a view shows as the text {@code shows} gives at an instant, made of the very function the view draws it
     * with. Its text is taken to stay the same from one change to the next, and not to come back to one it showed
     * before: a count that goes up or down, rounded.
     */
    static ShownTime reading(Function<Instant, String> shows) {
        return (now, before) -> {
            String shown = shows.apply(now);
            if (!before.isAfter(now) || shows.apply(before).equals(shown)) {
                return Option.none();
            }
            // Doubling the step finds an instant at which it changed, halving it the first one, to the millisecond.
            long same = 0;
            long changed = 1000;
            long until = Duration.between(now, before).toMillis();
            while (changed < until && shows.apply(now.plusMillis(changed)).equals(shown)) {
                same = changed;
                changed = changed * 2;
            }
            changed = Math.min(changed, until);
            while (changed - same > 1) {
                long half = same + (changed - same) / 2;
                if (shows.apply(now.plusMillis(half)).equals(shown)) {
                    same = half;
                } else {
                    changed = half;
                }
            }
            return Option.of(now.plusMillis(changed));
        };
    }

    /**
     * A time a view shows that moves on every {@code interval}, as the bars of checks that run do, which is drawn too
     * richly to read as a text.
     */
    static ShownTime every(Duration interval) {
        return (now, before) -> Option.of(now.plus(interval)).filter(before::isAfter);
    }

    /**
     * When the first of {@code shown} changes after {@code now}, within the {@link #HORIZON}: each is only asked
     * whether it changes before the first change found so far.
     */
    static Option<Instant> next(List<ShownTime> shown, Instant now) {
        Instant horizon = now.plus(HORIZON);
        Instant first = shown.foldLeft(
                horizon, (earliest, time) -> time.changes(now, earliest).getOrElse(earliest));
        return Option.when(first.isBefore(horizon), first);
    }
}

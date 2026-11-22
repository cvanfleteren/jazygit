package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;

/**
 * A merge of a pull request on Forgejo scheduled for when its checks succeed (ADR 181), as the timeline of the pull
 * request tells it: Forgejo's API gives no field of it, and the timeline does not say how it is to be merged.
 *
 * @param by who scheduled it; empty when that is not known
 * @param at when it was scheduled
 */
public record ForgejoScheduledMerge(String by, Instant at) {

    /**
     * The merge scheduled by {@code events}, in the order of the timeline: the last of them, when it schedules one.
     * A merge Forgejo tried but could not do, or one merged by hand, leaves it scheduled on the timeline, as Forgejo
     * then drops it without saying so: cancelling it is answered with 404, which tells.
     */
    public static Option<ForgejoScheduledMerge> of(List<Event> events) {
        return events.lastOption().flatMap(last -> switch (last) {
            case Event.Scheduled(String by, Instant at) -> Option.of(new ForgejoScheduledMerge(by, at));
            case Event.Cancelled() -> Option.none();
        });
    }

    /**
     * What the timeline of a pull request says of scheduling its merge.
     */
    public sealed interface Event {

        /**
         * Its merge was scheduled by {@code by} at {@code at}.
         */
        record Scheduled(String by, Instant at) implements Event {}

        /**
         * Its scheduled merge was cancelled.
         */
        record Cancelled() implements Event {}
    }
}

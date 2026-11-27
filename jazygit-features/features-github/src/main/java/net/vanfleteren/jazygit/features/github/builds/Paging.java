package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * Where reading older builds, a page at a time, is: of the commits of a branch, or of the runs of a workflow on every
 * branch (ADR 131).
 */
public sealed interface Paging {

    /**
     * After a page, with {@code read} read in all and the next page starting at {@code next}: reading on while fewer
     * than {@code limit} were read; idle at {@code next} once {@code limit} is reached; done when no page is left.
     */
    static Paging after(int read, int limit, Option<String> next) {
        return next.<Paging>map(from -> read < limit ? new Reading(Option.of(from)) : new Idle(from))
                .getOrElse(Done::new);
    }

    /**
     * Whether a page is being read.
     */
    default boolean reading() {
        return this instanceof Reading;
    }

    /**
     * Whether it waits for the page from {@code from} on.
     */
    default boolean awaits(Option<String> from) {
        return this instanceof Reading(Option<String> reading) && reading.equals(from);
    }

    /**
     * Where the next page to read starts: none at the newest, or when no older ones are left.
     */
    Option<String> from();

    /**
     * Reading on from where the next page starts.
     */
    default Paging more() {
        return new Reading(from());
    }

    /**
     * Reading the page failed for {@code failure}: stopped for it when {@code someRead}; else done, as what was read
     * failed (none, and the next read starts at the newest again).
     */
    default Paging failed(Failure failure, boolean someRead) {
        return someRead ? new Stopped(from(), failure) : new Done();
    }

    /**
     * Reading the page from {@code from} on; none at the newest.
     */
    record Reading(Option<String> from) implements Paging {}

    /**
     * Not reading, with older ones left from {@code after} on, which can be asked for.
     */
    record Idle(String after) implements Paging {

        @Override
        public Option<String> from() {
            return Option.of(after);
        }
    }

    /**
     * Reading the page from {@code from} on failed for {@code failure}, after some were read; it can be asked again.
     */
    record Stopped(Option<String> from, Failure failure) implements Paging {}

    /**
     * Not reading, with no older ones left, or none read as the first page failed.
     */
    record Done() implements Paging {

        @Override
        public Option<String> from() {
            return Option.none();
        }
    }
}

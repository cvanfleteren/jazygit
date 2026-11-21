package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import java.util.Locale;

/**
 * How the builds of a commit went, as Forgejo combines the statuses posted for it (ADR 174).
 *
 * @param state    how they went together
 * @param statuses each of them, as a CI posted it: a job of Forgejo Actions is {@code <workflow> / <job> (<event>)}
 */
public record CommitStatus(State state, List<Status> statuses) {

    /**
     * A commit no CI posted a status for.
     */
    public static final CommitStatus NONE = new CommitStatus(State.NONE, List.empty());

    /**
     * How builds went: Forgejo's {@code success}, {@code pending}, {@code failure}, {@code error} and
     * {@code warning}, where a warning counts as failed, as it asks to be looked at; none without a status.
     */
    public enum State {
        PASSED,
        FAILED,
        RUNNING,
        NONE;

        /**
         * The state Forgejo names {@code name}: none for one it has not got.
         */
        public static State named(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "success" -> PASSED;
                case "failure", "error", "warning" -> FAILED;
                case "pending" -> RUNNING;
                default -> NONE;
            };
        }
    }

    /**
     * One status of a commit.
     *
     * @param context     what it is of, as {@code build / test (push)}
     * @param state       how it went
     * @param description what the CI says of it, as {@code Failing after 11m2s}
     */
    public record Status(String context, State state, String description) {}
}

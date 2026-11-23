package net.vanfleteren.jazygit.core.repo;

import io.vavr.control.Option;
import java.time.Instant;

/**
 * An entry of the reflog of HEAD: where HEAD was moved to, when, and by what (ADR 186). One commit is often in a
 * reflog many times, so an entry is known by its place and time, not by its commit.
 *
 * @param place   how many entries are newer than it: {@code HEAD@{place}}
 * @param time    when HEAD was moved, which is not when the commit was made
 * @param subject what git wrote for the move, such as {@code checkout: moving from main to feature-x}
 * @param action  what the subject says was done
 * @param commit  the commit HEAD was moved to
 */
public record ReflogEntry(int place, Instant time, String subject, Action action, Commit commit) {

    /**
     * How git names the entry: {@code HEAD@{3}}.
     */
    public String selector() {
        return "HEAD@{" + place + "}";
    }

    public Sha sha() {
        return commit.sha();
    }

    /**
     * What moved HEAD, as git writes it in the subject of an entry. git writes these subjects untranslated, so they
     * can be read whatever the user's language.
     */
    public sealed interface Action {

        /**
         * {@code checkout: moving from main to feature-x}: a branch or commit checked out, or switched to.
         */
        record Checkout(String from, String to) implements Action {}

        /**
         * {@code commit: message}, {@code commit (amend): …}, {@code commit (initial): …}, {@code commit (merge): …}
         * or {@code commit (cherry-pick): …}, the last when a cherry-pick that stopped on conflicts is committed: a
         * commit made.
         */
        record Committed(CommitKind kind, String message) implements Action {}

        /**
         * {@code reset: moving to HEAD~1}.
         */
        record Reset(String to) implements Action {}

        /**
         * {@code rebase (pick): message}: a step of a rebase, such as {@code start}, {@code pick}, {@code squash},
         * {@code finish} or {@code abort}.
         *
         * @param phase the step; empty when git wrote none
         */
        record Rebase(String phase, String detail) implements Action {}

        /**
         * {@code merge feature-x: Fast-forward}.
         */
        record Merge(String merged, String detail) implements Action {}

        /**
         * {@code pull: Fast-forward}, or a step of a pull that rebases: {@code pull --rebase (pick): message}.
         *
         * @param phase the step of its rebase; none for a pull that merged
         */
        record Pull(Option<String> phase, String detail) implements Action {}

        /**
         * {@code cherry-pick: message}.
         */
        record CherryPick(String message) implements Action {}

        /**
         * Any other subject, such as a revert, a clone or what another tool wrote, kept whole.
         */
        record Other(String subject) implements Action {}
    }

    /**
     * Which kind of commit {@link Action.Committed} made.
     */
    public enum CommitKind {
        PLAIN,
        AMEND,
        INITIAL,
        MERGE,
        CHERRY_PICK
    }
}

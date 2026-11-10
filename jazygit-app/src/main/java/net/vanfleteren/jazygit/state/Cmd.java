package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * IO that {@link Update} asks for. Commands are immutable data that know how to run themselves and
 * how to report failure; {@link Program} performs them in the background and feeds the outcome
 * back as a {@link Msg}.
 */
public interface Cmd {

    /**
     * Performs the IO against {@code env} and returns the {@link Msg} that reports its outcome. A {@link RuntimeException}
     * thrown from here is a bug; {@link Program} still turns it into {@link #failed(Failure)}.
     */
    Msg run(Env env);

    /**
     * The {@link Msg} that reports this command failing for {@code failure}.
     */
    Msg failed(Failure failure);

    /**
     * Whether this command runs in the background, next to the other commands instead of after them: it is
     * slow, like talking to a remote, and the user is not waiting for it.
     */
    default boolean background() {
        return false;
    }

    /**
     * What this command shows as while it runs, once it ran a while (ADR 33); none for {@link Label#WORKING}.
     */
    default Option<Label> label() {
        return Option.none();
    }

    /**
     * The slot this command takes, if it is only wanted until a newer one for that slot is asked for: the newer
     * one cancels it when it has not started yet, and it then reports nothing: it is only done (ADR 101). Only a
     * command whose result the model drops once a newer one is asked for may take one, as nothing else may wait for
     * its answer.
     */
    default Option<Slot> slot() {
        return Option.none();
    }

    /**
     * What this command must not share with another command at the same time, if anything: while a command that
     * holds it runs, one that needs it waits on its own thread.
     */
    default Option<Guard> guard() {
        return Option.none();
    }

    /**
     * What a command can be the latest of.
     */
    enum Slot {
        /**
         * The preview of the highlighted pull request.
         */
        PULL_REQUEST_PREVIEW,
        /**
         * The log of the highlighted branch.
         */
        BRANCH_LOG,
        /**
         * The changes of the commit highlighted in the commits pane.
         */
        COMMIT_DETAIL,
        /**
         * The changes of the commit highlighted in the divergence that is open.
         */
        DIVERGENCE_DETAIL,
        /**
         * The changes of the commit highlighted in the commits of a branch that are open.
         */
        BRANCH_COMMITS_DETAIL,
        /**
         * The changes of the stash highlighted.
         */
        STASH_DETAIL,
        /**
         * The diff of the node highlighted in the tree of the files of a commit or a stash.
         */
        COMMIT_FILE_DIFF,
        /**
         * The diff of the files highlighted.
         */
        FILE_DIFF
    }

    /**
     * What a command can keep others from while it runs.
     */
    enum Guard {
        /**
         * The remote-tracking refs and {@code FETCH_HEAD}, which every fetch writes: by fetching, pulling, pushing,
         * deleting a branch on its remote, or checking out a pull request through gh. Two at once can leave
         * {@code FETCH_HEAD} with the lines of both, or fail on the lock of a ref.
         */
        REMOTE_REFS
    }
}

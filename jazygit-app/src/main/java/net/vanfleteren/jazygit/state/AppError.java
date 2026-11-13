package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * What the user is told went wrong, as data, which says itself as a {@link Phrase}; the view turns that into text. A
 * feature keeps its own errors, as a sealed interface of its own, as it keeps its messages (see {@link Msg}): adding
 * one changes nothing here. Errors the core tells keep a sealed interface of their own too, beside the code that tells
 * them (e.g. {@link UpstreamError}). What stays here is about any operation, not one kind of thing.
 */
public interface AppError {

    /**
     * What the user is told: the message, and what goes into it.
     */
    Phrase phrase();

    /**
     * What a subject, if any, and then {@code last} say, as the error key of an operation takes them.
     */
    private static Phrase[] subjectThen(Option<String> subject, Phrase last) {
        return subject.map(Phrase::verbatim).toList().append(last).toJavaArray(Phrase[]::new);
    }

    /**
     * An operation on the repository failed.
     *
     * @param subject what it was done to, e.g. the branch that was pushed, if the operation names one
     */
    record OperationFailed(Operation operation, Option<String> subject, Failure failure) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of(operation.errorKey(), subjectThen(subject, Phrase.failure(failure)));
        }
    }

    /**
     * An operation was asked for that waits until the repository is in the middle of nothing, see
     * {@link Operation#waitsForUnfinished()}, but it is in the middle of {@code inProgress}, or {@code conflicts}
     * files still have conflicts.
     *
     * @param subject what it was to be done to, as for {@link OperationFailed}
     */
    record Unfinished(Operation operation, Option<String> subject, Option<InProgress> inProgress, int conflicts)
            implements AppError {

        /**
         * What kind of operation {@code inProgress} is, e.g. "rebase".
         */
        public static Phrase kind(InProgress inProgress) {
            return Phrase.of(
                    switch (inProgress) {
                        case InProgress.Merging m -> "inProgress.merge";
                        case InProgress.Rebasing r -> "inProgress.rebase";
                        case InProgress.CherryPicking c -> "inProgress.cherryPick";
                        case InProgress.Reverting r -> "inProgress.revert";
                    });
        }

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    operation.errorKey(),
                    subjectThen(
                            subject,
                            inProgress
                                    .map(op -> Phrase.of("error.inProgress", kind(op)))
                                    .getOrElse(() -> Phrase.of("error.conflictsRemain", Phrase.count(conflicts)))));
        }
    }
}

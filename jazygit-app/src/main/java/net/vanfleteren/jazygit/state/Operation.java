package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * An operation that changes the repository. Its git commands are logged under its title, and its failure
 * is reported with its error message. The features declare their operations next to the commands that
 * perform them.
 *
 * @param logTitleKey        the message key of the command log title
 * @param errorKey           the message key of its failure, which is given the subject of the operation (if it
 *                           has one, see {@link OperationCmd#subject()}) and why it failed
 * @param waitsForUnfinished whether it waits until the repository is in the middle of nothing, and has no
 *                           conflicts: it moves HEAD or rewrites history, which git refuses then, or which would
 *                           get mixed up with what is in progress
 * @param busyKey            the message key of what a branch it keeps busy shows next to its spinner, for the
 *                           operations that talk to its remote
 */
public record Operation(String logTitleKey, String errorKey, boolean waitsForUnfinished, Option<String> busyKey) {

    /**
     * An operation that neither waits for anything unfinished nor labels the branch it keeps busy.
     */
    public static Operation of(String logTitleKey, String errorKey) {
        return new Operation(logTitleKey, errorKey, false, Option.none());
    }

    /**
     * This operation, waiting until the repository is in the middle of nothing.
     */
    public Operation waitingForUnfinished() {
        return new Operation(logTitleKey, errorKey, true, busyKey);
    }

    /**
     * This operation, labelling the branch it keeps busy with the message {@code key}.
     */
    public Operation labelledWhenBusy(String key) {
        return new Operation(logTitleKey, errorKey, waitsForUnfinished, Option.of(key));
    }
}

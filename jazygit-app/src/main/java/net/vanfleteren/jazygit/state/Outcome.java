package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;

/**
 * What the user is told about the operations: how the last one went, and the git commands they ran.
 *
 * @param error      why the last operation failed, until the next one is started
 * @param notice     what the user is told about the last operation when it did not fail, but still needs
 *                   their attention, until the next one is started
 * @param commandLog the mutating git commands that were executed, oldest first
 */
@With
public record Outcome(Option<AppError> error, Option<Notice> notice, List<LogEntry> commandLog) {

    public static final Outcome NONE = new Outcome(Option.none(), Option.none(), List.empty());

    /**
     * This outcome without an error, nor a notice.
     */
    public Outcome cleared() {
        return withError(Option.none()).withNotice(Option.none());
    }

    /**
     * This outcome with {@code notice}, instead of an error.
     */
    public Outcome informing(Notice notice) {
        return withError(Option.none()).withNotice(Option.of(notice));
    }

    /**
     * This outcome with {@code error}, instead of a notice.
     */
    public Outcome failing(AppError error) {
        return withError(Option.of(error)).withNotice(Option.none());
    }
}

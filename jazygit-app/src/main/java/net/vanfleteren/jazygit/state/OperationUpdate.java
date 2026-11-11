package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Operations on the repository, whichever they are. One is asked about in a popup, then started, unless the
 * repository is in the middle of something. Once done, its commands are logged, its branch is no longer busy, a
 * failure is shown, or a notice when it stopped on conflicts, and the repository is read again to show what changed;
 * and what GitHub knows, after one that changed that.
 */
public final class OperationUpdate {

    private OperationUpdate() {}

    /**
     * Starts {@code cmd}: its branch, if it has one, is busy until it is done ({@link Jobs#busy()}). One that waits
     * until the repository is in the middle of nothing is not started while it is, which the user is told instead.
     */
    public static Next start(Model model, OperationCmd cmd) {
        return unfinished(model, cmd).map(error -> Next.of(model.fail(error))).getOrElse(() -> Next.of(model, cmd));
    }

    private static Option<AppError> unfinished(Model model, OperationCmd cmd) {
        return Option.when(
                        cmd.operation().waitsForUnfinished(),
                        () -> model.repo().status().toOption())
                .flatMap(status -> status)
                .filter(status -> status.inProgress().isDefined() || status.conflicts() > 0)
                .map(status -> new AppError.Unfinished(
                        cmd.operation(), cmd.subject(), status.inProgress(), status.conflicts()));
    }

    /**
     * Opens {@code popup}, which asks about an operation to start: the error of the last one is not shown
     * anymore.
     */
    public static Next ask(Model model, Popup popup) {
        return Next.of(model.clearError().openPopup(popup));
    }

    /**
     * Opens {@code popup} as {@link #ask(Model, Popup)} does, if there is one: none when the operation it would
     * ask about cannot be done, and then nothing happens.
     */
    public static Next ask(Model model, Option<? extends Popup> popup) {
        return popup.map(open -> ask(model, open)).getOrElse(() -> Next.of(model));
    }

    /**
     * Confirms the open popup of type {@code type}: closes it, clears the error and starts the operation
     * {@code cmd} makes of it. When no such popup is open, or {@code cmd} makes none because what was entered
     * cannot be used, nothing happens.
     */
    public static <P extends Popup> Next confirm(
            Model model, Class<P> type, Function<? super P, Option<? extends OperationCmd>> cmd) {
        return model.popup(type)
                .flatMap(cmd)
                .map(operation -> start(model.withoutPopup(type).clearError(), operation))
                .getOrElse(() -> Next.of(model));
    }

    public static Next update(Model model, OperationMsg msg) {
        return switch (msg) {
            case OperationMsg.Succeeded(OperationCmd cmd, List<ExecutedCommand> commands) ->
                succeeded(model, cmd, commands);
            case OperationMsg.Stopped(OperationCmd cmd, List<ExecutedCommand> commands, var notice) ->
                refreshed(done(model, cmd, commands).inform(notice), cmd);
            case OperationMsg.Failed(OperationCmd cmd, Failure failure) ->
                RereadUpdate.refresh(done(model, cmd, failure.commands())
                        .fail(new AppError.OperationFailed(cmd.operation(), cmd.subject(), failure)));
        };
    }

    /**
     * What happens when {@code cmd} succeeded running {@code commands}, also for an operation that reports its
     * success with a message of its own: what any success does, which that message then adds to.
     */
    public static Next succeeded(Model model, OperationCmd cmd, List<ExecutedCommand> commands) {
        return refreshed(done(model, cmd, commands).clearError(), cmd);
    }

    /**
     * The repository read again once {@code cmd} is done; and what GitHub knows, when it changed that.
     */
    private static Next refreshed(Model model, OperationCmd cmd) {
        Next refreshed = RereadUpdate.refresh(model);
        return cmd.changesGitHub() ? PullRequestsUpdate.loadAnyway(refreshed) : refreshed;
    }

    /**
     * The model once {@code cmd} ran {@code commands}.
     */
    public static Model done(Model model, OperationCmd cmd, List<ExecutedCommand> commands) {
        return CommandLog.append(model, cmd.operation(), commands);
    }
}

package net.vanfleteren.jazygit.kernel.state.operation;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Jobs;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.RereadUpdate;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Operations on the repository, whichever they are. One is started unless the repository is in the middle of
 * something. Once done, its commands are logged, its branch is no longer busy, a failure is shown, or a notice when
 * it stopped on conflicts, and the repository is read again to show what changed; the features are told, for those
 * that read what the forge has to do so again after one that changed it.
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
     * The repository read again once {@code cmd} is done, and what the features answer of it: those that read from
     * the forge do so again, when it changed what the forge has.
     */
    private static Next refreshed(Model model, OperationCmd cmd) {
        return Events.happened(RereadUpdate.refresh(model), new KernelEvent.OperationDone(cmd.changesForge()));
    }

    /**
     * The model once {@code cmd} ran {@code commands}.
     */
    public static Model done(Model model, OperationCmd cmd, List<ExecutedCommand> commands) {
        return CommandLog.append(model, cmd.operation(), commands);
    }
}

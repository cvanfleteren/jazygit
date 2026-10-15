package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * What happens when an operation on the repository is done, whichever it was: its commands are logged, its
 * branch is no longer busy, a failure is shown, and the repository is read again to show what changed; and what
 * GitHub knows, after one that changed that.
 */
public final class OperationUpdate {

    private OperationUpdate() {}

    public static Next update(Model model, OperationMsg msg) {
        return switch (msg) {
            case OperationMsg.Succeeded(OperationCmd cmd, List<ExecutedCommand> commands) ->
                succeeded(model, cmd, commands);
            case OperationMsg.Failed(OperationCmd cmd, Failure failure) ->
                Update.refresh(done(model, cmd, failure.commands())
                        .fail(new AppError.OperationFailed(cmd.operation(), cmd.subject(), failure)));
        };
    }

    /**
     * What happens when {@code cmd} succeeded running {@code commands}, also for an operation that reports its
     * success with a message of its own: what any success does, which that message then adds to.
     */
    public static Next succeeded(Model model, OperationCmd cmd, List<ExecutedCommand> commands) {
        Next refreshed = Update.refresh(done(model, cmd, commands).clearError());
        return cmd.changesGitHub() ? PullRequestsUpdate.loadAnyway(refreshed) : refreshed;
    }

    /**
     * The model once {@code cmd} ran {@code commands}.
     */
    public static Model done(Model model, OperationCmd cmd, List<ExecutedCommand> commands) {
        Model released = cmd.busyBranch().map(model::withoutBusyBranch).getOrElse(model);
        return CommandLog.append(released, cmd.operation(), commands);
    }
}

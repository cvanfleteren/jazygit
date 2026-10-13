package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.process.ExecutedCommand;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * What happens when an operation on the repository is done, whichever it was: its commands are logged, its
 * branch is no longer busy, a failure is shown, and the repository is read again to show what changed.
 */
public final class OperationUpdate {

    private OperationUpdate() {
    }

    public static Next update(Model model, OperationMsg msg) {
        return switch (msg) {
            case OperationMsg.Succeeded(OperationCmd cmd, List<ExecutedCommand> commands) ->
                    Update.refresh(done(model, cmd, commands).clearError());
            case OperationMsg.Failed(OperationCmd cmd, Failure failure) -> Update.refresh(done(model, cmd, failure.commands())
                    .fail(new AppError.OperationFailed(cmd.operation(), cmd.subject(), failure)));
        };
    }

    /**
     * The model once {@code cmd} ran {@code commands}.
     */
    public static Model done(Model model, OperationCmd cmd, List<ExecutedCommand> commands) {
        Model released = cmd.busyBranch().map(model::withoutBusyBranch).getOrElse(model);
        return CommandLog.append(released, cmd.operation(), commands);
    }
}

package net.vanfleteren.jazygit.features.changes.cherrypick;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Commits the changes of the commit {@code sha} on top of the checked out branch.
 */
public record CherryPickCmd(Sha sha) implements GitOperationCmd {

    public static final Operation CHERRY_PICK = Operation.of(
                    "changes.log.title.cherryPick", "changes.error.cherryPick.failed")
            .waitingForUnfinished();

    @Override
    public Operation operation() {
        return CHERRY_PICK;
    }

    @Override
    public Option<String> subject() {
        return Option.of(sha.shortSha());
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.integrate().cherryPick(sha);
    }

    @Override
    public Msg succeeded(List<ExecutedCommand> commands) {
        return new CherryPickMsg.Picked(this, commands);
    }
}

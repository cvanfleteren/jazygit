package net.vanfleteren.jazygit.feature.cherrypick;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commits the changes of the commit {@code sha} on top of the checked out branch.
 */
public record CherryPickCmd(Sha sha) implements GitOperationCmd {

    public static final Operation CHERRY_PICK =
            Operation.of("log.title.cherryPick", "error.cherryPick.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return CHERRY_PICK;
    }

    @Override
    public Option<String> subject() {
        return Option.of(sha.shortSha());
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.cherryPick(sha);
    }

    @Override
    public Msg succeeded(List<ExecutedCommand> commands) {
        return new CherryPickMsg.Picked(this, commands);
    }
}

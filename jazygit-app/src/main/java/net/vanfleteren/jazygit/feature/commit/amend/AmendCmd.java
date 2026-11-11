package net.vanfleteren.jazygit.feature.commit.amend;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.commit.amend.AmendTarget.LastCommit;
import net.vanfleteren.jazygit.feature.commit.amend.AmendTarget.Older;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Stages the paths, if any, then amends {@code target} with the index, keeping its message. Only amending an
 * older commit waits until nothing is in progress, as it rebases the commits after it.
 */
public record AmendCmd(AmendTarget target, List<String> stage) implements GitOperationCmd {

    public static final Operation AMEND = Operation.of("log.title.amend", "error.amend.failed");

    public static final Operation AMEND_COMMIT =
            Operation.of("log.title.amendCommit", "error.amendCommit.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return switch (target) {
            case LastCommit() -> AMEND;
            case Older(Sha sha) -> AMEND_COMMIT;
        };
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return switch (target) {
            case LastCommit() ->
                GitFailure.sequence(
                        () -> git.index().stage(stage), () -> git.commits().amendLast());
            case Older(Sha sha) ->
                GitFailure.sequence(
                        () -> git.index().stage(stage), () -> git.commits().amend(sha));
        };
    }
}

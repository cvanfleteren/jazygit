package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Duration;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Showing the checks on the tip of a branch on a forge one by one (ADR 32, 209).
 */
public sealed interface ChecksMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ChecksUpdate.update(model, this);
    }

    /**
     * The user asked for the checks on {@code port} of {@code branch}, local or remote.
     */
    record Requested(ChecksPort port, BranchRef branch) implements ChecksMsg {}

    /**
     * The user asked, in the pull request dialog of {@code port}, for the checks of the pull request it shows.
     */
    record PullRequestChecksRequested(ChecksPort port) implements ChecksMsg {}

    /**
     * The user closed the checks: back to the popup they were opened from, if they were.
     */
    record Closed() implements ChecksMsg {}

    /**
     * The checks on {@code branch} on {@code port} were read, or why that failed.
     */
    record Read(ChecksPort port, RemoteBranch branch, Either<Failure, ChecksRead> read) implements ChecksMsg {}

    /**
     * The user asked to read the checks that are shown again.
     */
    record RefreshChosen() implements ChecksMsg {}

    /**
     * The user asked to run the failed checks shown again (ADR 135).
     */
    record RerunRequested() implements ChecksMsg {}

    /**
     * The user confirmed running the failed checks again.
     */
    record RerunConfirmed() implements ChecksMsg {}

    /**
     * The user did not confirm running the failed checks again.
     */
    record RerunCancelled() implements ChecksMsg {}

    /**
     * The forge {@code port} was asked by {@code cmd} to run the failed jobs of runs on {@code branch} again, which
     * went as {@code outcomes}, running {@code commands}.
     */
    record Rerun(
            ChecksPort port,
            RemoteBranch branch,
            OperationCmd cmd,
            List<RerunPlan.Outcome> outcomes,
            List<ExecutedCommand> commands)
            implements ChecksMsg {}

    /**
     * The user asked to pick the check {@code by} rows after the one picked, one listed later when positive (ADR 160).
     */
    record Stepped(int by) implements ChecksMsg {}

    /**
     * The user clicked the check {@code pick} to pick it (ADR 160).
     */
    record Clicked(CheckPick pick) implements ChecksMsg {}

    /**
     * The user asked to open the log of the check picked in an editor (ADR 160).
     */
    record LogRequested() implements ChecksMsg {}

    /**
     * The log of the check {@code name} was read by {@code cmd}, running {@code commands}, and kept in {@code file},
     * to open in an editor at {@code line}.
     */
    record LogRead(OperationCmd cmd, String name, String file, int line, List<ExecutedCommand> commands)
            implements ChecksMsg {}

    /**
     * Periodic prompt to read the checks on {@code port} again, while they are shown: each forge sends its own, so
     * that only those of the forge that is shown read them.
     */
    record Tick(ChecksPort port) implements ChecksMsg, Msg.Periodic {

        // Once a minute rather than every 10 s while a check runs.
        public static final Duration WHILE_AWAY = Duration.ofMinutes(1);

        @Override
        public Duration whileAway() {
            return WHILE_AWAY;
        }
    }
}

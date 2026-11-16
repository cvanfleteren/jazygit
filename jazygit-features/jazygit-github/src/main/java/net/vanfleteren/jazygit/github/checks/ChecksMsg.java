package net.vanfleteren.jazygit.github.checks;

import io.vavr.collection.List;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.github.PendingChecks;

/**
 * Showing the checks on the tip of a branch one by one (ADR 32).
 */
public sealed interface ChecksMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ChecksUpdate.update(model, this);
    }

    /**
     * The user asked for the checks of {@code branch}, local or remote.
     */
    record Requested(BranchRef branch) implements ChecksMsg {}

    /**
     * The user asked, in the pull request dialog, for the checks of the pull request it shows.
     */
    record PullRequestChecksRequested() implements ChecksMsg {}

    /**
     * The user closed the checks: back to the pull request dialog they were opened from, if they were.
     */
    record Closed() implements ChecksMsg {}

    /**
     * The checks on {@code branch} on GitHub were read.
     */
    record Loaded(RemoteBranch branch, PendingChecks checks) implements ChecksMsg {}

    record Failed(RemoteBranch branch, Failure failure) implements ChecksMsg {}

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
     * GitHub was asked to run the failed jobs of the runs of {@code cmd} again, which went as {@code outcomes}, running
     * {@code commands}.
     */
    record Rerun(RerunCmd cmd, List<RerunCmd.Outcome> outcomes, List<ExecutedCommand> commands) implements ChecksMsg {}

    /**
     * Periodic prompt to read the checks again, while they are shown.
     */
    record Tick() implements ChecksMsg, Msg.Periodic {

        // Once a minute rather than every 10 s while a check runs.
        public static final Duration WHILE_AWAY = Duration.ofMinutes(1);

        @Override
        public Duration whileAway() {
            return WHILE_AWAY;
        }
    }
}

package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Duration;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Showing the checks on the tip of a branch on Forgejo one by one (ADR 195), as GitHub's are (ADR 32).
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
     * The checks on {@code branch} on Forgejo were read, or why that failed.
     */
    record Read(RemoteBranch branch, Either<Failure, ChecksRead> read) implements ChecksMsg {}

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
     * Forgejo was asked to run the failed jobs of the runs of {@code cmd} again, which went as {@code outcomes}.
     */
    record Rerun(RerunCmd cmd, List<RerunCmd.Outcome> outcomes) implements ChecksMsg {}

    /**
     * The user asked to pick the check {@code by} rows after the one picked, one listed later when positive (ADR 160).
     */
    record Stepped(int by) implements ChecksMsg {}

    /**
     * The user clicked the check named {@code name} to pick it (ADR 160).
     */
    record Clicked(String name) implements ChecksMsg {}

    /**
     * The user asked to open the log of the check picked in an editor (ADR 160).
     */
    record LogRequested() implements ChecksMsg {}

    /**
     * The log of a check was read by {@code cmd}, and kept in {@code file}, to open in an editor at {@code line}.
     */
    record LogRead(CheckLogCmd cmd, String file, int line) implements ChecksMsg {}

    /**
     * The user chose {@code editor} in the menu of the editors to open the log of a check in.
     */
    record LogEditorChosen(EditorProgram editor) implements ChecksMsg {}

    /**
     * The user chose no editor to open the log of a check in.
     */
    record LogEditorCancelled() implements ChecksMsg {}

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

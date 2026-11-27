package net.vanfleteren.jazygit.kernel.state.operation;

import static net.vanfleteren.jazygit.core.git.StubGit.executed;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.core.git.model.MergeMode;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutModels;
import net.vanfleteren.jazygit.features.branches.branch.merge.MergeCmd;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.features.github.checks.branches.LoadBranchChecks;
import net.vanfleteren.jazygit.features.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.features.github.state.LoadProtectedBranches;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadBranches;
import net.vanfleteren.jazygit.kernel.state.load.LoadStashes;
import net.vanfleteren.jazygit.kernel.state.load.LoadStatus;
import net.vanfleteren.jazygit.kernel.state.load.LoadWorktrees;
import org.junit.jupiter.api.Test;

/**
 * The tests of OperationUpdateTest with GitHub in the app (ADR 171).
 */
class OperationUpdateGitHubTest {

    private static final MergeCmd MERGE =
            new MergeCmd(new BranchRef.Local(new BranchName("feature")), MergeMode.MERGE_COMMIT);

    private static final InProgress MERGING =
            new InProgress.Merging(new Sha("0123456789012345678901234567890123456789"));

    private static final PushCmd.Push PUSH = new PushCmd.Push(
            new BranchName("feature"), new RemoteBranch("origin", new BranchName("feature")), PushMode.FAST_FORWARD);

    /**
     * The sample model, the features of the app answering what happens in the kernel: GitHub's read from it again.
     */
    private static Model withTheFeatures() {
        return loaded().withReactions(Features.REACTIONS);
    }

    @Test
    void aSuccessLogsItsCommandsReleasesItsBranchClearsTheErrorAndReadsTheRepositoryAgain() {
        Model pushing = TestModels.running(CheckoutModels.withCheckoutError(withTheFeatures(), "foo", "boom"), PUSH);

        Next next = TestModels.done(pushing, PUSH, succeeded(PUSH, "git push origin feature"));

        assertEquals(
                List.of(new LogEntry(PushCmd.PUSH, executed("git push origin feature"), Instant.EPOCH)),
                next.model().outcome().commandLog());
        assertEquals(HashSet.empty(), next.model().jobs().busy().keySet());
        assertEquals(Option.none(), next.model().outcome().error());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        LoadBranches.LISTED,
                        new LoadStashes(),
                        new LoadWorktrees(),
                        new LoadProtectedBranches(),
                        new LoadRepositoryPullRequests(),
                        new LoadBranchChecks()),
                next.cmds());
    }
}

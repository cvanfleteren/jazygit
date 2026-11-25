package net.vanfleteren.jazygit.features.github.create;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.base.SetBaseCmd;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.PushFirstPopup;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.RecordParent;
import net.vanfleteren.jazygit.features.github.stack.StackPopups;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import org.junit.jupiter.api.Test;

/**
 * Creating the pull request of a branch without one to put it on a stack, as the stack menu asks (ADR 148): the base
 * is recorded as its parent, then the pull request is asked about as for any branch without one.
 */
class CreateFirstTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName FEAT_A = new BranchName("feat/a");
    private static final BranchName MAIN = new BranchName("main");

    private static final StackPopups.CreateFirst ON_TOP =
            new StackPopups.CreateFirst(FEATURE, FEAT_A, new PrNumber(96), Option.of(12));

    private static final Model MODEL = TestModels.localBranchesRead(
                    TestModels.loaded(),
                    List.of(MAIN, FEAT_A, FEATURE).map(name -> Fixtures.branch(name, false, new Sha("aaaa"))))
            .model();

    @Test
    void confirmingRecordsTheBaseAsTheParentOfTheBranch() {
        Update.Next confirmed = Update.update(MODEL.openPopup(ON_TOP), new CreatePullRequestMsg.StackRequested());

        assertEquals(List.of(new RecordParent(new SetBaseCmd(FEATURE, Option.of(FEAT_A)))), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
    }

    // feature is on no remote: it is pushed first, as for any branch without a pull request.
    @Test
    void onceRecordedThePullRequestIsAskedAbout() {
        Update.Next recorded = Update.update(
                MODEL,
                new CreatePullRequestMsg.ParentRecorded(
                        new RecordParent(new SetBaseCmd(FEATURE, Option.of(FEAT_A))), List.empty()));

        assertInstanceOf(PushFirstPopup.class, recorded.model().popup().get());
    }

    @Test
    void theQuestionSaysWhereThePullRequestGoes() {
        assertEquals(
                "feature has no pull request yet. Create one on feat/a, recorded as the branch feature is built on, "
                        + "then put it on the top of stack #12?",
                CreatePullRequestDialogs.createFirst(ON_TOP));
        assertEquals(
                "feature has no pull request yet. Create one on feat/a, recorded as the branch feature is built on, "
                        + "then stack it on #96?",
                CreatePullRequestDialogs.createFirst(
                        new StackPopups.CreateFirst(FEATURE, FEAT_A, new PrNumber(96), Option.none())));
    }
}

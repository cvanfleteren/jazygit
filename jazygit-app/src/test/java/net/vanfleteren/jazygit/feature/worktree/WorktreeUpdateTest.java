package net.vanfleteren.jazygit.feature.worktree;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Adding a worktree for a branch, switching to a worktree and removing one.
 */
class WorktreeUpdateTest {

    private static final Branch.Name FEATURE = new Branch.Name("feature");
    private static final Branch.Name TAKEN = new Branch.Name("taken");
    private static final Worktree MAIN = new Worktree(
            Path.of("/repos/app"),
            Option.of(new Sha("aaaa")),
            Option.of(new Branch.Name("main")),
            true,
            true,
            false,
            false);
    private static final Worktree LINKED = new Worktree(
            Path.of("/repos/app-taken"), Option.of(new Sha("1111")), Option.of(TAKEN), false, false, false, false);

    /**
     * {@code main} is checked out in the main worktree, which is shown; {@code taken} in a linked one;
     * {@code feature} in none.
     */
    private static Model model() {
        Model branches = Update.update(
                        loaded(),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                new Branch(FEATURE, false, new Sha("ffff")),
                                new Branch(
                                        TAKEN,
                                        false,
                                        new Sha("1111"),
                                        Instant.EPOCH,
                                        false,
                                        false,
                                        new Tracking.None(),
                                        Option.of(LINKED.path())))))
                .model();
        return Update.update(branches, new LoadMsg.WorktreesLoaded(List.of(MAIN, LINKED)))
                .model();
    }

    @Test
    void aNewWorktreeForABranchStartsOutNextToTheMainOne() {
        Next asked = Update.update(model(), new WorktreeMsg.NewRequested(FEATURE));

        assertEquals(
                Option.of(new NewWorktreePopup(FEATURE, "/repos/app-feature")),
                asked.model().popup());
        assertEquals(
                Option.none(),
                Update.update(asked.model(), new WorktreeMsg.NewCancelled())
                        .model()
                        .popup());
    }

    @Test
    void theCheckedOutBranchAndUnknownOrBusyBranchesGetNoWorktree() {
        Model busy = model().withBusyBranch(new BranchRef.Local(FEATURE), Operation.PUSH);

        assertEquals(
                Option.none(),
                Update.update(model(), new WorktreeMsg.NewRequested(new Branch.Name("main")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(model(), new WorktreeMsg.NewRequested(new Branch.Name("nope")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(busy, new WorktreeMsg.NewRequested(FEATURE))
                        .model()
                        .popup());
    }

    @Test
    void aBranchCheckedOutInAnotherWorktreeGetsNoOtherOneAndTheErrorSaysWhere() {
        Next next = Update.update(model(), new WorktreeMsg.NewRequested(TAKEN));

        assertEquals(
                Option.of(
                        "Creating a worktree for taken failed: it is checked out in the worktree at /repos/app-taken"),
                errorText(next.model()));
    }

    @Test
    void confirmingAddsTheWorktreeWithTheBranchBusyAndABlankLocationKeepsTheDialogOpen() {
        Model asked =
                Update.update(model(), new WorktreeMsg.NewRequested(FEATURE)).model();

        assertEquals(
                List.of(),
                Update.update(asked, new WorktreeMsg.NewConfirmed("  ")).cmds());
        Next confirmed = Update.update(asked, new WorktreeMsg.NewConfirmed(" ~/elsewhere "));

        assertEquals(List.of(new WorktreeCmd.Add(FEATURE, "~/elsewhere")), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                Option.of(Operation.ADD_WORKTREE),
                confirmed.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void anAddedWorktreeShowsTheWorktreesAndReadsTheRepositoryAgain() {
        WorktreeCmd.Add add = new WorktreeCmd.Add(FEATURE, "/repos/app-feature");
        Model adding = Update.start(model(), add).model();

        Next next = Update.update(
                adding, add.succeeded(StubGitProvider.executed("git worktree add --quiet /repos/app-feature feature")));

        assertEquals(BranchesTab.WORKTREES, next.model().repoView().branchesTab());
        assertEquals(Option.none(), next.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(next.cmds().contains(new Cmd.LoadWorktrees()), next.cmds().toString());
    }

    @Test
    void switchingOpensAnotherWorktreeButNotTheOneShown() {
        assertEquals(
                List.of(new RecentReposCmd.SwitchTo("/repos/app-taken")),
                Update.update(model(), new WorktreeMsg.SwitchRequested("/repos/app-taken"))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(model(), new WorktreeMsg.SwitchRequested("/repos/app"))
                        .cmds());
        assertEquals(
                List.of(),
                Update.update(model(), new WorktreeMsg.SwitchRequested("/repos/unknown"))
                        .cmds());
    }

    @Test
    void onlyALinkedWorktreeThatIsNotShownCanBeRemoved() {
        Next asked = Update.update(model(), new WorktreeMsg.RemoveRequested("/repos/app-taken"));

        assertEquals(
                Option.of(new RemoveWorktreePopup("/repos/app-taken", "app-taken")),
                asked.model().popup());
        assertEquals(
                Option.none(),
                Update.update(model(), new WorktreeMsg.RemoveRequested("/repos/app"))
                        .model()
                        .popup());
        assertEquals(
                List.of(new WorktreeCmd.Remove("/repos/app-taken", false)),
                Update.update(asked.model(), new WorktreeMsg.RemoveConfirmed()).cmds());
        assertEquals(
                Option.none(),
                Update.update(asked.model(), new WorktreeMsg.RemoveCancelled())
                        .model()
                        .popup());
    }

    @Test
    void aWorktreeWithChangesIsOnlyRemovedOnceTheUserForcesIt() {
        WorktreeCmd.Remove remove = new WorktreeCmd.Remove("/repos/app-taken", false);

        Next refused = Update.update(
                model(),
                new WorktreeMsg.NeedsForce(remove, StubGitProvider.executed("git worktree remove /repos/app-taken")));

        assertEquals(
                Option.of(new ForceRemoveWorktreePopup("/repos/app-taken", "app-taken")),
                refused.model().popup());
        // The removal that ran is logged like any other.
        assertEquals(
                List.of("git worktree remove /repos/app-taken"),
                refused.model()
                        .outcome()
                        .commandLog()
                        .flatMap(entry -> entry.commands())
                        .map(command -> command.line()));
        assertEquals(
                List.of(new WorktreeCmd.Remove("/repos/app-taken", true)),
                Update.update(refused.model(), new WorktreeMsg.ForceConfirmed()).cmds());
        assertEquals(
                Option.none(),
                Update.update(refused.model(), new WorktreeMsg.ForceCancelled())
                        .model()
                        .popup());
    }

    @Test
    void aLeadingTildeIsTheHomeDirectory() {
        assertEquals(Path.of(System.getProperty("user.home"), "x"), WorktreeCmd.path("~/x"));
        assertEquals(Path.of("rel/x"), WorktreeCmd.path("rel/x"));
        assertEquals(Path.of("~x"), WorktreeCmd.path("~x"));
    }
}

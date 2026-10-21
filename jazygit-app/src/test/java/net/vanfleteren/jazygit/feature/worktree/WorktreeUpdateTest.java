package net.vanfleteren.jazygit.feature.worktree;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchPopup;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.add.NewBranch;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreeBranchPopup;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.add.SwitchToAddedPopup;
import net.vanfleteren.jazygit.feature.worktree.lock.LockWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.lock.LockWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.prune.PruneWorktreesCmd;
import net.vanfleteren.jazygit.feature.worktree.prune.PruneWorktreesMsg;
import net.vanfleteren.jazygit.feature.worktree.remove.ForceRemoveWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeMsg;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.process.ExecutedCommand;
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
            Option.none(),
            false);
    private static final Worktree LINKED = new Worktree(
            Path.of("/repos/app-taken"),
            Option.of(new Sha("1111")),
            Option.of(TAKEN),
            false,
            false,
            Option.none(),
            false);

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
        Next asked = Update.update(model(), new AddWorktreeMsg.Requested(FEATURE));

        assertEquals(
                Option.of(new NewWorktreePopup(FEATURE, "/repos/app-feature")),
                asked.model().popup());
        assertEquals(
                Option.none(),
                Update.update(asked.model(), new AddWorktreeMsg.Cancelled())
                        .model()
                        .popup());
    }

    @Test
    void forTheCheckedOutBranchANewBranchMadeFromItIsAskedFor() {
        Branch.Name main = new Branch.Name("main");

        Next next = Update.update(model(), new AddWorktreeMsg.Requested(main));

        assertEquals(
                Option.of(new NewWorktreeBranchPopup("main", "", new NewBranch("main", Option.of(main), false))),
                next.model().popup());
    }

    @Test
    void aNewBranchMadeFromABranchIsNamedThenGivenAWorktreeThatCreatesIt() {
        Next named = Update.update(model(), new AddWorktreeMsg.NewBranchRequested(FEATURE));
        NewBranch fromFeature = new NewBranch("feature", Option.of(FEATURE), false);
        assertEquals(
                Option.of(new NewWorktreeBranchPopup("feature", "", fromFeature)),
                named.model().popup());

        // A name that is no branch name keeps the dialog open.
        Model blank = typed(named.model(), " ");
        assertEquals(
                blank.popup(),
                Update.update(blank, new AddWorktreeMsg.NameConfirmed()).model().popup());
        Next located = Update.update(typed(named.model(), "topic"), new AddWorktreeMsg.NameConfirmed());
        Branch.Name topic = new Branch.Name("topic");
        assertEquals(
                Option.of(new NewWorktreePopup(topic, "/repos/app-topic", Option.of(fromFeature))),
                located.model().popup());

        assertEquals(
                List.of(new AddWorktreeCmd(topic, "/repos/app-topic", Option.of(fromFeature))),
                Update.update(located.model(), new AddWorktreeMsg.Confirmed()).cmds());
    }

    @Test
    void aNewBranchFromARemoteOneFollowsItAndStartsOutNamedAsIt() {
        RemoteBranch remote = new RemoteBranch("origin", new Branch.Name("shared"));
        Model known = Update.update(
                        model(),
                        new LoadMsg.RemoteBranchesLoaded(
                                List.of(new RemoteRef(remote, new Sha("5555"), Instant.EPOCH))))
                .model();

        Next named = Update.update(known, new AddWorktreeMsg.NewBranchAtRemoteRequested(remote));

        assertEquals(
                Option.of(new NewWorktreeBranchPopup(
                        "origin/shared", "shared", new NewBranch("refs/remotes/origin/shared", Option.none(), true))),
                named.model().popup());
    }

    @Test
    void addingANewBranchRunsGitWithItsStartAndWhetherItFollowsIt() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktreeWithNewBranch(
                    Path location, Branch.Name name, String startPoint, Option<Branch.Name> parent, boolean track) {
                ran.add(location + " " + name.value() + " " + startPoint + " " + parent + " " + track);
                return Either.right(List.empty());
            }
        };

        new AddWorktreeCmd(
                        new Branch.Name("topic"),
                        "/repos/app-topic",
                        Option.of(new NewBranch("feature", Option.of(FEATURE), false)))
                .perform(git);

        assertEquals(java.util.List.of("/repos/app-topic topic feature Some(Name[value=feature]) false"), ran);
    }

    @Test
    void unknownOrBusyBranchesGetNoWorktree() {
        Model busy = model().withBusyBranch(new BranchRef.Local(FEATURE), PushCmd.PUSH);

        assertEquals(
                Option.none(),
                Update.update(model(), new AddWorktreeMsg.Requested(new Branch.Name("nope")))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(busy, new AddWorktreeMsg.Requested(FEATURE))
                        .model()
                        .popup());
    }

    @Test
    void aBranchCheckedOutInAnotherWorktreeGetsNoOtherOneAndTheErrorSaysWhere() {
        Next next = Update.update(model(), new AddWorktreeMsg.Requested(TAKEN));

        assertEquals(
                Option.of(
                        "Creating a worktree for taken failed: it is checked out in the worktree at /repos/app-taken"),
                errorText(next.model()));
    }

    @Test
    void confirmingAddsTheWorktreeWithTheBranchBusyAndABlankLocationKeepsTheDialogOpen() {
        Model asked =
                Update.update(model(), new AddWorktreeMsg.Requested(FEATURE)).model();

        assertEquals(
                List.of(),
                Update.update(typed(asked, "  "), new AddWorktreeMsg.Confirmed())
                        .cmds());
        Next confirmed = Update.update(typed(asked, " ~/elsewhere "), new AddWorktreeMsg.Confirmed());

        assertEquals(List.of(new AddWorktreeCmd(FEATURE, "~/elsewhere")), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                Option.of(AddWorktreeCmd.ADD_WORKTREE),
                confirmed.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void anAddedWorktreeShowsTheWorktreesReadsTheRepositoryAgainAndOffersToSwitchToIt() {
        AddWorktreeCmd add = new AddWorktreeCmd(FEATURE, "../app-feature");
        Model adding = Update.start(model(), add).model();

        Next next = Update.update(
                adding,
                new AddWorktreeMsg.Added(
                        add,
                        "/repos/app-feature",
                        StubGitProvider.executed("git worktree add --quiet /repos/app-feature feature")));

        assertEquals(BranchesTab.WORKTREES, next.model().repoView().branchesTab());
        assertEquals(Option.none(), next.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(next.cmds().contains(new Cmd.LoadWorktrees()), next.cmds().toString());
        assertEquals(
                Option.of(new SwitchToAddedPopup(FEATURE, "/repos/app-feature")),
                next.model().popup());

        assertEquals(
                List.of(RecentReposCmd.SwitchTo.worktree("/repos/app-feature")),
                Update.update(next.model(), new AddWorktreeMsg.SwitchConfirmed())
                        .cmds());
        Next stayed = Update.update(next.model(), new AddWorktreeMsg.SwitchCancelled());
        assertEquals(List.of(), stayed.cmds());
        assertEquals(Option.none(), stayed.model().popup());
    }

    @Test
    void anAddedWorktreeIsWhereTheLocationTakenFromTheWorkingTreeIs() {
        Env env = new Env(
                new StubGitProvider() {
                    @Override
                    public Path workTree() {
                        return Path.of("/repos/app");
                    }
                },
                net.vanfleteren.jazygit.gh.StubGitHub.empty());

        Msg added = new AddWorktreeCmd(FEATURE, "../app-feature").run(env);

        assertEquals("/repos/app-feature", ((AddWorktreeMsg.Added) added).location());
    }

    @Test
    void switchingOpensAnotherWorktreeButNotTheOneShown() {
        assertEquals(
                // It is shown on the worktrees, focused.
                List.of(new RecentReposCmd.SwitchTo(
                        "/repos/app-taken", new Landing(BranchesTab.WORKTREES, Option.of(Scopes.BRANCHES.id())))),
                Update.update(model(), new SwitchWorktreeMsg.Requested("/repos/app-taken"))
                        .cmds());
        Next shown = Update.update(model(), new SwitchWorktreeMsg.Requested("/repos/app"));
        assertEquals(List.of(), shown.cmds());
        assertEquals(Option.of("Worktree app is the one shown already"), errorText(shown.model()));
        assertEquals(
                List.of(),
                Update.update(model(), new SwitchWorktreeMsg.Requested("/repos/unknown"))
                        .cmds());
    }

    @Test
    void onlyALinkedWorktreeThatIsNotShownCanBeRemoved() {
        Next asked = Update.update(model(), new RemoveWorktreeMsg.Requested("/repos/app-taken"));

        assertEquals(
                Option.of(new RemoveWorktreePopup("/repos/app-taken", "app-taken", Option.of(TAKEN))),
                asked.model().popup());
        assertEquals(
                List.of(new RemoveWorktreeCmd("/repos/app-taken", false)),
                Update.update(asked.model(), new RemoveWorktreeMsg.Chosen(false))
                        .cmds());
        assertEquals(
                Option.none(),
                Update.update(asked.model(), new RemoveWorktreeMsg.Cancelled())
                        .model()
                        .popup());
    }

    @Test
    void itsBranchCanBeDeletedWithTheWorktree() {
        Next asked = Update.update(model(), new RemoveWorktreeMsg.Requested("/repos/app-taken"));

        Next chosen = Update.update(asked.model(), new RemoveWorktreeMsg.Chosen(true));
        assertEquals(List.of(new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN))), chosen.cmds());
        assertEquals(Option.none(), chosen.model().popup());
    }

    @Test
    void withoutABranchOrWhileItIsBusyOnlyTheWorktreeIsRemoved() {
        Worktree detached =
                new Worktree(LINKED.path(), LINKED.head(), Option.none(), false, false, Option.none(), false);
        Model asked = Update.update(worktrees(MAIN, detached), new RemoveWorktreeMsg.Requested("/repos/app-taken"))
                .model();
        assertEquals(Option.of(new RemoveWorktreePopup("/repos/app-taken", "app-taken", Option.none())), asked.popup());
        assertEquals(
                List.of(),
                Update.update(asked, new RemoveWorktreeMsg.Chosen(true)).cmds());

        Model busy = Update.update(model(), new RemoveWorktreeMsg.Requested("/repos/app-taken"))
                .model()
                .withBusyBranch(new BranchRef.Local(TAKEN), PushCmd.PUSH);
        assertEquals(
                List.of(),
                Update.update(busy, new RemoveWorktreeMsg.Chosen(true)).cmds());
        assertEquals(
                List.of(new RemoveWorktreeCmd("/repos/app-taken", false)),
                Update.update(busy, new RemoveWorktreeMsg.Chosen(false)).cmds());
    }

    @Test
    void deletingTheBranchOfAnotherWorktreeOffersToRemoveThatWorktreeFirst() {
        Model asked =
                Update.update(model(), new DeleteBranchMsg.Requested(TAKEN)).model();

        assertEquals(Option.of(new DeleteBranchPopup(TAKEN, Option.of("app-taken"))), asked.popup());
        // Deleting it locally on its own git would refuse.
        assertEquals(
                List.of(),
                Update.update(asked, new DeleteBranchMsg.Chosen(DeleteScope.LOCAL))
                        .cmds());
        Next chosen = Update.update(asked, new DeleteBranchMsg.WithWorktreeChosen());
        assertEquals(List.of(new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN))), chosen.cmds());
        assertEquals(Option.none(), chosen.model().popup());
    }

    @Test
    void aBranchWhoseWorktreeCannotBeRemovedIsStillNotDeletedAndTheErrorSaysWhere() {
        Worktree locked =
                new Worktree(LINKED.path(), LINKED.head(), LINKED.branch(), false, false, Option.of(""), false);

        Next next = Update.update(worktrees(MAIN, locked), new DeleteBranchMsg.Requested(TAKEN));

        assertEquals(Option.none(), next.model().popup(DeleteBranchPopup.class));
        assertEquals(
                Option.of("Deleting branch taken failed: it is checked out in the worktree at /repos/app-taken"),
                errorText(next.model()));
    }

    @Test
    void theBranchIsDeletedLocallyOnceTheWorktreeIsRemovedAndNotWhenRemovingItFails() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
                ran.add("remove " + location + " " + force);
                return location.toString().endsWith("changed")
                        ? Either.left(new GitFailure<>(new GitError.WorktreeHasChanges("changed"), List.empty()))
                        : Either.right(List.empty());
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(
                    Branch.Name name, RemoteBranch remote, DeleteScope scope) {
                ran.add("delete " + name.value() + " " + scope);
                return Either.right(List.empty());
            }
        };

        new RemoveWorktreeCmd("/repos/app-taken", true, Option.of(TAKEN)).perform(git);
        assertEquals(java.util.List.of("remove /repos/app-taken true", "delete taken LOCAL"), ran);

        ran.clear();
        RemoveWorktreeCmd changed = new RemoveWorktreeCmd("/repos/app-changed", false, Option.of(TAKEN));
        assertEquals(
                new RemoveWorktreeMsg.NeedsForce(changed, List.empty()),
                changed.run(new Env(git, net.vanfleteren.jazygit.gh.StubGitHub.empty())));
        assertEquals(java.util.List.of("remove /repos/app-changed false"), ran);
    }

    @Test
    void forcingTheRemovalStillDeletesTheBranch() {
        RemoveWorktreeCmd remove = new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN));

        Next refused = Update.update(model(), new RemoveWorktreeMsg.NeedsForce(remove, List.of()));

        assertEquals(
                List.of(new RemoveWorktreeCmd("/repos/app-taken", true, Option.of(TAKEN))),
                Update.update(refused.model(), new RemoveWorktreeMsg.ForceConfirmed())
                        .cmds());
    }

    @Test
    void theMainWorktreeTheOneShownAndALockedOneAreNotRemovedAndTheErrorSaysWhy() {
        Next main = Update.update(model(), new RemoveWorktreeMsg.Requested("/repos/app"));
        assertEquals(Option.none(), main.model().popup(RemoveWorktreePopup.class));
        assertEquals(
                Option.of("Worktree app is the main one, which holds the repository; it cannot be removed"),
                errorText(main.model()));

        Worktree mainNotShown =
                new Worktree(MAIN.path(), MAIN.head(), MAIN.branch(), true, false, Option.none(), false);
        Worktree shownLinked = new Worktree(
                Path.of("/repos/app-shown"),
                Option.of(new Sha("2222")),
                Option.none(),
                false,
                true,
                Option.none(),
                false);
        Next shown = Update.update(
                worktrees(mainNotShown, shownLinked), new RemoveWorktreeMsg.Requested("/repos/app-shown"));
        assertEquals(Option.none(), shown.model().popup(RemoveWorktreePopup.class));
        assertEquals(
                Option.of("Worktree app-shown is the one shown; switch to another one to remove it"),
                errorText(shown.model()));

        // Said before asking, rather than after git refuses.
        Worktree lockedLinked =
                new Worktree(LINKED.path(), LINKED.head(), LINKED.branch(), false, false, Option.of(""), false);
        Next locked = Update.update(worktrees(MAIN, lockedLinked), new RemoveWorktreeMsg.Requested("/repos/app-taken"));
        assertEquals(Option.none(), locked.model().popup(RemoveWorktreePopup.class));
        assertEquals(
                Option.of("Worktree app-taken is locked, which keeps it from being removed"),
                errorText(locked.model()));
        Worktree lockedWithReason =
                new Worktree(LINKED.path(), LINKED.head(), LINKED.branch(), false, false, Option.of("on usb"), false);
        assertEquals(
                Option.of("Worktree app-taken is locked, which keeps it from being removed: on usb"),
                errorText(Update.update(
                                worktrees(MAIN, lockedWithReason), new RemoveWorktreeMsg.Requested("/repos/app-taken"))
                        .model()));
    }

    @Test
    void withoutAMainWorktreeANewOneStartsOutNextToTheOneShownAndATakenLocationGetsANumber() {
        // Worktrees next to a bare repository: none is the main one.
        Worktree shown = new Worktree(
                Path.of("/repos/proj/main"),
                Option.of(new Sha("aaaa")),
                Option.of(new Branch.Name("main")),
                false,
                true,
                Option.none(),
                false);
        assertEquals(
                Option.of(new NewWorktreePopup(FEATURE, "/repos/proj/feature")),
                Update.update(worktrees(shown, LINKED), new AddWorktreeMsg.Requested(FEATURE))
                        .model()
                        .popup());

        Worktree taken = new Worktree(
                Path.of("/repos/app-feature"),
                Option.of(new Sha("3333")),
                Option.none(),
                false,
                false,
                Option.none(),
                false);
        assertEquals(
                Option.of(new NewWorktreePopup(FEATURE, "/repos/app-feature-2")),
                Update.update(worktrees(MAIN, LINKED, taken), new AddWorktreeMsg.Requested(FEATURE))
                        .model()
                        .popup());
    }

    @Test
    void aWorktreeWhoseDirectoryIsGoneIsNotSwitchedTo() {
        Worktree missing =
                new Worktree(LINKED.path(), LINKED.head(), LINKED.branch(), false, false, Option.none(), true);

        Next next = Update.update(worktrees(MAIN, missing), new SwitchWorktreeMsg.Requested("/repos/app-taken"));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of("The directory of worktree app-taken is gone; prune it with p"), errorText(next.model()));
    }

    @Test
    void pruningRunsOnlyWhenAWorktreeHasItsDirectoryGone() {
        Next nothing = Update.update(model(), new PruneWorktreesMsg.Requested());
        assertEquals(List.of(), nothing.cmds());
        assertEquals(
                Option.of("No worktree has its directory gone; there is nothing to prune"), errorText(nothing.model()));

        Worktree missing =
                new Worktree(LINKED.path(), LINKED.head(), LINKED.branch(), false, false, Option.none(), true);
        assertEquals(
                List.of(new PruneWorktreesCmd()),
                Update.update(worktrees(MAIN, missing), new PruneWorktreesMsg.Requested())
                        .cmds());
    }

    @Test
    void lockingLocksAnUnlockedWorktreeUnlocksALockedOneAndRefusesTheMainOne() {
        assertEquals(
                List.of(new LockWorktreeCmd("/repos/app-taken", true)),
                Update.update(model(), new LockWorktreeMsg.Requested("/repos/app-taken"))
                        .cmds());
        Worktree locked =
                new Worktree(LINKED.path(), LINKED.head(), LINKED.branch(), false, false, Option.of(""), false);
        assertEquals(
                List.of(new LockWorktreeCmd("/repos/app-taken", false)),
                Update.update(worktrees(MAIN, locked), new LockWorktreeMsg.Requested("/repos/app-taken"))
                        .cmds());

        Next main = Update.update(model(), new LockWorktreeMsg.Requested("/repos/app"));
        assertEquals(List.of(), main.cmds());
        assertEquals(Option.of("The main worktree cannot be locked"), errorText(main.model()));
    }

    /**
     * {@link #model()} with {@code worktrees} instead.
     */
    private static Model worktrees(Worktree... worktrees) {
        return Update.update(model(), new LoadMsg.WorktreesLoaded(List.of(worktrees)))
                .model();
    }

    @Test
    void aWorktreeWithChangesIsOnlyRemovedOnceTheUserForcesIt() {
        RemoveWorktreeCmd remove = new RemoveWorktreeCmd("/repos/app-taken", false);

        Next refused = Update.update(
                model(),
                new RemoveWorktreeMsg.NeedsForce(
                        remove, StubGitProvider.executed("git worktree remove /repos/app-taken")));

        assertEquals(
                Option.of(new ForceRemoveWorktreePopup(remove, "app-taken")),
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
                List.of(new RemoveWorktreeCmd("/repos/app-taken", true)),
                Update.update(refused.model(), new RemoveWorktreeMsg.ForceConfirmed())
                        .cmds());
        assertEquals(
                Option.none(),
                Update.update(refused.model(), new RemoveWorktreeMsg.ForceCancelled())
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

package net.vanfleteren.jazygit.feature.worktree;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchDialogs;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchPopup;
import net.vanfleteren.jazygit.feature.branch.delete.UnlockWorktreePopup;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.add.NewBranch;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreeBranchPopup;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreeStartPopup;
import net.vanfleteren.jazygit.feature.worktree.add.SwitchToAddedPopup;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.feature.worktree.lock.LockWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.lock.LockWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.prune.PruneWorktreesCmd;
import net.vanfleteren.jazygit.feature.worktree.prune.PruneWorktreesMsg;
import net.vanfleteren.jazygit.feature.worktree.remove.ForceRemoveWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeMsg;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * Adding a worktree for a branch, switching to a worktree and removing one.
 */
class WorktreeUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName TAKEN = new BranchName("taken");
    private static final Worktree MAIN = new Worktree(
            Path.of("/repos/app"),
            Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("aaaa")))),
            true,
            true,
            Option.none(),
            false);
    private static final Worktree LINKED = new Worktree(
            Path.of("/repos/app-taken"),
            Option.of(new Head.OnBranch(TAKEN, Option.of(new Sha("1111")))),
            false,
            false,
            Option.none(),
            false);

    /**
     * {@code main} is checked out in the main worktree, which is shown; {@code taken} in a linked one;
     * {@code feature} in none.
     */
    private static Model model() {
        return model(false);
    }

    /**
     * {@link #model()}, with {@code taken} also on its remote when {@code takenOnRemote}.
     */
    private static Model model(boolean takenOnRemote) {
        Model branches = TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(new BranchName("main"), true, new Sha("aaaa")),
                                Fixtures.branch(FEATURE, false, new Sha("ffff")),
                                new Branch(
                                        TAKEN,
                                        false,
                                        new Sha("1111"),
                                        Instant.EPOCH,
                                        takenOnRemote,
                                        false,
                                        new Tracking.None(),
                                        Option.of(LINKED.path()))))
                .model();
        return Update.update(branches, new LoadMsg.WorktreesLoaded(List.of(MAIN, LINKED)))
                .model();
    }

    @Test
    void aNewWorktreeForABranchStartsOutNextToTheMainOne() {
        Next asked = Update.update(model(), new AddWorktreeMsg.Requested(FEATURE));

        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Existing(FEATURE), "/repos/app-feature")),
                asked.model().popup());
        assertEquals(
                Option.none(),
                Update.update(asked.model(), new Msg.PopupDismissed(NewWorktreePopup.class))
                        .model()
                        .popup());
    }

    @Test
    void forTheCheckedOutBranchANewBranchMadeFromItIsAskedFor() {
        BranchName main = new BranchName("main");

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
        BranchName topic = new BranchName("topic");
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Created(topic, fromFeature), "/repos/app-topic")),
                located.model().popup());

        assertEquals(
                List.of(new AddWorktreeCmd(new WorktreeSource.Created(topic, fromFeature), "/repos/app-topic")),
                Update.update(located.model(), new AddWorktreeMsg.Confirmed()).cmds());
    }

    @Test
    void aNewBranchFromARemoteOneFollowsItAndStartsOutNamedAsIt() {
        RemoteBranch remote = new RemoteBranch("origin", new BranchName("shared"));
        Model known = TestModels.remoteBranchesRead(
                        model(), List.of(new RemoteRef(remote, new Sha("5555"), Instant.EPOCH)))
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
                    Path location, BranchName name, String startPoint, Option<BranchName> parent, boolean track) {
                ran.add(location + " " + name.value() + " " + startPoint + " " + parent.map(BranchName::value) + " "
                        + track);
                return Either.right(List.empty());
            }
        };

        new AddWorktreeCmd(
                        new WorktreeSource.Created(
                                new BranchName("topic"), new NewBranch("feature", Option.of(FEATURE), false)),
                        "/repos/app-topic")
                .run(new Env(git, net.vanfleteren.jazygit.gh.StubGitHub.empty()));

        assertEquals(java.util.List.of("/repos/app-topic topic feature Some(feature) false"), ran);
    }

    /**
     * The worktrees asked for a new worktree, starting where {@code start} says.
     */
    private static Model startedAt(Model model, String start) {
        Model asked = Update.update(model, new AddWorktreeMsg.StartRequested()).model();
        return Update.update(typed(asked, start), new AddWorktreeMsg.StartConfirmed())
                .model();
    }

    @Test
    void aNewWorktreeFromTheWorktreesStartsAtTheCheckedOutBranchAndABlankStartKeepsTheDialogOpen() {
        Next asked = Update.update(model(), new AddWorktreeMsg.StartRequested());

        assertEquals(Option.of(new NewWorktreeStartPopup("main")), asked.model().popup());
        Model blank = typed(asked.model(), "  ");
        assertEquals(
                blank.popup(),
                Update.update(blank, new AddWorktreeMsg.StartConfirmed())
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(asked.model(), new Msg.PopupDismissed(NewWorktreeStartPopup.class))
                        .model()
                        .popup());
    }

    @Test
    void fromALocalBranchItIsOnANewBranchOrLeftBlankOnThatBranch() {
        Model started = startedAt(model(), "feature");
        NewBranch fromFeature = new NewBranch("feature", Option.of(FEATURE), false);

        assertEquals(
                Option.of(new NewWorktreeBranchPopup(
                        "feature", "", fromFeature, Option.of(new WorktreeSource.Existing(FEATURE)))),
                started.popup());
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Existing(FEATURE), "/repos/app-feature")),
                Update.update(started, new AddWorktreeMsg.NameConfirmed())
                        .model()
                        .popup());
        BranchName topic = new BranchName("topic");
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Created(topic, fromFeature), "/repos/app-topic")),
                Update.update(typed(started, "topic"), new AddWorktreeMsg.NameConfirmed())
                        .model()
                        .popup());
    }

    @Test
    void fromABranchCheckedOutSomewhereOrACommitItIsLeftBlankOnNoBranch() {
        // The worktree of taken is at /repos/app-taken already.
        for (var start : List.of(
                io.vavr.Tuple.of("main", "/repos/app-main"),
                io.vavr.Tuple.of("taken", "/repos/app-taken-2"),
                io.vavr.Tuple.of("abc123", "/repos/app-abc123"))) {
            Model started = startedAt(model(), start._1);

            assertEquals(
                    Option.of(new WorktreeSource.Detached(start._1)),
                    started.popup(NewWorktreeBranchPopup.class).flatMap(NewWorktreeBranchPopup::ifBlank));
            assertEquals(
                    Option.of(new NewWorktreePopup(new WorktreeSource.Detached(start._1), start._2)),
                    Update.update(started, new AddWorktreeMsg.NameConfirmed())
                            .model()
                            .popup());
        }
    }

    @Test
    void fromARemoteBranchTheNewBranchFollowsItAndStartsOutNamedAsItButFromACommitItFollowsNone() {
        RemoteBranch other = new RemoteBranch("origin", new BranchName("other"));
        RemoteBranch remote = new RemoteBranch("origin", new BranchName("shared"));
        Model known = TestModels.remoteBranchesRead(
                        model(),
                        List.of(
                                new RemoteRef(other, new Sha("4444"), Instant.EPOCH),
                                new RemoteRef(remote, new Sha("5555"), Instant.EPOCH)))
                .model();

        assertEquals(
                Option.of(new NewWorktreeBranchPopup(
                        "origin/shared",
                        "shared",
                        new NewBranch("refs/remotes/origin/shared", Option.none(), true),
                        Option.of(new WorktreeSource.Detached("origin/shared")))),
                startedAt(known, "origin/shared").popup());
        assertEquals(
                Option.of(new NewWorktreeBranchPopup(
                        "abc123",
                        "",
                        new NewBranch("abc123", Option.none(), false),
                        Option.of(new WorktreeSource.Detached("abc123")))),
                startedAt(known, "abc123").popup());
    }

    private static PullRequestSummary pullRequest() {
        return new PullRequestSummary(
                "owner/repo",
                9,
                "Title",
                "bob",
                "patch-1",
                false,
                true,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9");
    }

    @Test
    void aDetachedWorktreeAndOneForAPullRequestRunGitAndGhInTheNewWorktree() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> addDetachedWorktree(
                    Path location, String startPoint) {
                ran.add("add " + location + " " + startPoint);
                return Either.right(StubGitProvider.executed("git worktree add"));
            }
        };
        StubGitHub gitHub = new StubGitHub() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number, Path in) {
                ran.add("gh " + number + " " + in);
                return Either.right(StubGitProvider.executed("gh pr checkout 9"));
            }
        };

        new AddWorktreeCmd(new WorktreeSource.Detached("abc123"), "/repos/app-abc123").run(new Env(git, gitHub));
        Msg added = new AddWorktreeCmd(new WorktreeSource.PullRequest(pullRequest()), "/repos/app-pr-9")
                .run(new Env(git, gitHub));

        assertEquals(
                java.util.List.of("add /repos/app-abc123 abc123", "add /repos/app-pr-9 HEAD", "gh 9 /repos/app-pr-9"),
                ran);
        assertEquals(
                List.of("git worktree add", "gh pr checkout 9"),
                ((AddWorktreeMsg.Added) added).commands().map(ExecutedCommand::line));
    }

    @Test
    void whenGhFailsTheWorktreeOfThePullRequestIsRemovedAgain() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
                ran.add("remove " + location + " " + force);
                return Either.right(StubGitProvider.executed("git worktree remove --force"));
            }
        };
        StubGitHub fails = new StubGitHub() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number, Path in) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("no such pull request"), 1),
                        StubGitProvider.executed("gh pr checkout 9")));
            }
        };

        Msg failed = new AddWorktreeCmd(new WorktreeSource.PullRequest(pullRequest()), "/repos/app-pr-9")
                .run(new Env(git, fails));

        assertEquals(java.util.List.of("remove /repos/app-pr-9 true"), ran);
        assertTrue(failed instanceof OperationMsg.Failed, failed.toString());
    }

    @Test
    void whenRemovingTheWorktreeOfThePullRequestFailsTooThatIsSaid() {
        GitError.CommandFailed locked = new GitError.CommandFailed(Option.of("fatal: cannot remove"), 128, "");
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> addDetachedWorktree(
                    Path location, String startPoint) {
                return Either.right(StubGitProvider.executed("git worktree add"));
            }

            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
                return Either.left(new GitFailure<>(locked, StubGitProvider.executed("git worktree remove --force")));
            }
        };
        StubGitHub fails = new StubGitHub() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number, Path in) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("no such pull request"), 1),
                        StubGitProvider.executed("gh pr checkout 9")));
            }
        };

        Msg failed = new AddWorktreeCmd(new WorktreeSource.PullRequest(pullRequest()), "/repos/app-pr-9")
                .run(new Env(git, fails));

        Failure failure = ((OperationMsg.Failed) failed).failure();
        assertEquals(
                List.of("git worktree add", "gh pr checkout 9", "git worktree remove --force"),
                failure.commands().map(ExecutedCommand::line));
        assertEquals(
                new Failure.GitHub(
                        new GhError.CommandFailed(Option.of("no such pull request"), 1),
                        failure.commands(),
                        Option.of(locked)),
                failure);
    }

    @Test
    void aStartThatGitWouldTakeForAnOptionKeepsTheDialogOpen() {
        Model typed = typed(
                Update.update(model(), new AddWorktreeMsg.StartRequested()).model(), "--no-checkout");

        assertEquals(
                typed.popup(),
                Update.update(typed, new AddWorktreeMsg.StartConfirmed())
                        .model()
                        .popup());
    }

    @Test
    void unknownOrBusyBranchesGetNoWorktree() {
        Model busy = model().withBusyBranch(new BranchRef.Local(FEATURE), PushCmd.PUSH);

        assertEquals(
                Option.none(),
                Update.update(model(), new AddWorktreeMsg.Requested(new BranchName("nope")))
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

        assertEquals(
                List.of(new AddWorktreeCmd(new WorktreeSource.Existing(FEATURE), "~/elsewhere")), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                Option.of(AddWorktreeCmd.ADD_WORKTREE),
                confirmed.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void anAddedWorktreeShowsTheWorktreesReadsTheRepositoryAgainAndOffersToSwitchToIt() {
        AddWorktreeCmd add = new AddWorktreeCmd(new WorktreeSource.Existing(FEATURE), "../app-feature");
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
                Option.of(new SwitchToAddedPopup("feature", "/repos/app-feature")),
                next.model().popup());

        assertEquals(
                List.of(RecentReposCmd.SwitchTo.worktree("/repos/app-feature")),
                Update.update(next.model(), new AddWorktreeMsg.SwitchConfirmed())
                        .cmds());
        Next stayed = Update.update(next.model(), new Msg.PopupDismissed(SwitchToAddedPopup.class));
        assertEquals(List.of(), stayed.cmds());
        assertEquals(Option.none(), stayed.model().popup());
    }

    @Test
    void anAddedWorktreeIsWhereTheLocationTakenFromTheWorkingTreeIs() {
        Env env = new Env(
                new StubGitProvider() {
                    @Override
                    public Path worktree() {
                        return Path.of("/repos/app");
                    }
                },
                net.vanfleteren.jazygit.gh.StubGitHub.empty());

        Msg added = new AddWorktreeCmd(new WorktreeSource.Existing(FEATURE), "../app-feature").run(env);

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
                Update.update(asked.model(), new Msg.PopupDismissed(RemoveWorktreePopup.class))
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
        Worktree detached = new Worktree(
                LINKED.path(), Option.of(new Head.Detached(new Sha("1111"))), false, false, Option.none(), false);
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
    void aBranchOnItsRemoteInAnotherWorktreeCanBeDeletedOnTheRemoteOrWithThatWorktree() {
        Model asked =
                Update.update(model(true), new DeleteBranchMsg.Requested(TAKEN)).model();
        DeleteBranchPopup popup = asked.popup(DeleteBranchPopup.class).get();

        assertEquals(
                List.of('r', 'w'),
                DeleteBranchDialogs.DELETE_BRANCH
                        .choices()
                        .filter(choice -> choice.shown().test(asked, popup)
                                && choice.enabled().test(asked, popup))
                        .map(ChoiceDialog.Choice::key));
        assertEquals(
                List.of(new DeleteBranchCmd.DeleteBranch(
                        TAKEN, new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, TAKEN), DeleteScope.REMOTE)),
                Update.update(asked, new DeleteBranchMsg.Chosen(DeleteScope.REMOTE))
                        .cmds());
    }

    @Test
    void aBranchWhoseWorktreeCannotBeRemovedIsStillNotDeletedAndTheErrorSaysWhere() {
        Worktree mainElsewhere = new Worktree(LINKED.path(), LINKED.head(), true, false, Option.none(), false);

        Next next = Update.update(worktrees(MAIN, mainElsewhere), new DeleteBranchMsg.Requested(TAKEN));

        assertEquals(Option.none(), next.model().popup(DeleteBranchPopup.class));
        assertEquals(
                Option.of("Deleting branch taken failed: it is checked out in the worktree at /repos/app-taken"),
                errorText(next.model()));
    }

    @Test
    void aBranchWhoseWorktreeIsLockedIsDeletedWithItOnceTheUserAgreesToUnlockIt() {
        Worktree locked = new Worktree(LINKED.path(), LINKED.head(), false, false, Option.of("on a usb stick"), false);
        Model asked = Update.update(worktrees(MAIN, locked), new DeleteBranchMsg.Requested(TAKEN))
                .model();
        assertEquals(Option.of(new DeleteBranchPopup(TAKEN, Option.of("app-taken"))), asked.popup());

        Next chosen = Update.update(asked, new DeleteBranchMsg.WithWorktreeChosen());
        UnlockWorktreePopup unlock = new UnlockWorktreePopup(TAKEN, "/repos/app-taken", "app-taken", "on a usb stick");
        assertEquals(Option.of(unlock), chosen.model().popup());
        assertEquals(List.of(), chosen.cmds());
        assertEquals(
                "The worktree at /repos/app-taken is locked: on a usb stick. Unlock it, so that it can be removed and"
                        + " branch taken deleted?",
                DeleteBranchDialogs.UNLOCK_WORKTREE.message().apply(unlock));

        Next confirmed = Update.update(chosen.model(), new DeleteBranchMsg.UnlockConfirmed());
        assertEquals(
                List.of(new RemoveWorktreeCmd(
                        "/repos/app-taken", false, Option.of(TAKEN), Option.of("on a usb stick"))),
                confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());

        Next cancelled = Update.update(chosen.model(), new Msg.PopupDismissed(UnlockWorktreePopup.class));
        assertEquals(List.of(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());
    }

    @Test
    void aPushedBranchWhoseWorktreeIsLockedCanBeDeletedOnTheRemoteOrWithThatWorktreeOnceUnlocked() {
        Worktree locked = new Worktree(LINKED.path(), LINKED.head(), false, false, Option.of(""), false);
        Model asked = Update.update(
                        Update.update(model(true), new LoadMsg.WorktreesLoaded(List.of(MAIN, locked)))
                                .model(),
                        new DeleteBranchMsg.Requested(TAKEN))
                .model();
        DeleteBranchPopup popup = asked.popup(DeleteBranchPopup.class).get();

        assertEquals(
                List.of('r', 'w'),
                DeleteBranchDialogs.DELETE_BRANCH
                        .choices()
                        .filter(choice -> choice.shown().test(asked, popup)
                                && choice.enabled().test(asked, popup))
                        .map(ChoiceDialog.Choice::key));
        Next chosen = Update.update(asked, new DeleteBranchMsg.WithWorktreeChosen());
        UnlockWorktreePopup unlock = new UnlockWorktreePopup(TAKEN, "/repos/app-taken", "app-taken", "");
        assertEquals(Option.of(unlock), chosen.model().popup());
        assertEquals(
                "The worktree at /repos/app-taken is locked. Unlock it, so that it can be removed and branch taken"
                        + " deleted?",
                DeleteBranchDialogs.UNLOCK_WORKTREE.message().apply(unlock));
        assertEquals(
                List.of(new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN), Option.of(""))),
                Update.update(chosen.model(), new DeleteBranchMsg.UnlockConfirmed())
                        .cmds());
    }

    @Test
    void aWorktreeIsUnlockedBeforeItIsRemovedAndForcingItUnlocksItAgain() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        RemoveWorktreeCmd remove =
                new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN), Option.of("on a usb stick"));

        remove.perform(lockingGit(ran, Either.right(List.empty()), Either.right(List.empty())));

        assertEquals(
                java.util.List.of("unlock /repos/app-taken", "remove /repos/app-taken false", "delete taken"), ran);
        assertEquals(
                new RemoveWorktreeCmd("/repos/app-taken", true, Option.of(TAKEN), Option.of("on a usb stick")),
                remove.forced());
    }

    @Test
    void aWorktreeThatIsNotRemovedAfterItWasUnlockedIsLockedAgainForTheSameReason() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        RemoveWorktreeCmd remove =
                new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN), Option.of("on a usb stick"));
        Env changed = new Env(
                lockingGit(
                        ran,
                        Either.left(new GitFailure<>(
                                new GitError.WorktreeHasChanges("changed"),
                                StubGitProvider.executed("git worktree remove /repos/app-taken"))),
                        StubGitProvider.ran("git worktree lock /repos/app-taken")),
                net.vanfleteren.jazygit.gh.StubGitHub.empty());

        assertEquals(
                new RemoveWorktreeMsg.NeedsForce(
                        remove,
                        StubGitProvider.executed(
                                "git worktree unlock /repos/app-taken",
                                "git worktree remove /repos/app-taken",
                                "git worktree lock /repos/app-taken")),
                remove.run(changed));
        assertEquals(
                java.util.List.of(
                        "unlock /repos/app-taken",
                        "remove /repos/app-taken false",
                        "lock /repos/app-taken on a usb stick"),
                ran);
    }

    @Test
    void whenTheWorktreeIsNotLockedAgainEitherThatFailsAndTheErrorSaysSo() {
        RemoveWorktreeCmd remove =
                new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN), Option.of("on a usb stick"));
        Env changed = new Env(
                lockingGit(
                        new java.util.ArrayList<>(),
                        Either.left(new GitFailure<>(new GitError.WorktreeHasChanges("changed"), List.empty())),
                        StubGitProvider.refused("git worktree lock /repos/app-taken", "already locked")),
                net.vanfleteren.jazygit.gh.StubGitHub.empty());

        Msg failed = remove.run(changed);

        assertInstanceOf(OperationMsg.Failed.class, failed);
        assertEquals(
                Option.of("Removing the worktree /repos/app-taken and deleting its branch failed: changed; cleaning up"
                        + " afterwards failed too: already locked"),
                errorText(Update.update(model(), failed).model()));
    }

    /**
     * A git that records what it is asked to do, unlocking a worktree, removing it with {@code removed} as the
     * outcome, locking it again with {@code locked} as the outcome, and deleting a branch.
     */
    private static GitInfoProvider lockingGit(
            java.util.List<String> ran,
            Either<GitFailure<GitError>, List<ExecutedCommand>> removed,
            Either<GitFailure<ProcessError>, List<ExecutedCommand>> locked) {
        return new StubGitProvider() {
            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> lockWorktree(Path location, boolean lock) {
                ran.add((lock ? "lock " : "unlock ") + location);
                return StubGitProvider.ran("git worktree " + (lock ? "lock " : "unlock ") + location);
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> lockWorktree(Path location, String reason) {
                ran.add("lock " + location + " " + reason);
                return locked;
            }

            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
                ran.add("remove " + location + " " + force);
                return removed;
            }

            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocalBranch(BranchName name) {
                ran.add("delete " + name.value());
                return Either.right(List.empty());
            }
        };
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
            public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocalBranch(BranchName name) {
                ran.add("delete " + name.value());
                return Either.right(List.empty());
            }
        };

        new RemoveWorktreeCmd("/repos/app-taken", true, Option.of(TAKEN)).perform(git);
        assertEquals(java.util.List.of("remove /repos/app-taken true", "delete taken"), ran);

        ran.clear();
        RemoveWorktreeCmd changed = new RemoveWorktreeCmd("/repos/app-changed", false, Option.of(TAKEN));
        assertEquals(
                new RemoveWorktreeMsg.NeedsForce(changed, List.empty()),
                changed.run(new Env(git, net.vanfleteren.jazygit.gh.StubGitHub.empty())));
        assertEquals(java.util.List.of("remove /repos/app-changed false"), ran);
    }

    @Test
    void whenTheBranchIsNotDeletedAfterTheWorktreeIsRemovedThatFailsWithBothCommandsLogged() {
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
                return StubGitProvider.ran("git worktree remove " + location);
            }

            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocalBranch(BranchName name) {
                return GitFailure.widen(
                        StubGitProvider.refused("git branch --delete " + name.value(), "ref is locked"));
            }
        };
        RemoveWorktreeCmd remove = new RemoveWorktreeCmd("/repos/app-taken", false, Option.of(TAKEN));

        Msg failed = remove.run(new Env(git, net.vanfleteren.jazygit.gh.StubGitHub.empty()));

        assertEquals(
                remove.failedWith(new GitFailure<>(
                        new GitError.CommandFailed("ref is locked", 1),
                        StubGitProvider.executed("git worktree remove /repos/app-taken", "git branch --delete taken"))),
                failed);
        assertEquals(
                Option.of("Removing the worktree /repos/app-taken and deleting its branch failed: ref is locked"),
                errorText(Update.update(model(), failed).model()));
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

        Worktree mainNotShown = new Worktree(MAIN.path(), MAIN.head(), true, false, Option.none(), false);
        Worktree shownLinked = new Worktree(
                Path.of("/repos/app-shown"),
                Option.of(new Head.Detached(new Sha("2222"))),
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
        Worktree lockedLinked = new Worktree(LINKED.path(), LINKED.head(), false, false, Option.of(""), false);
        Next locked = Update.update(worktrees(MAIN, lockedLinked), new RemoveWorktreeMsg.Requested("/repos/app-taken"));
        assertEquals(Option.none(), locked.model().popup(RemoveWorktreePopup.class));
        assertEquals(
                Option.of("Worktree app-taken is locked, which keeps it from being removed"),
                errorText(locked.model()));
        Worktree lockedWithReason =
                new Worktree(LINKED.path(), LINKED.head(), false, false, Option.of("on usb"), false);
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
                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("aaaa")))),
                false,
                true,
                Option.none(),
                false);
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Existing(FEATURE), "/repos/proj/feature")),
                Update.update(worktrees(shown, LINKED), new AddWorktreeMsg.Requested(FEATURE))
                        .model()
                        .popup());

        Worktree taken = new Worktree(
                Path.of("/repos/app-feature"),
                Option.of(new Head.Detached(new Sha("3333"))),
                false,
                false,
                Option.none(),
                false);
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Existing(FEATURE), "/repos/app-feature-2")),
                Update.update(worktrees(MAIN, LINKED, taken), new AddWorktreeMsg.Requested(FEATURE))
                        .model()
                        .popup());
    }

    @Test
    void aWorktreeWhoseDirectoryIsGoneIsNotSwitchedTo() {
        Worktree missing = new Worktree(LINKED.path(), LINKED.head(), false, false, Option.none(), true);

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

        Worktree missing = new Worktree(LINKED.path(), LINKED.head(), false, false, Option.none(), true);
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
        Worktree locked = new Worktree(LINKED.path(), LINKED.head(), false, false, Option.of(""), false);
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
                Update.update(refused.model(), new Msg.PopupDismissed(ForceRemoveWorktreePopup.class))
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

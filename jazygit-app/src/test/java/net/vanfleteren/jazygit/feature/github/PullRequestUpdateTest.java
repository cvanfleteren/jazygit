package net.vanfleteren.jazygit.feature.github;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static net.vanfleteren.jazygit.state.TestModels.lastRead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.fetch.FetchCmd;
import net.vanfleteren.jazygit.feature.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestCmd;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg.BaseMoved;
import net.vanfleteren.jazygit.feature.github.create.NewPullRequestPopup;
import net.vanfleteren.jazygit.feature.github.create.PushFirstPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.ShowsPullRequest;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.GitHub;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.LoadRepositoryPullRequests;
import org.junit.jupiter.api.Test;

class PullRequestUpdateTest {

    private static final Branch MAIN = Fixtures.branch(
            new BranchName("main"),
            true,
            new Sha("1"),
            Instant.EPOCH,
            true,
            true,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))), 0, 0)));
    private static final Branch RELEASE = Fixtures.branch(
            new BranchName("release"),
            false,
            new Sha("2"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("release"))), 0, 0)));
    private static final Branch PUSHED = Fixtures.branch(
            new BranchName("feature"),
            false,
            new Sha("3"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))), 0, 0)));
    private static final Branch UNPUSHED = Fixtures.branch(new BranchName("local"), false, new Sha("4"));
    private static final Branch AHEAD = Fixtures.branch(
            new BranchName("ahead"),
            false,
            new Sha("5"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName("ahead"))), 2, 0)));
    private static final BranchName FEATURE = PUSHED.name();
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);

    private static Model withPr() {
        return Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();
    }

    private static Env env() {
        return new Env(new StubGitProvider() {}, StubGitHub.empty());
    }

    /**
     * The branches, and no pull request from any of them.
     */
    private static Model model() {
        // The default branch is listed last, to see it offered first.
        Model branches = localBranchesRead(loaded(), List.of(RELEASE, PUSHED, UNPUSHED, AHEAD, MAIN))
                .model();
        return Update.update(branches, new PullRequestsMsg.Loaded(List.empty())).model();
    }

    @Test
    void beforeThePullRequestsAreReadNoneIsCreated() {
        Model starting = localBranchesRead(loaded(), List.of(PUSHED, MAIN)).model();

        Next asked = Update.update(starting, new PullRequestMsg.Requested(new BranchName("feature")));

        assertEquals(
                Option.of(new AppError.PullRequestsNotRead()),
                asked.model().outcome().error());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void aPushedBranchWithoutPullRequestReadsWhichBaseItCameFromFirst() {
        Next reading = Update.update(model(), new PullRequestMsg.Requested(FEATURE));

        assertEquals(
                List.of(new ReadParent(
                        FEATURE,
                        List.of(
                                new Base(MAIN.name(), remote("main")),
                                new Base(RELEASE.name(), remote("release")),
                                new Base(AHEAD.name(), remote("ahead"))))),
                reading.cmds());
        assertEquals(Option.none(), reading.model().popup());
    }

    @Test
    void withoutAParentTheTitleIsReadForTheDefaultBranchOfferedFirst() {
        Next reading = Update.update(model(), new CreatePullRequestMsg.ParentRead(FEATURE, Option.none()));

        assertEquals(
                List.of(new ReadTitle(
                        FEATURE,
                        new RemoteBranch("origin", FEATURE),
                        List.of(MAIN.name(), RELEASE.name(), AHEAD.name()),
                        remote("main"))),
                reading.cmds());
        assertEquals(Option.none(), reading.model().popup());
    }

    @Test
    void theParentIsOfferedFirstThenTheDefaultBranch() {
        Next reading = Update.update(model(), new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(AHEAD.name())));

        assertEquals(
                List.of(new ReadTitle(
                        FEATURE,
                        new RemoteBranch("origin", FEATURE),
                        List.of(AHEAD.name(), MAIN.name(), RELEASE.name()),
                        remote("ahead"))),
                reading.cmds());
    }

    @Test
    void aParentThatCannotBeChosenIsIgnored() {
        Next reading = Update.update(model(), new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(UNPUSHED.name())));

        assertEquals(
                List.of(new ReadTitle(
                        FEATURE,
                        new RemoteBranch("origin", FEATURE),
                        List.of(MAIN.name(), RELEASE.name(), AHEAD.name()),
                        remote("main"))),
                reading.cmds());
    }

    @Test
    void withOneBaseTheTitleIsReadWithoutTheParent() {
        Model one = localBranchesRead(model(), List.of(PUSHED, MAIN)).model();

        Next reading = Update.update(one, new PullRequestMsg.Requested(FEATURE));

        assertEquals(
                List.of(new ReadTitle(
                        FEATURE, new RemoteBranch("origin", FEATURE), List.of(MAIN.name()), remote("main"))),
                reading.cmds());
    }

    @Test
    void withoutABaseThereIsNothingToAsk() {
        Model none = localBranchesRead(model(), List.of(PUSHED, UNPUSHED)).model();

        Next next = Update.update(none, new PullRequestMsg.Requested(FEATURE));

        assertEquals(
                Option.of(new AppError.NoBase(FEATURE)), next.model().outcome().error());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theParentReadOfABranchThatIsGoneAsksNothing() {
        Model gone = localBranchesRead(model(), List.of(RELEASE, MAIN)).model();

        Next next = Update.update(gone, new CreatePullRequestMsg.ParentRead(FEATURE, Option.none()));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void aParentReadOnceNoBaseIsLeftAsksNothing() {
        // While the parent was read, the other branches left their remote.
        Model left = localBranchesRead(model(), List.of(PUSHED, UNPUSHED)).model();

        Next next = Update.update(left, new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(MAIN.name())));

        assertEquals(
                Option.of(new AppError.NoBase(FEATURE)), next.model().outcome().error());
        assertEquals(Option.none(), next.model().popup(NewPullRequestPopup.class));
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theTitleReadDoesNotTakeAwayAnotherPopup() {
        Model busy = creating();

        assertEquals(
                busy.popup(),
                Update.update(busy, titleRead(Option.of("Other"))).model().popup());
    }

    @Test
    void theRecordedParentIsReadWithoutCountingCommits() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitError.ProcessError, Option<BranchName>> parentBranch(BranchName branch) {
                return Either.right(Option.of(RELEASE.name()));
            }

            @Override
            public Either<GitError.ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                throw new AssertionError("counted");
            }
        };

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(RELEASE.name())),
                readParent().run(new Env(git, StubGitHub.empty())));
    }

    @Test
    void withoutARecordedParentTheClosestBaseIsTheParent() {
        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(RELEASE.name())),
                readParent().run(distances(5, 2, 3)));
    }

    @Test
    void onATieTheEarliestBaseIsTheParent() {
        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(MAIN.name())),
                readParent().run(distances(2, 2, 3)));
    }

    @Test
    void basesTooFarAwayAreNoParent() {
        int far = ReadParent.MAX_DISTANCE;
        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.none()),
                readParent().run(distances(far, far, far)));
    }

    @Test
    void aRecordedParentThatIsNoBaseIsIgnored() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitError.ProcessError, Option<BranchName>> parentBranch(BranchName branch) {
                return Either.right(Option.of(UNPUSHED.name()));
            }

            @Override
            public Either<GitError.ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                return Either.right(base.equals(remote("ahead")) ? 1 : 4);
            }
        };

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(AHEAD.name())),
                readParent().run(new Env(git, StubGitHub.empty())));
    }

    @Test
    void failingToCountLeavesNoParent() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitError.ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                return Either.left(new GitError.CommandFailed("boom", 128));
            }
        };

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.none()),
                readParent().run(new Env(git, StubGitHub.empty())));
    }

    private static BranchRef remote(String branch) {
        return new BranchRef.Remote(new RemoteBranch("origin", new BranchName(branch)));
    }

    /**
     * Reading which of main, release and ahead the feature branch came from.
     */
    private static ReadParent readParent() {
        return new ReadParent(
                FEATURE,
                List.of(
                        new Base(MAIN.name(), remote("main")),
                        new Base(RELEASE.name(), remote("release")),
                        new Base(AHEAD.name(), remote("ahead"))));
    }

    /**
     * Git without a recorded parent, where the feature branch has {@code main}, {@code release} and {@code ahead}
     * commits not on those bases.
     */
    private static Env distances(int main, int release, int ahead) {
        Map<BranchRef, Integer> counts =
                HashMap.of(remote("main"), main, remote("release"), release, remote("ahead"), ahead);
        return new Env(
                new StubGitProvider() {
                    @Override
                    public Either<GitError.ProcessError, Integer> countNotOn(
                            BranchRef branch, BranchRef base, int max) {
                        return Either.right(counts.get(base).get());
                    }
                },
                StubGitHub.empty());
    }

    /**
     * The title of the feature branch was read for main, offered first, then release and ahead.
     */
    private static CreatePullRequestMsg.TitleRead titleRead(Option<String> subject) {
        return new CreatePullRequestMsg.TitleRead(
                FEATURE,
                new RemoteBranch("origin", FEATURE),
                List.of(MAIN.name(), RELEASE.name(), AHEAD.name()),
                subject);
    }

    @Test
    void theTitleReadIsOfferedToEditWithTheBaseItWasReadFor() {
        Next asking = Update.update(model(), titleRead(Option.of("Add login")));

        assertEquals(
                Option.of(new NewPullRequestPopup(
                        FEATURE,
                        new RemoteBranch("origin", FEATURE),
                        List.of(MAIN.name(), RELEASE.name(), AHEAD.name()),
                        MAIN.name(),
                        "Add login",
                        new NewPullRequest("Add login", "", false))),
                asking.model().popup());
        assertEquals(List.empty(), asking.cmds());
    }

    @Test
    void withoutACommitOfItsOwnTheTitleIsTheBranch() {
        Next asking = Update.update(model(), titleRead(Option.none()));

        assertEquals(
                Option.of(new NewPullRequest("feature", "", false)),
                asking.model().popup(NewPullRequestPopup.class).map(NewPullRequestPopup::pullRequest));
    }

    @Test
    void movingToAnotherBasePicksItAndReadsTheTitleForIt() {
        ListCursor cursor = ListCursor.START.at(1);

        Next moved = Update.update(creating(), new BaseMoved(cursor, Option.of(RELEASE.name())));

        assertEquals(
                Option.of(RELEASE.name()),
                moved.model().popup(NewPullRequestPopup.class).map(NewPullRequestPopup::base));
        assertEquals(cursor, moved.model().listCursor());
        assertEquals(List.of(new RereadTitle(FEATURE, RELEASE.name(), remote("release"))), moved.cmds());
    }

    @Test
    void movingAfterTheTitleWasTypedReadsNoTitle() {
        Model typed = Update.update(
                        creating(), new CreatePullRequestMsg.PullRequestEdited(new NewPullRequest("Mine", "", false)))
                .model();

        Next moved = Update.update(typed, new BaseMoved(ListCursor.START.at(1), Option.of(RELEASE.name())));

        assertEquals(
                Option.of(RELEASE.name()),
                moved.model().popup(NewPullRequestPopup.class).map(NewPullRequestPopup::base));
        assertEquals(List.empty(), moved.cmds());
    }

    @Test
    void aFilterThatShowsNoBaseKeepsTheBase() {
        ListCursor cursor = new ListCursor(Option.of(0), Option.of("nothing"));

        Next moved = Update.update(creating(), new BaseMoved(cursor, Option.none()));

        assertEquals(
                Option.of(MAIN.name()),
                moved.model().popup(NewPullRequestPopup.class).map(NewPullRequestPopup::base));
        assertEquals(cursor, moved.model().listCursor());
        assertEquals(List.empty(), moved.cmds());
    }

    @Test
    void theTitleReadForThePickedBaseReplacesTheUntouchedTitle() {
        Model release = Update.update(creating(), new BaseMoved(ListCursor.START.at(1), Option.of(RELEASE.name())))
                .model();

        Model read = Update.update(
                        release,
                        new CreatePullRequestMsg.BaseTitleRead(FEATURE, RELEASE.name(), Option.of("Fix release")))
                .model();

        assertEquals(
                Option.of(new NewPullRequest("Fix release", "", false)),
                read.popup(NewPullRequestPopup.class).map(NewPullRequestPopup::pullRequest));
        assertEquals(ListCursor.START.at(1), read.listCursor());
    }

    @Test
    void aTitleTypedBackToTheOneReadIsReplacedAgain() {
        Model typed = Update.update(
                        creating(),
                        new CreatePullRequestMsg.PullRequestEdited(new NewPullRequest("Add login", "Why", true)))
                .model();

        Next moved = Update.update(typed, new BaseMoved(ListCursor.START.at(1), Option.of(RELEASE.name())));
        Model read = Update.update(
                        moved.model(),
                        new CreatePullRequestMsg.BaseTitleRead(FEATURE, RELEASE.name(), Option.of("Fix release")))
                .model();

        assertEquals(List.of(new RereadTitle(FEATURE, RELEASE.name(), remote("release"))), moved.cmds());
        assertEquals(
                Option.of(new NewPullRequest("Fix release", "Why", true)),
                read.popup(NewPullRequestPopup.class).map(NewPullRequestPopup::pullRequest));
    }

    @Test
    void aTitleReadForABaseNoLongerPickedIsDropped() {
        Model release = Update.update(creating(), new BaseMoved(ListCursor.START.at(1), Option.of(RELEASE.name())))
                .model();
        Model ahead = Update.update(release, new BaseMoved(ListCursor.START.at(2), Option.of(AHEAD.name())))
                .model();

        assertEquals(
                ahead,
                Update.update(
                                ahead,
                                new CreatePullRequestMsg.BaseTitleRead(
                                        FEATURE, RELEASE.name(), Option.of("Fix release")))
                        .model());
    }

    @Test
    void aTitleReadAfterTheTitleWasTypedIsDropped() {
        Model release = Update.update(creating(), new BaseMoved(ListCursor.START.at(1), Option.of(RELEASE.name())))
                .model();
        Model typed = Update.update(
                        release, new CreatePullRequestMsg.PullRequestEdited(new NewPullRequest("Mine", "", false)))
                .model();

        assertEquals(
                typed,
                Update.update(
                                typed,
                                new CreatePullRequestMsg.BaseTitleRead(
                                        FEATURE, RELEASE.name(), Option.of("Fix release")))
                        .model());
    }

    @Test
    void aTitleReadAfterThePopupClosedOpensNothing() {
        Model closed = Update.update(creating(), new Msg.PopupDismissed(NewPullRequestPopup.class))
                .model();

        assertEquals(
                Option.none(),
                Update.update(closed, new CreatePullRequestMsg.BaseTitleRead(FEATURE, MAIN.name(), Option.of("x")))
                        .model()
                        .popup());
    }

    @Test
    void theTitleIsReadAgainFromTheFirstCommitNotOnThePickedBase() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
                return Either.right(Option.of(
                        branch.equals(new BranchRef.Local(FEATURE)) && base.equals(remote("release"))
                                ? "Fix release"
                                : "wrong"));
            }
        };

        assertEquals(
                new CreatePullRequestMsg.BaseTitleRead(FEATURE, RELEASE.name(), Option.of("Fix release")),
                new RereadTitle(FEATURE, RELEASE.name(), remote("release")).run(new Env(git, StubGitHub.empty())));
    }

    private static final Branch DEV = Fixtures.branch(
            new BranchName("dev"),
            false,
            new Sha("6"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(
                    new BranchRef.Remote(new RemoteBranch("origin", new BranchName("development"))), 0, 0)));

    @Test
    void aBaseIsMergedIntoUnderItsNameOnTheRemote() {
        Model branches = localBranchesRead(loaded(), List.of(PUSHED, DEV, MAIN)).model();
        Model loaded = Update.update(branches, new PullRequestsMsg.Loaded(List.empty()))
                .model();
        Model asked = Update.update(
                        loaded,
                        new CreatePullRequestMsg.TitleRead(
                                FEATURE,
                                new RemoteBranch("origin", FEATURE),
                                List.of(MAIN.name(), DEV.name()),
                                Option.of("Add login")))
                .model();
        Model picked = Update.update(asked, new BaseMoved(ListCursor.START.at(1), Option.of(DEV.name())))
                .model();

        Next created = Update.update(picked, new CreatePullRequestMsg.Confirmed());

        assertEquals(
                List.of(new CreatePullRequest(
                        FEATURE,
                        new RemoteBranch("origin", FEATURE),
                        new RemoteBranch("origin", new BranchName("development")),
                        new NewPullRequest("Add login", "", false))),
                created.cmds());
    }

    @Test
    void aParentRecordedUnderTheNameOnTheRemoteIsTheBaseOfThatName() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitError.ProcessError, Option<BranchName>> parentBranch(BranchName branch) {
                return Either.right(Option.of(new BranchName("development")));
            }

            @Override
            public Either<GitError.ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                throw new AssertionError("counted");
            }
        };
        ReadParent read = new ReadParent(
                FEATURE, List.of(new Base(MAIN.name(), remote("main")), new Base(DEV.name(), remote("development"))));

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(DEV.name())),
                read.run(new Env(git, StubGitHub.empty())));
    }

    @Test
    void aParentRecordedUnderALocalNameIsTheBaseOfThatName() {
        ReadParent read = new ReadParent(
                FEATURE, List.of(new Base(MAIN.name(), remote("main")), new Base(DEV.name(), remote("development"))));

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(DEV.name())),
                read.run(new Env(parentRecorded("dev"), StubGitHub.empty())));
    }

    @Test
    void aNameOnTheRemoteWinsOverTheSameLocalName() {
        ReadParent read = new ReadParent(
                FEATURE,
                List.of(new Base(new BranchName("stable"), remote("main")), new Base(MAIN.name(), remote("trunk"))));

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(new BranchName("stable"))),
                read.run(new Env(parentRecorded("main"), StubGitHub.empty())));
    }

    private static StubGitProvider parentRecorded(String name) {
        return new StubGitProvider() {
            @Override
            public Either<GitError.ProcessError, Option<BranchName>> parentBranch(BranchName branch) {
                return Either.right(Option.of(new BranchName(name)));
            }

            @Override
            public Either<GitError.ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                throw new AssertionError("counted");
            }
        };
    }

    @Test
    void confirmingCreatesThePullRequestAsEnteredAndKeepsTheBranchBusy() {
        NewPullRequest entered = new NewPullRequest(" Add login ", "Why", true);

        Next created = confirmed(entered);

        assertEquals(
                List.of(new CreatePullRequest(
                        FEATURE,
                        new RemoteBranch("origin", FEATURE),
                        new RemoteBranch("origin", MAIN.name()),
                        new NewPullRequest("Add login", "Why", true))),
                created.cmds());
        assertEquals(Option.none(), created.model().popup());
        assertEquals(
                Option.of(CreatePullRequestCmd.CREATE_PR),
                created.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void aBlankTitleKeepsThePopupOpen() {
        Next next = confirmed(new NewPullRequest(" ", "", false));

        assertEquals(List.empty(), next.cmds());
        assertTrue(next.model().popup(NewPullRequestPopup.class).isDefined());
    }

    @Test
    void editingKeepsWhatThePullRequestSaysInThePopup() {
        NewPullRequest edited = new NewPullRequest("Add login!", "Why", true);

        Next next = Update.update(creating(), new CreatePullRequestMsg.PullRequestEdited(edited));

        assertEquals(
                Option.of(edited), next.model().popup(NewPullRequestPopup.class).map(NewPullRequestPopup::pullRequest));
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void anEditAfterThePopupClosedIsIgnored() {
        Model closed = Update.update(creating(), new Msg.PopupDismissed(NewPullRequestPopup.class))
                .model();

        assertEquals(
                closed,
                Update.update(closed, new CreatePullRequestMsg.PullRequestEdited(new NewPullRequest("x", "", false)))
                        .model());
    }

    @Test
    void cancellingTheNewPullRequestClosesThePopup() {
        assertEquals(
                Option.none(),
                Update.update(creating(), new Msg.PopupDismissed(NewPullRequestPopup.class))
                        .model()
                        .popup());
    }

    /**
     * Confirming {@code pullRequest} as what the new pull request of the feature branch into main says.
     */
    private static Next confirmed(NewPullRequest pullRequest) {
        Model edited = Update.update(creating(), new CreatePullRequestMsg.PullRequestEdited(pullRequest))
                .model();
        return TestModels.stepped(edited, new CreatePullRequestMsg.Confirmed());
    }

    /**
     * Asking what the new pull request of the feature branch into main says.
     */
    private static Model creating() {
        return Update.update(model(), titleRead(Option.of("Add login"))).model();
    }

    @Test
    void aPullRequestIsCreatedFromTheNameTheBranchHasOnItsRemoteNotItsLocalOne() {
        // #138: local login is pushed to origin/feature/login; gh has to be given that name.
        RemoteBranch pushedAs = new RemoteBranch("origin", new BranchName("feature/login"));
        Branch login = Fixtures.branch(
                new BranchName("login"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(pushedAs), 0, 0)));
        Model model = Update.update(
                        localBranchesRead(loaded(), List.of(login, MAIN)).model(),
                        new PullRequestsMsg.Loaded(List.empty()))
                .model();

        Next reading = Update.update(model, new CreatePullRequestMsg.ParentRead(login.name(), Option.none()));
        ReadTitle readTitle = (ReadTitle) reading.cmds().single();
        assertEquals(pushedAs, readTitle.head());
        Model asked = Update.update(reading.model(), readTitle.run(env())).model();
        Next confirmed = TestModels.stepped(asked, new CreatePullRequestMsg.Confirmed());
        CreatePullRequest create = (CreatePullRequest) confirmed.cmds().single();

        assertEquals(pushedAs, create.head());
        CreatePullRequestMsg.Created created = (CreatePullRequestMsg.Created) create.run(env());
        assertTrue(
                created.commands().exists(command -> command.line().contains("--head feature/login --base main")),
                created.commands().toString());
    }

    @Test
    void aBranchNotOnItsRemoteIsAskedToBePushedToItsPushTargetFirst() {
        Next asking = Update.update(model(), new PullRequestMsg.Requested(UNPUSHED.name()));

        assertEquals(
                Option.of(new PushFirstPopup(
                        UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name()), Option.none())),
                asking.model().popup());
        assertEquals(List.empty(), asking.cmds());
        assertEquals(Option.none(), asking.model().outcome().error());
    }

    @Test
    void aBranchAheadOfItsRemoteIsAskedToPushItsCommitsFirst() {
        Next asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()));

        assertEquals(
                Option.of(new PushFirstPopup(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()), Option.of(2))),
                asking.model().popup());
        assertEquals(List.empty(), asking.cmds());
    }

    @Test
    void aDivergedBranchIsNotForcePushedForItsPullRequest() {
        RemoteBranch upstream = new RemoteBranch("origin", new BranchName("diverged"));
        Branch diverged = Fixtures.branch(
                upstream.branch(),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(upstream), 2, 1)));
        Model branches = localBranchesRead(loaded(), List.of(diverged, MAIN)).model();
        Model starting = Update.update(branches, new PullRequestsMsg.Loaded(List.empty()))
                .model();

        Next refused = Update.update(starting, new PullRequestMsg.Requested(diverged.name()));

        assertEquals(
                Option.of(new AppError.Diverged(diverged.name(), upstream)),
                refused.model().outcome().error());
        assertTrue(refused.model().popup(PushFirstPopup.class).isEmpty());
        assertEquals(List.empty(), refused.cmds());
    }

    @Test
    void confirmingPushesABranchNotOnItsRemoteToItsPushTargetWhichItThenTracks() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(UNPUSHED.name()))
                .model();

        Next pushing = TestModels.stepped(asking, new CreatePullRequestMsg.PushFirstConfirmed());

        assertEquals(
                List.of(new PushFirst(
                        new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())))),
                pushing.cmds());
        assertEquals(Option.none(), pushing.model().popup());
        assertTrue(pushing.model().idleBranch(UNPUSHED.name()).isEmpty());
    }

    @Test
    void confirmingPushesTheCommitsOfABranchAheadOfItsRemote() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()))
                .model();

        Next pushing = Update.update(asking, new CreatePullRequestMsg.PushFirstConfirmed());

        assertEquals(
                List.of(new PushFirst(
                        new Push(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()), PushMode.FAST_FORWARD))),
                pushing.cmds());
    }

    @Test
    void cancellingThePushPushesNothing() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(UNPUSHED.name()))
                .model();

        Next cancelled = Update.update(asking, new Msg.PopupDismissed(PushFirstPopup.class));

        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void onceItIsPushedItsPullRequestIsAskedForWhileTheRepositoryIsReadAgain() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
        Model pushing = Update.start(model(), push).model();

        List<ExecutedCommand> commands = StubGitProvider.executed("git push -u origin local");

        Next pushed = Update.update(pushing, push.succeeded(commands));

        assertEquals(
                List.of(new ReadParent(
                        UNPUSHED.name(),
                        List.of(
                                new Base(MAIN.name(), remote("main")),
                                new Base(RELEASE.name(), remote("release")),
                                new Base(FEATURE, remote("feature")),
                                new Base(AHEAD.name(), remote("ahead"))))),
                pushed.cmds().filter(ReadParent.class::isInstance));
        // What any push does once it succeeded is done as well.
        assertTrue(
                pushed.cmds()
                        .containsAll(OperationUpdate.succeeded(pushing, push, commands)
                                .cmds()),
                pushed.cmds().toString());
        assertTrue(pushed.model().idleBranch(UNPUSHED.name()).isDefined());
    }

    @Test
    void aFailedPushAsksForNoPullRequest() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
        Model pushing = Update.start(model(), push).model();

        Next failed = Update.update(pushing, failed(push, "rejected"));

        assertFalse(
                failed.cmds().exists(CreatePullRequestCmd.class::isInstance),
                failed.cmds().toString());
        assertTrue(failed.model().outcome().error().isDefined());
        assertTrue(failed.model().popup(NewPullRequestPopup.class).isEmpty());
    }

    /**
     * {@link #AHEAD}, but {@code ahead} and {@code behind} its upstream.
     */
    private static Branch ahead(int ahead, int behind) {
        return Fixtures.branch(
                AHEAD.name(),
                false,
                new Sha("5"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", AHEAD.name())), ahead, behind)));
    }

    @Test
    void confirmingOnceItWasPushedMeanwhileAsksForItsPullRequest() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()))
                .model();
        Model pushedElsewhere = localBranchesRead(asking, List.of(RELEASE, PUSHED, UNPUSHED, ahead(0, 0), MAIN))
                .model();

        Next confirmed = Update.update(pushedElsewhere, new CreatePullRequestMsg.PushFirstConfirmed());

        assertTrue(confirmed.model().popup(PushFirstPopup.class).isEmpty());
        assertFalse(
                confirmed.cmds().exists(PushFirst.class::isInstance),
                confirmed.cmds().toString());
        assertTrue(
                confirmed.cmds().exists(ReadParent.class::isInstance),
                confirmed.cmds().toString());
    }

    @Test
    void confirmingOnceItDivergedMeanwhileSaysSo() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()))
                .model();
        Model diverged = localBranchesRead(asking, List.of(RELEASE, PUSHED, UNPUSHED, ahead(2, 1), MAIN))
                .model();

        Next confirmed = Update.update(diverged, new CreatePullRequestMsg.PushFirstConfirmed());

        assertTrue(confirmed.model().popup(PushFirstPopup.class).isEmpty());
        assertEquals(
                Option.of(new AppError.Diverged(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()))),
                confirmed.model().outcome().error());
        assertEquals(List.empty(), confirmed.cmds());
    }

    @Test
    void aPushedBranchThatIsGoneMeanwhileAsksForNoPullRequest() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
        Model pushing = Update.start(model(), push).model();
        Model gone = localBranchesRead(pushing, List.of(RELEASE, PUSHED, AHEAD, MAIN))
                .model();
        List<ExecutedCommand> commands = StubGitProvider.executed("git push -u origin local");

        Next pushed = Update.update(gone, push.succeeded(commands));

        assertEquals(OperationUpdate.succeeded(gone, push, commands).cmds(), pushed.cmds());
        assertTrue(pushed.model().popup(NewPullRequestPopup.class).isEmpty());
    }

    @Test
    void aPushThatEndsWhileAnotherPopupIsOpenLeavesThatOpen() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
        Model other = Update.start(model(), push).model().openPopup(new SwitchWorktreePopup(MAIN.name(), "/work/main"));
        Model pushed = Update.update(other, push.succeeded(StubGitProvider.executed("git push -u origin local")))
                .model();

        Model parentRead = Update.update(pushed, new CreatePullRequestMsg.ParentRead(UNPUSHED.name(), Option.none()))
                .model();
        Model titleRead = Update.update(
                        parentRead,
                        new CreatePullRequestMsg.TitleRead(
                                UNPUSHED.name(),
                                new RemoteBranch("origin", UNPUSHED.name()),
                                List.of(MAIN.name()),
                                Option.of("Add it")))
                .model();

        assertEquals(other.popup(), titleRead.popup());
    }

    @Test
    void aBranchWithAPullRequestShowsItWhileItsDetailsAreRead() {
        Next shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")));

        assertEquals(
                Option.of(PullRequestPopup.loading(new BranchName("feature"), FEATURE_PR, Instant.EPOCH, 0)),
                shown.model().popup());
        assertEquals(List.of(new LoadPullRequestDetails(42, lastRead(shown.model()))), shown.cmds());
    }

    @Test
    void theDetailsReadShowInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();

        Model loaded = Update.update(shown, new LoadPullRequestDetails(42, lastRead(shown)).run(env()))
                .model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new BranchName("feature"), FEATURE_PR, Instant.EPOCH, 0)
                        .with(Either.right(details), 0)),
                loaded.popup());
    }

    @Test
    void theDetailsReadShowOnTheBranchToo() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();
        PullRequestDetails failing = new PullRequestDetails(
                42,
                details.title(),
                details.author(),
                details.status().withDraft(true),
                details.branches(),
                details.activity(),
                details.size(),
                new Conversation(
                        Review.CHANGES_REQUESTED,
                        List.empty(),
                        0,
                        details.conversation().reviewThreads(),
                        List.empty()),
                List.of(
                        new PullRequestDetails.Check("build", Checks.PASSING),
                        new PullRequestDetails.Check("test", Checks.FAILING)));

        Model loaded = Update.update(shown, new PullRequestMsg.DetailsLoaded(42, lastRead(shown), failing))
                .model();
        // Also when the dialog was closed meanwhile.
        Model closed = Update.update(
                        Update.update(shown, new Msg.PopupDismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.DetailsLoaded(42, lastRead(shown), failing))
                .model();

        PullRequest now = new PullRequest(42, "feature", true, Checks.FAILING, Review.CHANGES_REQUESTED);
        assertEquals(Option.of(now), loaded.gitHub().pullRequest(PUSHED));
        assertEquals(Option.of(now), closed.gitHub().pullRequest(PUSHED));
    }

    @Test
    void aPullRequestTheDetailsShowMergedIsNoLongerOnTheBranch() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();
        PullRequestDetails merged = details.withStatus(new Status(
                PullRequestDetails.State.MERGED,
                details.status().draft(),
                details.status().mergeable()));

        Model loaded = Update.update(shown, new PullRequestMsg.DetailsLoaded(42, lastRead(shown), merged))
                .model();

        assertEquals(Option.none(), loaded.gitHub().pullRequest(PUSHED));
    }

    @Test
    void failingToReadTheDetailsShowsWhyInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        var failure = refused("gh: not logged in");

        Model failed = Update.update(shown, new PullRequestMsg.DetailsFailed(42, lastRead(shown), failure))
                .model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new BranchName("feature"), FEATURE_PR, Instant.EPOCH, 0)
                        .with(Either.left(failure), 0)),
                failed.popup());
        assertEquals(Option.none(), failed.outcome().error());
    }

    @Test
    void detailsReadAfterThePopupClosedOrForAnotherPullRequestAreDropped() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(7).get();

        Model other = Update.update(shown, new PullRequestMsg.DetailsLoaded(7, lastRead(shown), details))
                .model();
        assertEquals(shown.popup(), other.popup());
        assertEquals(shown.gitHub().pullRequests(), other.gitHub().pullRequests());
        Model closed = Update.update(shown, new Msg.PopupDismissed(PullRequestPopup.class))
                .model();
        assertEquals(Option.none(), closed.popup());
        Model late = Update.update(closed, new PullRequestMsg.DetailsLoaded(42, lastRead(closed), details))
                .model();
        assertEquals(Option.none(), late.popup());
        assertEquals(closed.gitHub().pullRequests(), late.gitHub().pullRequests());
    }

    /**
     * {@code model} a minute after the pull request was read, when it is read again by itself also with its checks
     * done (ADR 34).
     */
    private static Model aMinuteOn(Model model) {
        return model.withNow(model.now().plus(CheckTimings.READ_WHEN_DONE));
    }

    @Test
    void theTickReadsTheDetailsAgainByItselfAMinuteAfterTheLastReadWhenTheirChecksAreDone() {
        Model shown = showingDetails();

        assertEquals(
                List.empty(),
                Update.update(shown.withNow(shown.now().plusSeconds(54)), new PullRequestMsg.Tick())
                        .cmds());
        Next again = Update.update(aMinuteOn(shown), new PullRequestMsg.Tick());
        assertEquals(List.of(new LoadPullRequestDetails(42, lastRead(again.model()))), again.cmds());
        assertEquals(
                Reading.BY_ITSELF,
                again.model().popup(PullRequestPopup.class).get().reading());
        assertEquals(
                Reading.ASKED,
                Update.update(shown, new PullRequestMsg.RefreshChosen())
                        .model()
                        .popup(PullRequestPopup.class)
                        .get()
                        .reading());
    }

    @Test
    void refreshingWhileTheDetailsAreReadByThemselvesShowsThatReadAsAsked() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();

        Next asked = Update.update(quiet, new PullRequestMsg.RefreshChosen());

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Reading.ASKED, asked.model().popup(PullRequestPopup.class).get().reading());
    }

    @Test
    void aDraftChangedWhileTheDetailsAreReadByThemselvesDoesNotTakeThatRead() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();

        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        SetDraft cmd = (SetDraft) drafting.cmds().head();
        assertEquals(new SetDraft(FEATURE, 42, true), cmd);

        // The quiet read started before the change, and comes back after it, with how it was before; the pull
        // requests read again after the change list it as a draft.
        Model drafted = Update.update(
                        Update.update(drafting.model(), cmd.run(env())).model(),
                        new PullRequestsMsg.Loaded(
                                List.of(new PullRequest(42, "feature", true, Checks.PASSING, Review.APPROVED))))
                .model();
        Model dropped = Update.update(drafted, new PullRequestMsg.DetailsLoaded(42, lastRead(drafted), before))
                .model();
        PullRequestPopup popup = dropped.popup(PullRequestPopup.class).get();
        assertEquals(Option.of(true), popup.draft());
        assertEquals(Reading.NOT, popup.reading());
        assertEquals(drafted.gitHub().pullRequests(), dropped.gitHub().pullRequests());
    }

    @Test
    void aReadThatComesBackBeforeTheDraftChangeIsDoneIsTakenAndTheChangeShowsOnceDone() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());

        // The quiet read comes back while the change still runs: how it was then, which is taken.
        Model read = Update.update(
                        drafting.model(), new PullRequestMsg.DetailsLoaded(42, lastRead(drafting.model()), before))
                .model();
        assertEquals(Reading.NOT, read.popup(PullRequestPopup.class).get().reading());
        assertEquals(Option.of(false), read.popup(PullRequestPopup.class).get().draft());

        Model drafted = Update.update(read, ((SetDraft) drafting.cmds().head()).run(env()))
                .model();
        assertEquals(
                Option.of(true), drafted.popup(PullRequestPopup.class).get().draft());
    }

    @Test
    void aDroppedReadThatFailsMarksNothingStaleAndLetsTicksReadAgain() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();

        Model failed = Update.update(
                        drafted, new PullRequestMsg.DetailsFailed(42, lastRead(drafted), refused("gh: offline")))
                .model();

        PullRequestPopup popup = failed.popup(PullRequestPopup.class).get();
        assertEquals(Option.none(), popup.refreshFailed());
        assertEquals(Option.of(true), popup.draft());
        assertEquals(Reading.NOT, popup.reading());
        assertEquals(
                List.of(new LoadPullRequestDetails(42, failed.gitHub().nextRead())),
                Update.update(aMinuteOn(failed), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void refreshingWhileAReadThatIsNoLongerTakenRunsStartsAFreshOne() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();

        Next asked = Update.update(drafted, new PullRequestMsg.RefreshChosen());

        assertEquals(List.of(new LoadPullRequestDetails(42, drafted.gitHub().nextRead())), asked.cmds());
        PullRequestPopup popup = asked.model().popup(PullRequestPopup.class).get();
        assertEquals(Reading.ASKED, popup.reading());
        assertEquals(drafted.gitHub().nextRead(), popup.read());
    }

    /**
     * {@code details} with one check that runs, of a commit status, which is not timed.
     */
    private static PullRequestDetails withARunningCheck(PullRequestDetails details) {
        return details.withChecks(List.of(new PullRequestDetails.Check("ci/status", Checks.PENDING)));
    }

    @Test
    void whileACheckIsNotDoneATickReadsTheDetailsAgainTenSecondsOn() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        Model running = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42,
                                lastRead(shown),
                                withARunningCheck(StubGitHub.empty()
                                        .pullRequestDetails(42)
                                        .get())))
                .model();

        assertEquals(
                List.of(new LoadPullRequestDetails(42, running.gitHub().nextRead())),
                Update.update(running.withNow(running.now().plusSeconds(10)), new PullRequestMsg.Tick())
                        .cmds());
    }

    @Test
    void afterAReadFailedTheDetailsAreReadAgainEveryMinuteOnly() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        Model running = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42,
                                lastRead(shown),
                                withARunningCheck(StubGitHub.empty()
                                        .pullRequestDetails(42)
                                        .get())))
                .model();
        Model reading = Update.update(running.withNow(running.now().plusSeconds(10)), new PullRequestMsg.Tick())
                .model();
        Model failed = Update.update(
                        reading, new PullRequestMsg.DetailsFailed(42, lastRead(reading), refused("gh: offline")))
                .model();

        assertEquals(
                List.empty(),
                Update.update(failed.withNow(failed.now().plusSeconds(10)), new PullRequestMsg.Tick())
                        .cmds());
        assertEquals(
                List.of(new LoadPullRequestDetails(42, failed.gitHub().nextRead())),
                Update.update(aMinuteOn(failed), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void aReadNotTakenAfterTheDialogWasClosedLeavesTheBranchesAsTheyWere() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();
        Model closed = Update.update(drafted, new Msg.PopupDismissed(PullRequestPopup.class))
                .model();
        // The pull requests read again after the change list it as a draft.
        Model listed = Update.update(
                        closed,
                        new PullRequestsMsg.Loaded(
                                List.of(new PullRequest(42, "feature", true, Checks.PASSING, Review.APPROVED))))
                .model();

        Model dropped = Update.update(listed, new PullRequestMsg.DetailsLoaded(42, lastRead(listed), before))
                .model();

        assertEquals(listed.gitHub().pullRequests(), dropped.gitHub().pullRequests());
    }

    @Test
    void aDialogOpenedAgainLeavesAnOlderReadThatComesBackFirstToTheBranches() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        long older = lastRead(quiet);
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();
        Model reopened = Update.update(
                        Update.update(quiet, new Msg.PopupDismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.Requested(FEATURE))
                .model();
        PullRequestPopup loading = reopened.popup(PullRequestPopup.class).get();

        // The older read is still taken, as nothing changed since, but the dialog waits for its own.
        Model failed = Update.update(reopened, new PullRequestMsg.DetailsFailed(42, older, refused("gh: offline")))
                .model();
        assertEquals(Option.of(loading), failed.popup(PullRequestPopup.class));

        Model read = Update.update(reopened, new PullRequestMsg.DetailsLoaded(42, older, details.withDraft(true)))
                .model();
        assertEquals(Option.of(loading), read.popup(PullRequestPopup.class));
        assertEquals(
                Option.of(new PullRequest(42, "feature", true, Checks.NONE, Review.NONE)),
                read.gitHub().pullRequest(PUSHED));
    }

    @Test
    void aDialogOpenedAgainTakesItsOwnReadWhenItComesBackBeforeOneThatIsNoLongerTaken() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        long stale = lastRead(quiet);
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();
        Model reopened = Update.update(
                        Update.update(drafted, new Msg.PopupDismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.Requested(FEATURE))
                .model();

        // Its own read comes back first, then the one that started before the change.
        Model own = Update.update(
                        reopened, new PullRequestMsg.DetailsLoaded(42, lastRead(reopened), before.withDraft(true)))
                .model();
        Model late = Update.update(own, new PullRequestMsg.DetailsLoaded(42, stale, before))
                .model();

        PullRequestPopup popup = late.popup(PullRequestPopup.class).get();
        assertEquals(Option.of(true), popup.draft());
        assertEquals(Reading.NOT, popup.reading());
        assertEquals(own.gitHub().pullRequests(), late.gitHub().pullRequests());
    }

    /**
     * The pull request of the feature branch shown, with its details read.
     */
    private static Model showingDetails() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        return Update.update(shown, new LoadPullRequestDetails(42, lastRead(shown)).run(env()))
                .model();
    }

    private static final String HEAD_OID = "a".repeat(40);
    private static final String BASE_OID = "b".repeat(40);

    /**
     * The dialog showing pull request 42 as read, of {@code owner/repo} on github.com, at known commits when
     * {@code known}.
     */
    private static Model showingCommits(PullRequestDetails.State state, boolean known) {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        PullRequestDetails read = StubGitHub.empty().pullRequestDetails(42).get();
        PullRequestDetails details = read.withRepository("owner/repo")
                .withUrl("https://github.com/owner/repo")
                .withStatus(read.status().withState(state))
                .withBranches(new PullRequestDetails.Branches(
                        "feature",
                        "main",
                        GhCommit.parse(known ? HEAD_OID : ""),
                        Option.none(),
                        GhCommit.parse(BASE_OID)));
        return Update.update(shown, new PullRequestMsg.DetailsLoaded(42, lastRead(shown), details))
                .model();
    }

    @Test
    void theFilesOfAPullRequestOpenInTheFilesPaneInPlaceOfTheDialog() {
        PullRequestCommits commits =
                new PullRequestCommits(42, "owner/repo", "github.com", new Sha(BASE_OID), new Sha(HEAD_OID));
        // A pull request closed without being merged shows its changes as it did while it was open.
        for (PullRequestDetails.State state : List.of(PullRequestDetails.State.OPEN, PullRequestDetails.State.CLOSED)) {
            Model shown = showingCommits(state, true).withScreen(Screen.PULL_REQUESTS);

            Next opened = Update.update(shown, new PullRequestMsg.FilesChosen());

            CommitFilesView.Changes of = new CommitFilesView.Changes.OfPullRequest(commits);
            assertEquals(Option.none(), opened.model().popup(PullRequestPopup.class));
            assertEquals(Screen.REPOSITORY, opened.model().screen());
            assertEquals(
                    Option.of(CommitFilesView.of(of, "branches", "files")),
                    opened.model().repoView().commitFiles());
            assertEquals(List.of(new Cmd.LoadCommitFiles(of), new CommitFilesCmd.LoadReviewRemarks(42)), opened.cmds());
            assertTrue(opened.cmds().head().background());
        }
    }

    @Test
    void theFilesOfAPullRequestThatIsMergedOrAtCommitsNotKnownAreNotListed() {
        for (Model shown : List.of(
                showingCommits(PullRequestDetails.State.MERGED, true),
                showingCommits(PullRequestDetails.State.OPEN, false))) {
            Next chosen = Update.update(shown, new PullRequestMsg.FilesChosen());

            assertEquals(shown, chosen.model());
            assertEquals(List.empty(), chosen.cmds());
        }
    }

    @Test
    void refreshingReadsTheDetailsAgainWhileTheyStayShown() {
        Model shown = showingDetails();
        PullRequestPopup before = shown.popup(PullRequestPopup.class).get();

        Next refreshing = Update.update(shown, new PullRequestMsg.RefreshChosen());

        PullRequestPopup popup =
                refreshing.model().popup(PullRequestPopup.class).get();
        assertEquals(before.details(), popup.details());
        assertTrue(popup.refreshing());
        assertEquals(List.of(new LoadPullRequestDetails(42, lastRead(refreshing.model()))), refreshing.cmds());
        // Once it is being read again, it is not read a second time.
        assertEquals(
                List.empty(),
                Update.update(refreshing.model(), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void theTickReadsTheDetailsAgainOnlyOnceTheFirstReadIsDone() {
        Model reading =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();

        assertEquals(
                List.empty(), Update.update(reading, new PullRequestMsg.Tick()).cmds());
        assertEquals(
                List.of(new LoadPullRequestDetails(42, showingDetails().gitHub().nextRead())),
                Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                        .cmds());
        assertEquals(
                List.empty(), Update.update(model(), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void failingToReadTheDetailsAgainKeepsThemShownAsStale() {
        Model refreshing = Update.update(showingDetails(), new PullRequestMsg.RefreshChosen())
                .model();
        var failure = refused("gh: offline");

        PullRequestPopup failed = Update.update(
                        refreshing, new PullRequestMsg.DetailsFailed(42, lastRead(refreshing), failure))
                .model()
                .popup(PullRequestPopup.class)
                .get();

        assertTrue(failed.detailsRead());
        assertFalse(failed.refreshing());
        assertEquals(Option.of(failure), failed.refreshFailed());

        Model again = Update.update(
                        Update.update(refreshing, new PullRequestMsg.DetailsFailed(42, lastRead(refreshing), failure))
                                .model(),
                        new PullRequestMsg.RefreshChosen())
                .model();
        PullRequestPopup recovered = Update.update(again, new LoadPullRequestDetails(42, lastRead(again)).run(env()))
                .model()
                .popup(PullRequestPopup.class)
                .get();
        assertEquals(Option.none(), recovered.refreshFailed());
        assertFalse(recovered.refreshing());
    }

    @Test
    void theTickDoesNotReadTheDetailsUnderThePopupOpenedFromIt() {
        Model comments = Update.update(
                        showingDetails(), new net.vanfleteren.jazygit.feature.github.comments.CommentsMsg.Requested())
                .model();

        assertTrue(comments.popup(net.vanfleteren.jazygit.feature.github.comments.CommentsPopup.class)
                .isDefined());
        assertEquals(
                List.empty(), Update.update(comments, new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void aDraftIsNotChangedWhileTheDetailsAreReadAgain() {
        // A read that started before the change could come back after it, and show it undone.
        Model refreshing = Update.update(showingDetails(), new PullRequestMsg.RefreshChosen())
                .model();

        assertEquals(
                List.empty(),
                Update.update(refreshing, new PullRequestMsg.DraftChosen()).cmds());

        Model read = Update.update(refreshing, new LoadPullRequestDetails(42, lastRead(refreshing)).run(env()))
                .model();
        assertEquals(
                List.of(new SetDraft(FEATURE, 42, true)),
                Update.update(read, new PullRequestMsg.DraftChosen()).cmds());
    }

    @Test
    void theDetailsAreNotReadAgainWhileTheBranchIsBusy() {
        // A read running while it becomes a draft could miss that, and show it undone.
        Next drafting = TestModels.stepped(showingDetails(), new PullRequestMsg.DraftChosen());
        SetDraft cmd = (SetDraft) drafting.cmds().head();

        assertEquals(
                List.empty(),
                Update.update(drafting.model(), new PullRequestMsg.RefreshChosen())
                        .cmds());
        assertEquals(
                List.empty(),
                Update.update(drafting.model(), new PullRequestMsg.Tick()).cmds());

        Model done = TestModels.done(drafting.model(), cmd, cmd.run(env())).model();
        assertEquals(
                List.of(new LoadPullRequestDetails(42, done.gitHub().nextRead())),
                Update.update(aMinuteOn(done), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void detailsReadAgainWhileTheCommentsAreOpenShowOnceTheDialogIsBack() {
        Model refreshing = Update.update(showingDetails(), new PullRequestMsg.RefreshChosen())
                .model();
        Model comments = Update.update(
                        refreshing, new net.vanfleteren.jazygit.feature.github.comments.CommentsMsg.Requested())
                .model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();

        Model read = Update.update(
                        comments, new PullRequestMsg.DetailsLoaded(42, lastRead(comments), details.withDraft(true)))
                .model();

        PullRequestPopup kept = read.popup(net.vanfleteren.jazygit.feature.github.comments.CommentsPopup.class)
                .map(ShowsPullRequest::pullRequestPopup)
                .get();
        assertFalse(kept.refreshing());
        assertEquals(Option.of(true), kept.draft());
    }

    @Test
    void aFailedFirstReadIsTriedAgainAndASecondFailureReplacesIt() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        Model failed = Update.update(
                        shown, new PullRequestMsg.DetailsFailed(42, lastRead(shown), refused("gh: offline")))
                .model();

        Next again = Update.update(aMinuteOn(failed), new PullRequestMsg.Tick());
        assertEquals(List.of(new LoadPullRequestDetails(42, lastRead(again.model()))), again.cmds());

        var second = refused("gh: not logged in");
        PullRequestPopup popup = Update.update(
                        again.model(), new PullRequestMsg.DetailsFailed(42, lastRead(again.model()), second))
                .model()
                .popup(PullRequestPopup.class)
                .get();
        assertEquals(Option.of(Either.left(second)), popup.details());
        assertEquals(Option.none(), popup.refreshFailed());
        assertFalse(popup.refreshing());
    }

    @Test
    void choosingToMergeOrCloseStartsItOnceTheDetailsShowItCan() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        // Nothing is known about the pull request yet.
        assertEquals(
                List.empty(),
                Update.update(shown, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH))
                        .cmds());
        assertEquals(
                List.empty(),
                Update.update(shown, new PullRequestMsg.CloseChosen()).cmds());

        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42,
                                lastRead(shown),
                                StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next merging = TestModels.stepped(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        Next closing = TestModels.stepped(read, new PullRequestMsg.CloseChosen());

        assertEquals(
                List.of(new MergePullRequest(new BranchName("feature"), 42, Option.none(), MergeMethod.SQUASH)),
                merging.cmds());
        assertEquals(Option.none(), merging.model().popup());
        assertEquals(
                Option.of(PullRequestCmd.MERGE_PR),
                merging.model().jobs().busy().get(new BranchRef.Local(new BranchName("feature"))));
        assertEquals(List.of(new ClosePullRequest(new BranchName("feature"), 42)), closing.cmds());
        assertEquals(
                Option.of(PullRequestCmd.CLOSE_PR),
                closing.model().jobs().busy().get(new BranchRef.Local(new BranchName("feature"))));
    }

    @Test
    void choosingDraftTogglesItOnceTheDetailsAreReadAndKeepsTheDialogOpen() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        assertEquals(
                List.empty(),
                Update.update(shown, new PullRequestMsg.DraftChosen()).cmds());

        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42,
                                lastRead(shown),
                                StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next drafting = TestModels.stepped(read, new PullRequestMsg.DraftChosen());

        assertEquals(List.of(new SetDraft(FEATURE, 42, true)), drafting.cmds());
        assertTrue(drafting.model().popup(PullRequestPopup.class).isDefined());
        assertEquals(
                Option.of(PullRequestCmd.DRAFT_PR),
                drafting.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        // While it runs, it is not started again.
        assertEquals(
                List.empty(),
                Update.update(drafting.model(), new PullRequestMsg.DraftChosen())
                        .cmds());
    }

    @Test
    void aPullRequestThatBecameADraftShowsAsOneAndThePullRequestsAreReadAgain() {
        Model read = Update.update(
                        Update.update(withPr(), new PullRequestMsg.Requested(FEATURE))
                                .model(),
                        new PullRequestMsg.DetailsLoaded(
                                42, 0, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next drafting = Update.update(read, new PullRequestMsg.DraftChosen());
        SetDraft cmd = (SetDraft) drafting.cmds().head();

        Next done = Update.update(drafting.model(), cmd.run(env()));

        assertEquals(Option.of(true), done.model().popup(PullRequestPopup.class).flatMap(PullRequestPopup::draft));
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        assertEquals(
                "gh pr ready 42 --undo",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void markingADraftReadyRunsGhPrReady() {
        SetDraft cmd = new SetDraft(FEATURE, 42, false);
        Model marking = TestModels.running(withPr(), cmd);

        Next done = TestModels.done(marking, cmd, cmd.run(env()));

        assertEquals(
                "gh pr ready 42",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    /**
     * GitHub that merges each pull request as soon as it is asked to.
     */
    private static StubGitHub mergingAtOnce() {
        return new StubGitHub() {
            @Override
            public Either<GhFailure, GitHub.Merging> mergePullRequest(
                    int number, Option<GhCommit> head, MergeMethod method) {
                return Either.right(new GitHub.Merging(new MergeStatus.Merged(), List.empty()));
            }
        };
    }

    /**
     * {@code model} once GitHub was asked to merge pull request 42 of feature, and said it is doing so.
     */
    private static Model asked(Model model) {
        MergePullRequest cmd = new MergePullRequest(FEATURE, 42, Option.of(new GhCommit("abc123")), MergeMethod.MERGE);
        return TestModels.done(TestModels.running(model, cmd), cmd, cmd.run(env()))
                .model();
    }

    @Test
    void aMergedPullRequestIsReadAgainOnceAnEarlierReadIsDone() {
        MergePullRequest cmd = new MergePullRequest(FEATURE, 42, Option.none(), MergeMethod.MERGE);
        // The running read started before the merge, and would list the pull request as still open.
        Model merging = TestModels.running(withPr(), cmd, new LoadRepositoryPullRequests());

        Next done = TestModels.done(merging, cmd, cmd.run(new Env(new StubGitProvider() {}, mergingAtOnce())));
        Next earlierRead =
                TestModels.done(done.model(), new LoadRepositoryPullRequests(), new PullRequestsMsg.Loaded(List.of()));

        assertFalse(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        assertTrue(
                earlierRead.cmds().contains(new LoadRepositoryPullRequests()),
                earlierRead.cmds().toString());
    }

    @Test
    void aPullRequestGitHubMergedAtOnceShowsMergedUntilThePullRequestsAreReadAgain() {
        MergePullRequest cmd = new MergePullRequest(FEATURE, 42, Option.none(), MergeMethod.MERGE);
        Model merging = TestModels.running(withPr(), cmd);

        Next done = TestModels.done(merging, cmd, cmd.run(new Env(new StubGitProvider() {}, mergingAtOnce())));

        assertEquals(Option.of(FEATURE_PR), done.model().gitHub().pullRequests().flatMap(open -> open.get(FEATURE)));
        assertEquals(Option.of(new MergeStatus.Merged()), done.model().gitHub().merge(42));
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        // The merge changed the base branch on the remote.
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
    }

    @Test
    void aMergeGitHubDoesInTheBackgroundLeavesTheBranchFreeAndIsPending() {
        Model merging = asked(withPr());

        assertEquals(Option.none(), merging.jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")), merging.gitHub().merge(42));
        assertTrue(merging.gitHub().merging(42));
        assertEquals(
                "gh api --method PUT 'repos/{owner}/{repo}/pulls/42/merge-async' -f merge_method=merge -f sha=abc123",
                merging.outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aPullRequestGitHubIsMergingIsNotMergedAgain() {
        Model read = Update.update(
                        Update.update(asked(withPr()), new PullRequestMsg.Requested(FEATURE))
                                .model(),
                        new PullRequestMsg.DetailsLoaded(
                                42, 0, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();

        assertEquals(
                List.empty(),
                Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH))
                        .cmds());
    }

    @Test
    void eachTickAsksGitHubOnceHowAPendingMergeIsDoing() {
        Next ticked = Update.update(asked(withPr()), new PullRequestMsg.MergeTick());

        assertEquals(List.of(new CheckMerge(42, "merge-42")), ticked.cmds());
        // While it is asked, the next tick does not ask again.
        assertEquals(
                List.empty(),
                Update.update(ticked.model(), new PullRequestMsg.MergeTick()).cmds());

        Next stillPending = Update.update(
                ticked.model(), new PullRequestMsg.MergeChecked(42, "merge-42", new MergeStatus.Pending("merge-42")));
        assertEquals(
                List.of(new CheckMerge(42, "merge-42")),
                Update.update(stillPending.model(), new PullRequestMsg.MergeTick())
                        .cmds());
    }

    @Test
    void noTickAsksAnythingWithoutPendingMerges() {
        Model model = withPr();

        Next ticked = Update.update(model, new PullRequestMsg.MergeTick());

        assertEquals(List.empty(), ticked.cmds());
        assertEquals(model, ticked.model());
    }

    @Test
    void aMergeThatAskingAboutFailedIsAskedAboutAgain() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Next failed = Update.update(
                checking, new PullRequestMsg.MergeCheckFailed(42, "merge-42", refused("gh: Bad Gateway (HTTP 502)")));

        assertEquals(Option.none(), failed.model().popup(ErrorPopup.class));
        assertEquals(
                List.of(new CheckMerge(42, "merge-42")),
                Update.update(failed.model(), new PullRequestMsg.MergeTick()).cmds());
    }

    @Test
    void aMergeGitHubFinishedReadsThePullRequestsAndTheRemotesAgain() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Next merged =
                Update.update(checking, new PullRequestMsg.MergeChecked(42, "merge-42", new MergeStatus.Merged()));

        assertEquals(
                Option.of(new MergeStatus.Merged()), merged.model().gitHub().merge(42));
        assertTrue(
                merged.cmds().contains(new LoadRepositoryPullRequests()),
                merged.cmds().toString());
        assertTrue(
                merged.cmds().contains(new FetchCmd.ListRemotes()),
                merged.cmds().toString());
        assertEquals(
                List.empty(),
                Update.update(merged.model(), new PullRequestMsg.MergeTick()).cmds());
    }

    @Test
    void aMergeGitHubFailedIsShownAsAnErrorAndStaysShownUntilItIsMergedAgain() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Model failed = Update.update(
                        checking,
                        new PullRequestMsg.MergeChecked(
                                42, "merge-42", new MergeStatus.Failed("Required status check \"build\" is failing")))
                .model();

        assertEquals(
                Option.of("Merging the pull request of feature failed: Required status check \"build\" is failing"),
                errorText(failed));
        assertEquals(
                Option.of(new MergeStatus.Failed("Required status check \"build\" is failing")),
                Update.update(failed, new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                        .model()
                        .gitHub()
                        .merge(42));
        assertFalse(failed.gitHub().merging(42));
        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")),
                asked(failed).gitHub().merge(42));
    }

    @Test
    void aPullRequestGitHubAddedToTheMergeQueueSaysSo() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Model enqueued = Update.update(
                        checking, new PullRequestMsg.MergeChecked(42, "merge-42", new MergeStatus.Enqueued()))
                .model();

        assertEquals(Option.of(new MergeStatus.Enqueued()), enqueued.gitHub().merge(42));
        assertEquals(
                Option.of(new Notice.MergeEnqueued(FEATURE)), enqueued.outcome().notice());
    }

    @Test
    void anAnswerAboutAnEarlierMergeIsIgnored() {
        Model checking =
                Update.update(asked(withPr()), new PullRequestMsg.MergeTick()).model();

        Next answered =
                Update.update(checking, new PullRequestMsg.MergeChecked(42, "earlier", new MergeStatus.Merged()));

        assertEquals(checking, answered.model());
    }

    @Test
    void aMergeThatIsPendingForTooLongIsLeftToGitHub() {
        Model merging = asked(withPr().withNow(Instant.parse("2026-10-23T10:00:00Z")));

        Next ticked =
                Update.update(merging.withNow(Instant.parse("2026-10-23T10:10:05Z")), new PullRequestMsg.MergeTick());

        assertEquals(List.empty(), ticked.cmds());
        assertEquals(Option.none(), ticked.model().gitHub().merge(42));
        assertEquals(
                Option.of(new Notice.MergeStillPending(FEATURE)),
                ticked.model().outcome().notice());
    }

    /**
     * {@code model} once GitHub said the merge of pull request {@code number}, from {@code branch}, goes as
     * {@code status}.
     */
    private static Model askedFor(Model model, String branch, int number, MergeStatus status) {
        MergePullRequest cmd = new MergePullRequest(new BranchName(branch), number, Option.none(), MergeMethod.MERGE);
        return Update.update(model, new PullRequestMsg.MergeAsked(cmd, status, List.empty()))
                .model();
    }

    /**
     * Stack {@code number} of the open pull requests {@code numbers}, the bottom one first, as seen from the one at
     * {@code position}.
     */
    private static PullRequestStack stack(int number, int position, Integer... numbers) {
        List<PullRequestStack.Entry> entries = List.of(numbers)
                .zipWithIndex((pr, index) -> new PullRequestStack.Entry(index + 1, pr, PullRequestDetails.State.OPEN));
        return new PullRequestStack(number, position, entries.size(), entries);
    }

    /**
     * Pull request 41 at the bottom of a stack, and 42 of feature on top of it.
     */
    private static Model stackOfTwo() {
        PullRequest bottom = new PullRequest(
                41, "base-work", false, Checks.PASSING, Review.APPROVED, Option.of(stack(7, 1, 41, 42)));
        PullRequest top =
                new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED, Option.of(stack(7, 2, 41, 42)));
        return Update.update(model(), new PullRequestsMsg.Loaded(List.of(bottom, top)))
                .model();
    }

    @Test
    void aPullRequestWhoseMergeFailedIsMergingWhileOneAboveItMergesTheStack() {
        Model failedBelow = askedFor(stackOfTwo(), "base-work", 41, new MergeStatus.Failed("conflicts"));

        Model merging = askedFor(failedBelow, "feature", 42, new MergeStatus.Pending("merge-42"));

        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")), merging.gitHub().merge(41));
        assertTrue(merging.gitHub().merging(41));
    }

    @Test
    void mergingAPullRequestOnItsOwnAfterTheStackFailedLeavesTheFailureAbove() {
        Model failedAbove = askedFor(stackOfTwo(), "feature", 42, new MergeStatus.Failed("conflicts"));

        Model merging = askedFor(failedAbove, "base-work", 41, new MergeStatus.Pending("merge-41"));

        assertEquals(
                Option.of(new MergeStatus.Pending("merge-41")), merging.gitHub().merge(41));
        assertEquals(
                Option.of(new MergeStatus.Failed("conflicts")), merging.gitHub().merge(42));
    }

    @Test
    void mergingAStackedPullRequestMergesTheOnesBelowItToo() {
        PullRequest bottom = new PullRequest(
                41, "base-work", false, Checks.PASSING, Review.APPROVED, Option.of(stack(7, 1, 41, 42, 43)));
        PullRequest middle = new PullRequest(
                42, "feature", false, Checks.PASSING, Review.APPROVED, Option.of(stack(7, 2, 41, 42, 43)));
        PullRequest top = new PullRequest(
                43, "on-top", false, Checks.PASSING, Review.APPROVED, Option.of(stack(7, 3, 41, 42, 43)));
        PullRequest other = new PullRequest(
                40, "elsewhere", false, Checks.PASSING, Review.APPROVED, Option.of(stack(8, 1, 40, 44)));
        Model stacked = Update.update(model(), new PullRequestsMsg.Loaded(List.of(bottom, middle, top, other)))
                .model();

        Model merging = asked(stacked);

        assertEquals(
                Option.of(new MergeStatus.Pending("merge-42")), merging.gitHub().merge(41));
        assertEquals(Option.none(), merging.gitHub().merge(43));
        assertEquals(Option.none(), merging.gitHub().merge(40));
    }

    @Test
    void aMergeThatGitHubRefusesIsShownAsAnErrorAndKeepsThePullRequest() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42,
                                lastRead(shown),
                                StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next merging = Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        MergePullRequest cmd = (MergePullRequest) merging.cmds().head();
        // What gh says when e.g. a required check fails or a review is missing.
        StubGitHub refusing = new StubGitHub() {
            @Override
            public Either<GhFailure, GitHub.Merging> mergePullRequest(
                    int number, Option<GhCommit> head, MergeMethod method) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(
                                Option.of(
                                        "Pull request #42 is not mergeable: the base branch policy prohibits the merge."),
                                1),
                        List.of(ExecutedCommand.of("gh", "api", "--method", "PUT", "merge-async"))));
            }
        };

        Next failed = Update.update(merging.model(), cmd.run(new Env(new StubGitProvider() {}, refusing)));

        // The pull request dialog stays closed; the error comes up in its own popup.
        assertEquals(Option.none(), failed.model().popup(PullRequestPopup.class));
        assertTrue(
                failed.model().popup(ErrorPopup.class).isDefined(),
                failed.model().popup().toString());
        assertEquals(
                Option.of("Merging the pull request of feature failed: Pull request #42 is not mergeable: "
                        + "the base branch policy prohibits the merge."),
                errorText(failed.model()));
        assertEquals(Option.none(), failed.model().jobs().busy().get(new BranchRef.Local(new BranchName("feature"))));
        assertEquals(
                StubGitHub.empty().pullRequestDetails(42).get().listed(),
                failed.model().gitHub().pullRequests().flatMap(open -> open.get(new BranchName("feature"))));
    }

    @Test
    void openingThePullRequestInTheBrowserKeepsTheDialogOpen() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();

        Next opened = Update.update(shown, new PullRequestMsg.OpenConfirmed());

        assertEquals(List.of(new OpenPullRequest(new BranchName("feature"), 42)), opened.cmds());
        assertEquals(shown.popup(), opened.model().popup());

        Next done = Update.update(
                opened.model(), new OpenPullRequest(new BranchName("feature"), 42).succeeded(List.empty()));
        assertEquals(shown.popup(), done.model().popup());
    }

    @Test
    void thePullRequestOfABranchIsFoundByItsNameOnTheRemote() {
        Branch renamed = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.gitHub().pullRequest(renamed));
        assertEquals(Option.none(), withPr.gitHub().pullRequest(UNPUSHED));
    }

    @Test
    void thePullRequestOfABranchIsFoundEvenWhenItsRemoteNameHasASlash() {
        Branch onFork = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(
                        new BranchRef.Remote(new RemoteBranch("my/fork", new BranchName("feature"))), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.gitHub().pullRequest(onFork));
    }

    @Test
    void askingForAPullRequestAfterFailingToReadThemTellsWhy() {
        var failure = refused("gh: not logged in");
        Model failed =
                Update.update(model(), new PullRequestsMsg.LoadFailed(failure)).model();

        Next asked = Update.update(failed, new PullRequestMsg.Requested(new BranchName("feature")));

        assertEquals(
                Option.of(new AppError.PullRequestsUnavailable(failure)),
                asked.model().outcome().error());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void aCreatedPullRequestIsLoggedAndThePullRequestsAreReadAgain() {
        CreatePullRequest cmd = new CreatePullRequest(
                FEATURE,
                new RemoteBranch("origin", FEATURE),
                new RemoteBranch("origin", MAIN.name()),
                new NewPullRequest("Login", "", true));
        Model creating = confirmed(cmd.pullRequest()).model();

        Next done = TestModels.done(creating, cmd, cmd.run(new Env(new StubGitProvider() {}, StubGitHub.empty())));

        assertTrue(
                done.cmds().contains(new LoadRepositoryPullRequests()),
                done.cmds().toString());
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(new BranchName("feature"))));
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
        assertEquals(
                "gh pr create --head feature --base main --title Login --body '' --draft",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aCreatedPullRequestShowsAtOnceEvenIfGitHubDoesNotListItYet() {
        CreatePullRequest cmd = new CreatePullRequest(
                FEATURE,
                new RemoteBranch("origin", FEATURE),
                new RemoteBranch("origin", MAIN.name()),
                new NewPullRequest("Login", "", false));
        PullRequest created = new PullRequest(43, "feature", false, Checks.NONE, Review.NONE);
        Model creating = confirmed(cmd.pullRequest()).model();

        Next done = Update.update(creating, new CreatePullRequestMsg.Created(cmd, Option.of(created), List.empty()));

        assertEquals(
                Option.of(created),
                done.model().gitHub().pullRequests().flatMap(open -> open.get(new BranchName("feature"))));
        assertTrue(done.cmds().contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void aPullRequestFromThePullRequestsScreenIsShownAndItsDetailsRead() {
        PullRequestSummary summary = new PullRequestSummary(
                "owner/repo",
                9,
                "From a fork",
                "bob",
                "patch-1",
                true,
                true,
                Checks.FAILING,
                Review.REQUIRED,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9");

        Model ofOwnerRepo = loaded().withPrOverview(
                        net.vanfleteren.jazygit.state.prs.PrOverview.INITIAL.withRepositoryName(
                                Option.of("owner/repo")));

        Next shown = Update.update(ofOwnerRepo, new PullRequestMsg.SummaryRequested(summary));

        assertEquals(
                Option.of(PullRequestPopup.loading(
                        new BranchName("patch-1"),
                        new PullRequest(9, "patch-1", true, Checks.FAILING, Review.REQUIRED),
                        Instant.EPOCH,
                        0)),
                shown.model().popup(PullRequestPopup.class));
        assertEquals(List.of(new LoadPullRequestDetails(9, lastRead(shown.model()))), shown.cmds());
    }

    @Test
    void aPullRequestOfAnotherRepositoryOpensInTheBrowser() {
        PullRequestSummary elsewhere = new PullRequestSummary(
                "other/repo",
                3,
                "Elsewhere",
                "carol",
                "fix",
                false,
                false,
                Checks.NONE,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/other/repo/pull/3");
        Model ofOwnerRepo = loaded().withPrOverview(
                        net.vanfleteren.jazygit.state.prs.PrOverview.INITIAL.withRepositoryName(
                                Option.of("owner/repo")));

        Next opened = Update.update(ofOwnerRepo, new PullRequestMsg.SummaryRequested(elsewhere));

        assertEquals(Option.none(), opened.model().popup());
        assertEquals(List.of(new BrowsePullRequestCmd(elsewhere)), opened.cmds());
    }

    private static PullRequestSummary summary(String repository) {
        return new PullRequestSummary(
                repository,
                9,
                "From a fork",
                "bob",
                "patch-1",
                false,
                true,
                Checks.NONE,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/" + repository + "/pull/9");
    }

    private static Model ofOwnerRepo(RepoStatus status) {
        return Update.update(loaded(), new LoadMsg.StatusLoaded(status))
                .model()
                .withPrOverview(net.vanfleteren.jazygit.state.prs.PrOverview.INITIAL.withRepositoryName(
                        Option.of("owner/repo")));
    }

    @Test
    void aPullRequestOfTheRepositoryIsCheckedOutWhenOnlyUntrackedFilesAreAround() {
        PullRequestSummary pr = summary("owner/repo");

        Next checkingOut = Update.update(ofOwnerRepo(DIRTY), new CheckoutPullRequestMsg.Requested(pr));

        assertEquals(List.of(new CheckoutPullRequestCmd(pr)), checkingOut.cmds());
        assertEquals(Option.none(), checkingOut.model().outcome().error());
    }

    @Test
    void forAPullRequestWhoseBranchAnotherWorktreeHasSwitchingToThatWorktreeIsOffered() {
        BranchName patch = new BranchName("patch-1");
        Model elsewhere = localBranchesRead(
                        ofOwnerRepo(CLEAN),
                        List.of(new Branch(
                                patch,
                                false,
                                new Sha("bbbb"),
                                Instant.EPOCH,
                                Option.none(),
                                false,
                                false,
                                new Tracking.None(),
                                Option.of(java.nio.file.Path.of("/repos/linked")))))
                .model();
        PullRequestSummary fork = summary("owner/repo");
        PullRequestSummary own = new PullRequestSummary(
                fork.repository(),
                fork.number(),
                fork.title(),
                fork.author(),
                "patch-1",
                false,
                false,
                fork.checks(),
                fork.review(),
                fork.updatedAt(),
                fork.url());

        Next offered = Update.update(elsewhere, new CheckoutPullRequestMsg.Requested(own));

        assertEquals(List.empty(), offered.cmds());
        assertEquals(
                Option.of(new SwitchWorktreePopup(patch, "/repos/linked")),
                offered.model().popup());
        // The branch of one from a fork has another name here, so it is checked out as ever.
        assertEquals(
                List.of(new CheckoutPullRequestCmd(fork)),
                Update.update(elsewhere, new CheckoutPullRequestMsg.Requested(fork))
                        .cmds());
    }

    @Test
    void aPullRequestIsNotCheckedOutOverChanges() {
        RepoStatus changed = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)));

        Next refused = Update.update(ofOwnerRepo(changed), new CheckoutPullRequestMsg.Requested(summary("owner/repo")));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(
                Option.of(new AppError.ChangesBeforeCheckout("owner/repo#9")),
                refused.model().outcome().error());
    }

    @Test
    void aPullRequestOfAnotherRepositoryIsNotCheckedOut() {
        Next refused = Update.update(ofOwnerRepo(CLEAN), new CheckoutPullRequestMsg.Requested(summary("other/repo")));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(
                Option.of(new AppError.OtherRepository("other/repo#9")),
                refused.model().outcome().error());
    }

    @Test
    void aPullRequestIsNotCheckedOutWhileItIsNotKnownWhichRepositoryThisIs() {
        Model reading = Update.update(loaded(), new LoadMsg.StatusLoaded(CLEAN)).model();

        Next refused = Update.update(reading, new CheckoutPullRequestMsg.Requested(summary("owner/repo")));

        assertEquals(List.empty(), refused.cmds());
        assertEquals(
                Option.of(new AppError.PullRequestsNotRead()),
                refused.model().outcome().error());
    }

    private static PullRequestSummary own(String headBranch) {
        PullRequestSummary fork = summary("owner/repo");
        return new PullRequestSummary(
                fork.repository(),
                fork.number(),
                fork.title(),
                fork.author(),
                headBranch,
                false,
                false,
                fork.checks(),
                fork.review(),
                fork.updatedAt(),
                fork.url());
    }

    /**
     * The repository with {@code status}, its main worktree at {@code /repos/app}.
     */
    private static Model withMainWorktree(RepoStatus status) {
        return Update.update(
                        ofOwnerRepo(status),
                        new LoadMsg.WorktreesLoaded(List.of(new Worktree(
                                java.nio.file.Path.of("/repos/app"),
                                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("aaaa")))),
                                true,
                                true,
                                Option.none(),
                                false))))
                .model();
    }

    @Test
    void aPullRequestIsCheckedOutInANewWorktreeNamedAfterItsBranchOrForAForkItsNumberChangesOrNot() {
        PullRequestSummary fork = summary("owner/repo");

        Next asked = Update.update(withMainWorktree(DIRTY), new CheckoutPullRequestMsg.WorktreeRequested(fork));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.PullRequest(fork), "/repos/app-pr-9")),
                asked.model().popup());
        assertEquals(
                List.of(new AddWorktreeCmd(new WorktreeSource.PullRequest(fork), "/repos/app-pr-9")),
                Update.update(asked.model(), new Msg.PopupConfirmed(NewWorktreePopup.class))
                        .cmds());

        RepoStatus changed = new RepoStatus(
                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)));
        PullRequestSummary own = own("feature/login");
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.PullRequest(own), "/repos/app-feature-login")),
                Update.update(withMainWorktree(changed), new CheckoutPullRequestMsg.WorktreeRequested(own))
                        .model()
                        .popup());
    }

    @Test
    void aPullRequestWhoseBranchIsCheckedOutHereGetsNoWorktreeAndOneOfAnotherRepositoryNeither() {
        Model onPatch = localBranchesRead(
                        withMainWorktree(CLEAN),
                        List.of(Fixtures.branch(new BranchName("patch-1"), true, new Sha("bbbb"))))
                .model();

        Next here = Update.update(onPatch, new CheckoutPullRequestMsg.WorktreeRequested(own("patch-1")));
        assertEquals(Option.none(), here.model().popup(NewWorktreePopup.class));
        assertEquals(
                Option.of(new AppError.PullRequestCheckedOutHere("owner/repo#9")),
                here.model().outcome().error());

        Next other = Update.update(
                withMainWorktree(CLEAN), new CheckoutPullRequestMsg.WorktreeRequested(summary("other/repo")));
        assertEquals(Option.none(), other.model().popup(NewWorktreePopup.class));
        assertEquals(
                Option.of(new AppError.OtherRepository("other/repo#9")),
                other.model().outcome().error());
    }

    @Test
    void forAPullRequestWhoseBranchAnotherWorktreeHasSwitchingToItIsOfferedInsteadOfANewOne() {
        BranchName patch = new BranchName("patch-1");
        Model elsewhere = localBranchesRead(
                        withMainWorktree(CLEAN),
                        List.of(new Branch(
                                patch,
                                false,
                                new Sha("bbbb"),
                                Instant.EPOCH,
                                Option.none(),
                                false,
                                false,
                                new Tracking.None(),
                                Option.of(java.nio.file.Path.of("/repos/linked")))))
                .model();

        assertEquals(
                Option.of(new SwitchWorktreePopup(patch, "/repos/linked")),
                Update.update(elsewhere, new CheckoutPullRequestMsg.WorktreeRequested(own("patch-1")))
                        .model()
                        .popup());
    }

    @Test
    void whileTheWorktreeOfAPullRequestIsBeingAddedItsBranchIsBusyAndAnotherIsNotAsked() {
        PullRequestSummary own = own("feature/login");
        Model asked = Update.update(withMainWorktree(CLEAN), new CheckoutPullRequestMsg.WorktreeRequested(own))
                .model();

        Next adding = TestModels.stepped(asked, new Msg.PopupConfirmed(NewWorktreePopup.class));
        BranchRef login = new BranchRef.Local(new BranchName("feature/login"));
        assertEquals(
                Option.of(AddWorktreeCmd.ADD_WORKTREE),
                adding.model().jobs().busy().get(login));

        Next again = Update.update(adding.model(), new CheckoutPullRequestMsg.WorktreeRequested(own));
        assertEquals(Option.none(), again.model().popup(NewWorktreePopup.class));
        assertEquals(List.empty(), again.cmds());

        // The branch gh gives one from a fork is not known up front, so nothing is busy for it.
        assertEquals(
                Option.none(),
                new AddWorktreeCmd(new WorktreeSource.PullRequest(summary("owner/repo")), "/repos/app-pr-9")
                        .busyBranch());
    }
}

package net.vanfleteren.jazygit.feature.github;

import static net.vanfleteren.jazygit.state.TestModels.*;
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
import net.vanfleteren.jazygit.feature.fetch.FetchCmd;
import net.vanfleteren.jazygit.feature.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestCmd;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg.BaseMoved;
import net.vanfleteren.jazygit.feature.github.create.NewPullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class PullRequestUpdateTest {

    private static final Branch MAIN = new Branch(
            new Branch.Name("main"),
            true,
            new Sha("1"),
            Instant.EPOCH,
            true,
            true,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("main")), 0, 0)));
    private static final Branch RELEASE = new Branch(
            new Branch.Name("release"),
            false,
            new Sha("2"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("release")), 0, 0)));
    private static final Branch PUSHED = new Branch(
            new Branch.Name("feature"),
            false,
            new Sha("3"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("feature")), 0, 0)));
    private static final Branch UNPUSHED = new Branch(new Branch.Name("local"), false, new Sha("4"));
    private static final Branch AHEAD = new Branch(
            new Branch.Name("ahead"),
            false,
            new Sha("5"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("ahead")), 2, 0)));
    private static final Branch.Name FEATURE = PUSHED.name();
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

        Next asked = Update.update(starting, new PullRequestMsg.Requested(new Branch.Name("feature")));

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
                        FEATURE, FEATURE, List.of(MAIN.name(), RELEASE.name(), AHEAD.name()), remote("main"))),
                reading.cmds());
        assertEquals(Option.none(), reading.model().popup());
    }

    @Test
    void theParentIsOfferedFirstThenTheDefaultBranch() {
        Next reading = Update.update(model(), new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(AHEAD.name())));

        assertEquals(
                List.of(new ReadTitle(
                        FEATURE, FEATURE, List.of(AHEAD.name(), MAIN.name(), RELEASE.name()), remote("ahead"))),
                reading.cmds());
    }

    @Test
    void aParentThatCannotBeChosenIsIgnored() {
        Next reading = Update.update(model(), new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(UNPUSHED.name())));

        assertEquals(
                List.of(new ReadTitle(
                        FEATURE, FEATURE, List.of(MAIN.name(), RELEASE.name(), AHEAD.name()), remote("main"))),
                reading.cmds());
    }

    @Test
    void withOneBaseTheTitleIsReadWithoutTheParent() {
        Model one = localBranchesRead(model(), List.of(PUSHED, MAIN)).model();

        Next reading = Update.update(one, new PullRequestMsg.Requested(FEATURE));

        assertEquals(List.of(new ReadTitle(FEATURE, FEATURE, List.of(MAIN.name()), remote("main"))), reading.cmds());
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
            public Either<GitError.ProcessError, Option<Branch.Name>> parentBranch(Branch.Name branch) {
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
            public Either<GitError.ProcessError, Option<Branch.Name>> parentBranch(Branch.Name branch) {
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
        return new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name(branch)));
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
                FEATURE, FEATURE, List.of(MAIN.name(), RELEASE.name(), AHEAD.name()), subject);
    }

    @Test
    void theTitleReadIsOfferedToEditWithTheBaseItWasReadFor() {
        Next asking = Update.update(model(), titleRead(Option.of("Add login")));

        assertEquals(
                Option.of(new NewPullRequestPopup(
                        FEATURE,
                        FEATURE,
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
        Model closed =
                Update.update(creating(), new CreatePullRequestMsg.Cancelled()).model();

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

    @Test
    void confirmingCreatesThePullRequestAsEnteredAndKeepsTheBranchBusy() {
        NewPullRequest entered = new NewPullRequest(" Add login ", "Why", true);

        Next created = confirmed(entered);

        assertEquals(
                List.of(new CreatePullRequest(
                        FEATURE, FEATURE, MAIN.name(), new NewPullRequest("Add login", "Why", true))),
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
        Model closed =
                Update.update(creating(), new CreatePullRequestMsg.Cancelled()).model();

        assertEquals(
                closed,
                Update.update(closed, new CreatePullRequestMsg.PullRequestEdited(new NewPullRequest("x", "", false)))
                        .model());
    }

    @Test
    void cancellingTheNewPullRequestClosesThePopup() {
        assertEquals(
                Option.none(),
                Update.update(creating(), new CreatePullRequestMsg.Cancelled())
                        .model()
                        .popup());
    }

    /**
     * Confirming {@code pullRequest} as what the new pull request of the feature branch into main says.
     */
    private static Next confirmed(NewPullRequest pullRequest) {
        Model edited = Update.update(creating(), new CreatePullRequestMsg.PullRequestEdited(pullRequest))
                .model();
        return Update.update(edited, new CreatePullRequestMsg.Confirmed());
    }

    /**
     * Asking what the new pull request of the feature branch into main says.
     */
    private static Model creating() {
        return Update.update(model(), titleRead(Option.of("Add login"))).model();
    }

    @Test
    void aBranchThatIsNotPushedOrHasUnpushedCommitsMustBePushedFirst() {
        assertEquals(
                Option.of(new AppError.NotPushed(new Branch.Name("local"))),
                Update.update(model(), new PullRequestMsg.Requested(new Branch.Name("local")))
                        .model()
                        .outcome()
                        .error());
        assertEquals(
                Option.of(new AppError.NotPushed(new Branch.Name("ahead"))),
                Update.update(model(), new PullRequestMsg.Requested(new Branch.Name("ahead")))
                        .model()
                        .outcome()
                        .error());
    }

    @Test
    void aBranchWithAPullRequestShowsItWhileItsDetailsAreRead() {
        Next shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                Option.of(PullRequestPopup.loading(new Branch.Name("feature"), FEATURE_PR)),
                shown.model().popup());
        assertEquals(List.of(new LoadPullRequestDetails(42)), shown.cmds());
    }

    @Test
    void theDetailsReadShowInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();

        Model loaded =
                Update.update(shown, new LoadPullRequestDetails(42).run(env())).model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new Branch.Name("feature"), FEATURE_PR)
                        .with(Either.right(details))),
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
                details.state(),
                true,
                details.headBranch(),
                details.baseBranch(),
                details.createdAt(),
                0,
                0,
                0,
                details.mergeable(),
                Review.CHANGES_REQUESTED,
                0,
                details.reviewThreads(),
                List.empty(),
                List.empty(),
                List.of(
                        new PullRequestDetails.Check("build", Checks.PASSING),
                        new PullRequestDetails.Check("test", Checks.FAILING)));

        Model loaded = Update.update(shown, new PullRequestMsg.DetailsLoaded(42, failing))
                .model();
        // Also when the dialog was closed meanwhile.
        Model closed = Update.update(
                        Update.update(shown, new PullRequestMsg.InfoClosed()).model(),
                        new PullRequestMsg.DetailsLoaded(42, failing))
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
        PullRequestDetails merged = new PullRequestDetails(
                42,
                details.title(),
                details.author(),
                PullRequestDetails.State.MERGED,
                details.draft(),
                details.headBranch(),
                details.baseBranch(),
                details.createdAt(),
                0,
                0,
                0,
                details.mergeable(),
                details.review(),
                0,
                details.reviewThreads(),
                List.empty(),
                List.empty(),
                List.empty());

        Model loaded = Update.update(shown, new PullRequestMsg.DetailsLoaded(42, merged))
                .model();

        assertEquals(Option.none(), loaded.gitHub().pullRequest(PUSHED));
    }

    @Test
    void failingToReadTheDetailsShowsWhyInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        var failure = refused("gh: not logged in");

        Model failed = Update.update(shown, new PullRequestMsg.DetailsFailed(42, failure))
                .model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new Branch.Name("feature"), FEATURE_PR)
                        .with(Either.left(failure))),
                failed.popup());
        assertEquals(Option.none(), failed.outcome().error());
    }

    @Test
    void detailsReadAfterThePopupClosedOrForAnotherPullRequestAreDropped() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(7).get();

        assertEquals(
                shown,
                Update.update(shown, new PullRequestMsg.DetailsLoaded(7, details))
                        .model());
        Model closed = Update.update(shown, new PullRequestMsg.InfoClosed()).model();
        assertEquals(Option.none(), closed.popup());
        assertEquals(
                closed,
                Update.update(closed, new PullRequestMsg.DetailsLoaded(42, details))
                        .model());
    }

    @Test
    void choosingToMergeOrCloseStartsItOnceTheDetailsShowItCan() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
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
                                42, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next merging = Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        Next closing = Update.update(read, new PullRequestMsg.CloseChosen());

        assertEquals(List.of(new MergePullRequest(new Branch.Name("feature"), 42, MergeMethod.SQUASH)), merging.cmds());
        assertEquals(Option.none(), merging.model().popup());
        assertEquals(
                Option.of(PullRequestCmd.MERGE_PR),
                merging.model().jobs().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertEquals(List.of(new ClosePullRequest(new Branch.Name("feature"), 42)), closing.cmds());
        assertEquals(
                Option.of(PullRequestCmd.CLOSE_PR),
                closing.model().jobs().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
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
                                42, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next drafting = Update.update(read, new PullRequestMsg.DraftChosen());

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
                                42, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next drafting = Update.update(read, new PullRequestMsg.DraftChosen());
        SetDraft cmd = (SetDraft) drafting.cmds().head();

        Next done = Update.update(drafting.model(), cmd.run(env()));

        assertEquals(Option.of(true), done.model().popup(PullRequestPopup.class).flatMap(PullRequestPopup::draft));
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertTrue(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
        assertEquals(
                "gh pr ready 42 --undo",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void markingADraftReadyRunsGhPrReady() {
        SetDraft cmd = new SetDraft(FEATURE, 42, false);
        Model marking = withPr().withBusyBranch(new BranchRef.Local(FEATURE), PullRequestCmd.READY_PR);

        Next done = Update.update(marking, cmd.run(env()));

        assertEquals(
                "gh pr ready 42",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aMergedPullRequestIsReadAgainOnceAnEarlierReadIsDone() {
        MergePullRequest cmd = new MergePullRequest(new Branch.Name("feature"), 42, MergeMethod.MERGE);
        // The running read started before the merge, and would list the pull request as still open.
        Model merging = withPr().withBusyBranch(
                        new BranchRef.Local(new Branch.Name("feature")), PullRequestCmd.MERGE_PR)
                .mapGitHub(gitHub -> gitHub.withReading(io.vavr.collection.HashSet.of(new LoadPullRequests())));

        Next done = Update.update(merging, cmd.run(new Env(new StubGitProvider() {}, StubGitHub.empty())));
        Next earlierRead = Update.update(done.model(), new PullRequestsMsg.Loaded(List.of()));

        assertFalse(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
        assertTrue(
                earlierRead.cmds().contains(new LoadPullRequests()),
                earlierRead.cmds().toString());
    }

    @Test
    void aMergedPullRequestIsNoLongerListedAndThePullRequestsAreReadAgain() {
        MergePullRequest cmd = new MergePullRequest(new Branch.Name("feature"), 42, MergeMethod.MERGE);
        Model merging =
                withPr().withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), PullRequestCmd.MERGE_PR);

        Next done = Update.update(merging, cmd.run(new Env(new StubGitProvider() {}, StubGitHub.empty())));

        assertEquals(
                Option.none(),
                done.model().gitHub().pullRequests().flatMap(open -> open.get(new Branch.Name("feature"))));
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertTrue(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
        // The merge changed the base branch on the remote.
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
        assertEquals(
                "gh pr merge 42 --merge",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aMergeThatGitHubRefusesIsShownAsAnErrorAndKeepsThePullRequest() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();
        Model read = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                42, StubGitHub.empty().pullRequestDetails(42).get()))
                .model();
        Next merging = Update.update(read, new PullRequestMsg.MergeChosen(MergeMethod.SQUASH));
        MergePullRequest cmd = (MergePullRequest) merging.cmds().head();
        // What gh says when e.g. a required check fails or a review is missing.
        StubGitHub refusing = new StubGitHub() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> mergePullRequest(int number, MergeMethod method) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(
                                Option.of(
                                        "Pull request #42 is not mergeable: the base branch policy prohibits the merge."),
                                1),
                        List.of(ExecutedCommand.of("gh", "pr", "merge", "42", "--squash"))));
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
        assertEquals(Option.none(), failed.model().jobs().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertEquals(
                StubGitHub.empty().pullRequestDetails(42).get().listed(),
                failed.model().gitHub().pullRequests().flatMap(open -> open.get(new Branch.Name("feature"))));
    }

    @Test
    void openingThePullRequestInTheBrowserKeepsTheDialogOpen() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new Branch.Name("feature")))
                .model();

        Next opened = Update.update(shown, new PullRequestMsg.OpenConfirmed());

        assertEquals(List.of(new OpenPullRequest(new Branch.Name("feature"), 42)), opened.cmds());
        assertEquals(shown.popup(), opened.model().popup());

        Next done = Update.update(
                opened.model(), new OpenPullRequest(new Branch.Name("feature"), 42).succeeded(List.empty()));
        assertEquals(shown.popup(), done.model().popup());
    }

    @Test
    void thePullRequestOfABranchIsFoundByItsNameOnTheRemote() {
        Branch renamed = new Branch(
                new Branch.Name("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("feature")), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.gitHub().pullRequest(renamed));
        assertEquals(Option.none(), withPr.gitHub().pullRequest(UNPUSHED));
    }

    @Test
    void thePullRequestOfABranchIsFoundEvenWhenItsRemoteNameHasASlash() {
        Branch onFork = new Branch(
                new Branch.Name("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new RemoteBranch("my/fork", new Branch.Name("feature")), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.gitHub().pullRequest(onFork));
    }

    @Test
    void askingForAPullRequestAfterFailingToReadThemTellsWhy() {
        var failure = refused("gh: not logged in");
        Model failed =
                Update.update(model(), new PullRequestsMsg.LoadFailed(failure)).model();

        Next asked = Update.update(failed, new PullRequestMsg.Requested(new Branch.Name("feature")));

        assertEquals(
                Option.of(new AppError.PullRequestsUnavailable(failure)),
                asked.model().outcome().error());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void aCreatedPullRequestIsLoggedAndThePullRequestsAreReadAgain() {
        CreatePullRequest cmd =
                new CreatePullRequest(FEATURE, FEATURE, MAIN.name(), new NewPullRequest("Login", "", true));
        Model creating = confirmed(cmd.pullRequest()).model();

        Next done = Update.update(creating, cmd.run(new Env(new StubGitProvider() {}, StubGitHub.empty())));

        assertTrue(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
        assertEquals(Option.none(), done.model().jobs().busy().get(new BranchRef.Local(new Branch.Name("feature"))));
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
        assertEquals(
                "gh pr create --head feature --base main --title Login --body '' --draft",
                done.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aCreatedPullRequestShowsAtOnceEvenIfGitHubDoesNotListItYet() {
        CreatePullRequest cmd =
                new CreatePullRequest(FEATURE, FEATURE, MAIN.name(), new NewPullRequest("Login", "", false));
        PullRequest created = new PullRequest(43, "feature", false, Checks.NONE, Review.NONE);
        Model creating = confirmed(cmd.pullRequest()).model();

        Next done = Update.update(creating, new CreatePullRequestMsg.Created(cmd, Option.of(created), List.empty()));

        assertEquals(
                Option.of(created),
                done.model().gitHub().pullRequests().flatMap(open -> open.get(new Branch.Name("feature"))));
        assertTrue(done.cmds().contains(new LoadPullRequests()));
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
                Option.of(new PullRequestPopup(
                        new Branch.Name("patch-1"),
                        new PullRequest(9, "patch-1", true, Checks.FAILING, Review.REQUIRED),
                        Option.none())),
                shown.model().popup(PullRequestPopup.class));
        assertEquals(List.of(new LoadPullRequestDetails(9)), shown.cmds());
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
        Branch.Name patch = new Branch.Name("patch-1");
        Model elsewhere = localBranchesRead(
                        ofOwnerRepo(CLEAN),
                        List.of(new Branch(
                                patch,
                                false,
                                new Sha("bbbb"),
                                Instant.EPOCH,
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
                RepoStatus.Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                List.of(new FileEntry("a.txt", ChangeType.MODIFIED)));

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
                                Option.of(new Sha("aaaa")),
                                Option.of(new Branch.Name("main")),
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
                Update.update(asked.model(), new AddWorktreeMsg.Confirmed()).cmds());

        RepoStatus changed = new RepoStatus(
                RepoStatus.Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                List.of(new FileEntry("a.txt", ChangeType.MODIFIED)));
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
                        withMainWorktree(CLEAN), List.of(new Branch(new Branch.Name("patch-1"), true, new Sha("bbbb"))))
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
        Branch.Name patch = new Branch.Name("patch-1");
        Model elsewhere = localBranchesRead(
                        withMainWorktree(CLEAN),
                        List.of(new Branch(
                                patch,
                                false,
                                new Sha("bbbb"),
                                Instant.EPOCH,
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

        Next adding = Update.update(asked, new AddWorktreeMsg.Confirmed());
        BranchRef login = new BranchRef.Local(new Branch.Name("feature/login"));
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

package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubBranches;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.changes.fetch.FetchCmd;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestMsg;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestMsg.BaseMoved;
import net.vanfleteren.jazygit.features.github.create.NewPullRequestPopup;
import net.vanfleteren.jazygit.features.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Creating a pull request: which base it is offered against, its title, and confirming it.
 */
class CreatePullRequestUpdateTest {

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
    void beforeThePullRequestsAreReadNoneIsCreated() {
        Model starting = localBranchesRead(loaded(), List.of(PUSHED, MAIN)).model();

        Next asked = Update.update(starting, new PullRequestMsg.Requested(new BranchName("feature")));

        assertEquals(
                Option.of(new PullRequestsError.NotRead()),
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
                Option.of(new GitHubError.NoBase(FEATURE)),
                next.model().outcome().error());
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
                Option.of(new GitHubError.NoBase(FEATURE)),
                next.model().outcome().error());
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
        Git git = StubGit.of()
                .withHistory(new StubHistory() {
                    @Override
                    public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                        throw new AssertionError("counted");
                    }
                })
                .withBranches(new StubBranches() {
                    @Override
                    public Either<GitError, Option<BranchName>> parent(BranchName branch) {
                        return Either.right(Option.of(RELEASE.name()));
                    }
                });

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(RELEASE.name())),
                readParent().run(new Env(git, StubGitHub.of())));
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
        Git git = StubGit.of()
                .withHistory(new StubHistory() {
                    @Override
                    public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                        return Either.right(base.equals(remote("ahead")) ? 1 : 4);
                    }
                })
                .withBranches(new StubBranches() {
                    @Override
                    public Either<GitError, Option<BranchName>> parent(BranchName branch) {
                        return Either.right(Option.of(UNPUSHED.name()));
                    }
                });

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(AHEAD.name())),
                readParent().run(new Env(git, StubGitHub.of())));
    }

    @Test
    void failingToCountLeavesNoParent() {
        Git git = StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                return Either.left(new GitError.CommandFailed("boom", 128));
            }
        });

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.none()),
                readParent().run(new Env(git, StubGitHub.of())));
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
                StubGit.of().withHistory(new StubHistory() {
                    @Override
                    public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                        return Either.right(counts.get(base).get());
                    }
                }),
                StubGitHub.of());
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
                        new NewPullRequest("Add login", "", false),
                        true)),
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
        Model closed = Update.update(creating(), new PopupMsg.Dismissed(NewPullRequestPopup.class))
                .model();

        assertEquals(
                Option.none(),
                Update.update(closed, new CreatePullRequestMsg.BaseTitleRead(FEATURE, MAIN.name(), Option.of("x")))
                        .model()
                        .popup());
    }

    @Test
    void theTitleIsReadAgainFromTheFirstCommitNotOnThePickedBase() {
        Git git = StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
                return Either.right(Option.of(
                        branch.equals(new BranchRef.Local(FEATURE)) && base.equals(remote("release"))
                                ? "Fix release"
                                : "wrong"));
            }
        });

        assertEquals(
                new CreatePullRequestMsg.BaseTitleRead(FEATURE, RELEASE.name(), Option.of("Fix release")),
                new RereadTitle(FEATURE, RELEASE.name(), remote("release")).run(new Env(git, StubGitHub.of())));
    }

    @Test
    void aBaseIsMergedIntoUnderItsNameOnTheRemote() {
        Model branches = localBranchesRead(loaded(), List.of(PUSHED, DEV, MAIN)).model();
        Model loaded = Update.update(branches, PullRequestFixtures.listed(List.empty()))
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
        Git git = StubGit.of()
                .withHistory(new StubHistory() {
                    @Override
                    public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                        throw new AssertionError("counted");
                    }
                })
                .withBranches(new StubBranches() {
                    @Override
                    public Either<GitError, Option<BranchName>> parent(BranchName branch) {
                        return Either.right(Option.of(new BranchName("development")));
                    }
                });
        ReadParent read = new ReadParent(
                FEATURE, List.of(new Base(MAIN.name(), remote("main")), new Base(DEV.name(), remote("development"))));

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(DEV.name())),
                read.run(new Env(git, StubGitHub.of())));
    }

    @Test
    void aParentRecordedUnderALocalNameIsTheBaseOfThatName() {
        ReadParent read = new ReadParent(
                FEATURE, List.of(new Base(MAIN.name(), remote("main")), new Base(DEV.name(), remote("development"))));

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(DEV.name())),
                read.run(new Env(parentRecorded("dev"), StubGitHub.of())));
    }

    @Test
    void aNameOnTheRemoteWinsOverTheSameLocalName() {
        ReadParent read = new ReadParent(
                FEATURE,
                List.of(new Base(new BranchName("stable"), remote("main")), new Base(MAIN.name(), remote("trunk"))));

        assertEquals(
                new CreatePullRequestMsg.ParentRead(FEATURE, Option.of(new BranchName("stable"))),
                read.run(new Env(parentRecorded("main"), StubGitHub.of())));
    }

    private static Git parentRecorded(String name) {
        return StubGit.of()
                .withHistory(new StubHistory() {
                    @Override
                    public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                        throw new AssertionError("counted");
                    }
                })
                .withBranches(new StubBranches() {
                    @Override
                    public Either<GitError, Option<BranchName>> parent(BranchName branch) {
                        return Either.right(Option.of(new BranchName(name)));
                    }
                });
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
        Model closed = Update.update(creating(), new PopupMsg.Dismissed(NewPullRequestPopup.class))
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
                Update.update(creating(), new PopupMsg.Dismissed(NewPullRequestPopup.class))
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
    void aCreatedPullRequestIsLoggedAndThePullRequestsAreReadAgain() {
        CreatePullRequest cmd = new CreatePullRequest(
                FEATURE,
                new RemoteBranch("origin", FEATURE),
                new RemoteBranch("origin", MAIN.name()),
                new NewPullRequest("Login", "", true));
        Model creating = confirmed(cmd.pullRequest()).model();

        Next done = TestModels.done(creating, cmd, cmd.run(new Env(StubGit.of(), StubGitHub.of())));

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
        PullRequest created =
                new PullRequest(new PrNumber(43), new GhBranch("feature"), false, Checks.NONE, Review.NONE);
        Model creating = confirmed(cmd.pullRequest()).model();

        Next done = Update.update(creating, new CreatePullRequestMsg.Created(cmd, Option.of(created), List.empty()));

        assertEquals(
                Option.of(created),
                done.model()
                        .part(GitHubState.PART)
                        .pullRequests()
                        .flatMap(open -> open.get(new BranchName("feature"))));
        assertTrue(done.cmds().contains(new LoadRepositoryPullRequests()));
    }
}

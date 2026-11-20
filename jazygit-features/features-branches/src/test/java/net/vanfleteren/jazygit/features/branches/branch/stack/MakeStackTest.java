package net.vanfleteren.jazygit.features.branches.branch.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.gh.stacks.StubStacks;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.BaseDistance;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.stack.StackChoice.BaseChange;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.github.StackChange;
import net.vanfleteren.jazygit.kernel.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts;
import net.vanfleteren.jazygit.kernel.ui.text.NoticeText;
import org.junit.jupiter.api.Test;

/**
 * Making a stack from the menu of a branch: choosing its bottom and its top, and linking the pull requests between
 * them on GitHub, after changing the bases that do not make one line (ADR 73).
 */
class MakeStackTest {

    private static BranchName name(String name) {
        return new BranchName(name);
    }

    private static Branch branch(String name) {
        return Fixtures.branch(name(name), false, new Sha("aaaa"));
    }

    // The main branch, a root.
    private static final Branch MAIN =
            Fixtures.branch(name("main"), false, new Sha("aaaa"), Instant.EPOCH, false, true);

    private static PullRequest pr(int number, String head, String base) {
        return new PullRequest(
                new PrNumber(number),
                new GhBranch(head),
                Option.of(new GhBranch(base)),
                false,
                Checks.NONE,
                Review.NONE,
                Option.none(),
                Option.none());
    }

    // main ← feat/a ← feat/b ← feat/c; feat/b's pull request is still on main.
    private static Model model(PullRequest b) {
        Model read = TestModels.localBranchesRead(
                        TestModels.loaded(), List.of(MAIN, branch("feat/a"), branch("feat/b"), branch("feat/c")))
                .model();
        return read.mapRepo(repo -> repo.withParents(HashMap.of(
                        name("feat/a"), name("main"),
                        name("feat/b"), name("feat/a"),
                        name("feat/c"), name("feat/b"))))
                .mapPart(
                        GitHubState.PART,
                        gitHub -> gitHub.withPullRequests(Option.of(HashMap.of(
                                name("feat/a"), pr(96, "feat/a", "main"),
                                name("feat/b"), b,
                                name("feat/c"), pr(98, "feat/c", "feat/b")))));
    }

    private static final Model MODEL = model(pr(97, "feat/b", "main"));

    private static Model step(Model model, Msg msg) {
        return Update.update(model, msg).model();
    }

    private static Model menu(Model model, String branch) {
        return step(model, new StackMsg.MenuRequested(Option.of(name(branch)), Option.none()));
    }

    @Test
    void sOnABranchInNoStackOffersToMakeOne() {
        StackPopups.Menu menu =
                menu(MODEL, "feat/b").popup(StackPopups.Menu.class).get();

        assertEquals(true, menu.canMake());
    }

    @Test
    void aRootIsNeverStackedAndHasNoMenuYet() {
        assertEquals(Option.none(), menu(MODEL, "main").popup());
    }

    // feat/a and feat/b make stack #12 on GitHub, feat/b its top; feat/c is built on it, on its own.
    private static final PullRequestStack STACK = new PullRequestStack(
            12,
            2,
            2,
            List.of(
                    new PullRequestStack.Entry(
                            1, new PrNumber(96), State.OPEN, new GhBranch("feat/a"), new GhBranch("main")),
                    new PullRequestStack.Entry(
                            2, new PrNumber(97), State.OPEN, new GhBranch("feat/b"), new GhBranch("feat/a"))));

    private static final Model STACKED = model(new PullRequest(
                    new PrNumber(97),
                    new GhBranch("feat/b"),
                    Option.of(new GhBranch("feat/a")),
                    false,
                    Checks.NONE,
                    Review.NONE,
                    Option.of(STACK),
                    Option.none()))
            .mapPart(
                    GitHubState.PART,
                    gitHub -> gitHub.withPullRequests(gitHub.pullRequests()
                            .map(open -> open.put(
                                    name("feat/a"),
                                    new PullRequest(
                                            new PrNumber(96),
                                            new GhBranch("feat/a"),
                                            Option.of(new GhBranch("main")),
                                            false,
                                            Checks.NONE,
                                            Review.NONE,
                                            Option.of(new PullRequestStack(12, 1, 2, STACK.entries())),
                                            Option.none())))));

    @Test
    void aBranchInAStackIsNotMadeOneAgainButAddedToOrUnstacked() {
        StackPopups.Menu menu =
                menu(STACKED, "feat/b").popup(StackPopups.Menu.class).get();

        assertEquals(false, menu.canMake());
        assertEquals(false, menu.canStackOn());
        assertEquals(true, menu.canAdd());
        assertEquals(true, menu.canUnstack());
        assertEquals(Option.of(12), menu.stack().map(PullRequestStack::number));
        assertEquals(Option.of(name("feat/b")), menu.top().map(Branch::name));
    }

    @Test
    void sOnTheHeaderOfAStackOpensItsMenu() {
        Model opened = step(STACKED, new StackMsg.MenuRequested(Option.none(), Option.of(12)));

        assertEquals(
                Option.of(12),
                opened.popup(StackPopups.Menu.class)
                        .flatMap(StackPopups.Menu::stack)
                        .map(PullRequestStack::number));
    }

    @Test
    void addingPutsTheBranchesChosenAboveTheTopOnItWithAdd() {
        Model picking = step(menu(STACKED, "feat/b"), new StackMsg.AddChosen());
        StackPopups.Pick pick = picking.popup(StackPopups.Pick.class).get();
        assertEquals(Option.of(12), pick.addTo());
        assertEquals(
                List.of(name("feat/b"), name("feat/c")),
                pick.choice().rows().map(row -> row.branch().name()));

        Update.Next confirmed = Update.update(step(picking, new StackMsg.TopChosen(1)), new StackMsg.PickConfirmed());

        assertEquals(
                List.of(new StackCmd.MakeStack(List.empty(), List.of(new PrNumber(98)), Option.of(12))),
                confirmed.cmds());
        assertEquals(
                Option.of("branches.dialog.addToStack.bottomFixed"),
                step(picking, new StackMsg.BottomChosen(1))
                        .popup(StackPopups.Pick.class)
                        .flatMap(StackPopups.Pick::refused));
    }

    // ADR 148: feat/x was made on feat/b with plain git: it has no parent, and no pull request.
    private static final Branch X = Fixtures.branch(name("feat/x"), false, new Sha("bbbb"));

    private static Model withX(Model model) {
        return TestModels.localBranchesRead(
                        model, List.of(MAIN, branch("feat/a"), branch("feat/b"), branch("feat/c"), X))
                .model()
                .mapRepo(repo -> repo.withParents(model.repo().parents()));
    }

    @Test
    void addingFirstReadsWhichBranchesWithoutAParentAreBuiltOnTheTop() {
        Update.Next adding = Update.update(menu(withX(STACKED), "feat/b"), new StackMsg.AddChosen());

        assertTrue(
                adding.cmds()
                        .contains(new ReadBuiltOn(
                                12,
                                name("feat/b"),
                                new Sha("aaaa"),
                                HashMap.of(name("feat/x"), new Sha("bbbb")),
                                HashMap.of(name("feat/c"), new Sha("aaaa")))),
                adding.cmds().toString());
        assertEquals(Option.none(), adding.model().popup());
    }

    @Test
    void aBranchBuiltOnTheTopWithoutAPullRequestIsAskedToGetOneThere() {
        Model picking = step(
                step(menu(withX(STACKED), "feat/b"), new StackMsg.AddChosen()),
                new StackMsg.BuiltOnRead(
                        12,
                        name("feat/b"),
                        HashSet.of(name("feat/x")),
                        HashMap.of(
                                name("feat/b"), List.of(name("feat/b"), name("feat/c"), name("feat/x")),
                                name("feat/x"), List.of(name("feat/x")))));
        assertEquals(
                List.of(name("feat/b"), name("feat/c"), name("feat/x")),
                picking.popup(StackPopups.Pick.class)
                        .get()
                        .choice()
                        .rows()
                        .map(row -> row.branch().name()));

        Model asking = step(step(picking, new StackMsg.TopChosen(2)), new StackMsg.PickConfirmed());

        assertEquals(
                Option.of(new StackPopups.CreateFirst(name("feat/x"), name("feat/b"), new PrNumber(97), Option.of(12))),
                asking.popup());
    }

    @Test
    void aBranchWithoutAPullRequestIsStackedOnAnotherByCreatingOne() {
        Model choosing = step(menu(withX(STACKED), "feat/x"), new StackMsg.StackOnChosen());
        StackPopups.StackOn popup = choosing.popup(StackPopups.StackOn.class).get();
        assertEquals(Option.none(), popup.pullRequest());
        StackPopups.Target onB = popup.targets()
                .find(t -> t.branch().name().equals(name("feat/b")))
                .get();

        assertEquals(
                Option.of(new StackPopups.CreateFirst(name("feat/x"), name("feat/b"), new PrNumber(97), Option.of(12))),
                step(choosing, new StackMsg.TargetChosen(onB)).popup());
    }

    @Test
    void stackingOnABranchInNoStackMakesAStackOfTheTwoAfterChangingTheBase() {
        Model choosing = step(menu(MODEL, "feat/c"), new StackMsg.StackOnChosen());
        StackPopups.StackOn popup = choosing.popup(StackPopups.StackOn.class).get();
        StackPopups.Target onA = popup.targets()
                .find(t -> t.branch().name().equals(name("feat/a")))
                .get();

        Update.Next chosen = Update.update(choosing, new StackMsg.TargetChosen(onA));

        assertEquals(
                Option.of(new StackPopups.Confirm(
                        new StackCmd.MakeStack(
                                List.of(new BaseChange(
                                        new PrNumber(98), Option.of(new GhBranch("feat/b")), new GhBranch("feat/a"))),
                                List.of(new PrNumber(96), new PrNumber(98)),
                                Option.none()),
                        List.empty())),
                chosen.model().popup());
    }

    @Test
    void stackingOnTheTopOfAStackAddsToIt() {
        Model choosing = step(menu(STACKED, "feat/c"), new StackMsg.StackOnChosen());
        StackPopups.StackOn popup = choosing.popup(StackPopups.StackOn.class).get();
        assertEquals(
                List.of(name("feat/b")), popup.targets().map(t -> t.branch().name()));

        Update.Next chosen = Update.update(
                choosing, new StackMsg.TargetChosen(popup.targets().head()));

        assertEquals(
                List.of(new StackCmd.MakeStack(List.empty(), List.of(new PrNumber(98)), Option.of(12))), chosen.cmds());
    }

    @Test
    void unstackingAsksSayingWhatStaysThenTakesTheStackApart() {
        Model asking = step(menu(STACKED, "feat/b"), new StackMsg.UnstackChosen());
        StackPopups.Unstack popup = asking.popup(StackPopups.Unstack.class).get();

        assertEquals("2 pull requests stay open, unlinked.", StackDialogs.unstacked(popup));
        assertEquals(
                List.of("gh", "api", "--method", "POST", "repos/{owner}/{repo}/stacks/12/unstack"),
                ((StackMsg.Changed) popup.confirm().get().run(new Env(StubGit.of(), StubGitHub.of())))
                        .commands()
                        .head()
                        .commandLine());
    }

    // feat/c is built on feat/b: stacking feat/b on it would make #97 the base of its own base.
    @Test
    void aBranchIsNotStackedOnOneBuiltOnIt() {
        Model choosing = step(menu(MODEL, "feat/b"), new StackMsg.StackOnChosen());

        assertEquals(
                List.of(name("feat/a")),
                choosing.popup(StackPopups.StackOn.class)
                        .get()
                        .targets()
                        .map(t -> t.branch().name()));
    }

    // #95 is merged and #96 in a merge queue: both stay in the stack; only #97 leaves it.
    private static final PullRequestStack STAYING = new PullRequestStack(
            12,
            3,
            3,
            List.of(
                    new PullRequestStack.Entry(
                            1, new PrNumber(95), State.MERGED, new GhBranch("feat/z"), new GhBranch("main")),
                    new PullRequestStack.Entry(
                            2,
                            new PrNumber(96),
                            State.OPEN,
                            new GhBranch("feat/a"),
                            new GhBranch("feat/z"),
                            Option.none(),
                            true),
                    new PullRequestStack.Entry(
                            3, new PrNumber(97), State.OPEN, new GhBranch("feat/b"), new GhBranch("feat/a"))));

    @Test
    void unstackingSaysMergedAndQueuedPullRequestsStayInTheStack() {
        assertEquals(
                "1 pull request stays open, unlinked.\n"
                        + "2 merged or queued pull requests stay in the stack on GitHub.",
                StackDialogs.unstacked(new StackPopups.Unstack(STAYING)));
    }

    @Test
    void anUnstackedStackIsShownThoughAQueuedPullRequestStaysInIt() {
        StackChange gone = new StackChange.Unstacked(12, MODEL.now());
        PullRequest queued = new PullRequest(
                new PrNumber(96),
                new GhBranch("feat/a"),
                Option.of(new GhBranch("feat/z")),
                false,
                Checks.NONE,
                Review.NONE,
                Option.of(new PullRequestStack(12, 2, 3, STAYING.entries())),
                Option.none());

        assertEquals(
                true, gone.shownIn(HashMap.of(name("feat/a"), queued, name("feat/b"), pr(97, "feat/b", "feat/a"))));
    }

    @Test
    void makingOneOpensOnTheBranchWithTheBottomAsFarDownAsThereArePullRequests() {
        Model picking = step(menu(MODEL, "feat/b"), new StackMsg.MakeChosen());
        StackChoice choice = picking.popup(StackPopups.Pick.class).get().choice();

        assertEquals(
                List.of(name("feat/a"), name("feat/b"), name("feat/c")),
                choice.rows().map(row -> row.branch().name()));
        assertEquals(Option.of(name("main")), choice.base());
        assertEquals(0, choice.bottom());
        assertEquals(1, choice.top());
        assertEquals(ListCursor.START.at(1), picking.listCursor());
        assertEquals(Option.none(), picking.popup(StackPopups.Menu.class));
    }

    @Test
    void aTopBelowTheBranchIsRefusedWithWhy() {
        Model picking = step(menu(MODEL, "feat/b"), new StackMsg.MakeChosen());

        StackPopups.Pick pick = step(picking, new StackMsg.TopChosen(0))
                .popup(StackPopups.Pick.class)
                .get();

        assertEquals(1, pick.choice().top());
        assertEquals(Option.of("branches.dialog.makeStack.topBelow"), pick.refused());
    }

    @Test
    void basesThatChangeAreAskedAboutBeforeTheStackIsMade() {
        Model picking = step(step(menu(MODEL, "feat/b"), new StackMsg.MakeChosen()), new StackMsg.TopChosen(2));

        Update.Next confirmed = Update.update(picking, new StackMsg.PickConfirmed());

        StackCmd.MakeStack cmd = new StackCmd.MakeStack(
                List.of(new BaseChange(new PrNumber(97), Option.of(new GhBranch("main")), new GhBranch("feat/a"))),
                List.of(new PrNumber(96), new PrNumber(97), new PrNumber(98)),
                Option.none());
        assertEquals(
                Option.of(new StackPopups.Confirm(cmd, List.empty())),
                confirmed.model().popup());
        assertEquals(List.empty(), confirmed.cmds());
        assertEquals(
                "These bases change on GitHub first:\n#97: main → feat/a",
                StackDialogs.changes(new StackPopups.Confirm(cmd, List.empty())));
    }

    @Test
    void aStackWhoseBasesMakeOneLineIsMadeAtOnce() {
        Model picking = step(menu(model(pr(97, "feat/b", "feat/a")), "feat/b"), new StackMsg.MakeChosen());

        Update.Next confirmed = Update.update(picking, new StackMsg.PickConfirmed());

        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                List.of(new StackCmd.MakeStack(
                        List.empty(), List.of(new PrNumber(96), new PrNumber(97)), Option.none())),
                confirmed.cmds());
    }

    @Test
    void whatMakesNoStackIsNotMade() {
        Model picking = step(step(menu(MODEL, "feat/b"), new StackMsg.MakeChosen()), new StackMsg.BottomChosen(1));

        Update.Next confirmed = Update.update(picking, new StackMsg.PickConfirmed());

        assertEquals(List.empty(), confirmed.cmds());
        assertInstanceOf(StackPopups.Pick.class, confirmed.model().popup().get());
    }

    @Test
    void theBasesChangeFromTheBottomUpThenTheStackIsMade() {
        StackCmd.MakeStack cmd = new StackCmd.MakeStack(
                List.of(
                        new BaseChange(new PrNumber(97), Option.of(new GhBranch("main")), new GhBranch("feat/a")),
                        new BaseChange(new PrNumber(98), Option.of(new GhBranch("main")), new GhBranch("feat/b"))),
                List.of(new PrNumber(96), new PrNumber(97), new PrNumber(98)),
                Option.none());

        Msg done = cmd.run(new Env(StubGit.of(), StubGitHub.of()));

        assertEquals(
                List.of(
                        List.of("gh", "pr", "edit", "97", "--base", "feat/a"),
                        List.of("gh", "pr", "edit", "98", "--base", "feat/b"),
                        List.of(
                                "gh",
                                "api",
                                "--method",
                                "POST",
                                "repos/{owner}/{repo}/stacks",
                                "-F",
                                "pull_requests[]=96",
                                "-F",
                                "pull_requests[]=97",
                                "-F",
                                "pull_requests[]=98")),
                ((StackMsg.Changed) done).commands().map(ExecutedCommand::commandLine));
        assertEquals(true, cmd.changesForge());
    }

    @Test
    void aStackGitHubRefusesShowsTheBasesChangedBeforeInTheLog() {
        StackCmd.MakeStack cmd = new StackCmd.MakeStack(
                List.of(new BaseChange(new PrNumber(97), Option.of(new GhBranch("main")), new GhBranch("feat/a"))),
                List.of(new PrNumber(96), new PrNumber(97)),
                Option.none());
        GitHub refusing = StubGitHub.of().withStacks(new StubStacks() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> create(List<PrNumber> pullRequests) {
                return Either.left(
                        new GhFailure(new GhError.CommandFailed(Option.of("HTTP 404: Not Found"), 1), List.empty()));
            }
        });

        Msg done = cmd.run(new Env(StubGit.of(), refusing));

        StackMsg.ChangeFailed failed = (StackMsg.ChangeFailed) done;
        assertEquals(
                List.of(List.of("gh", "pr", "edit", "97", "--base", "feat/a")),
                failed.failure().commands().map(ExecutedCommand::commandLine));

        // The base it changed shows: the pull requests are read again, and the error says why it failed.
        Update.Next shown = Update.update(TestModels.running(MODEL, cmd), done);
        assertTrue(
                shown.cmds().exists(LoadRepositoryPullRequests.class::isInstance),
                shown.cmds().toString());
        assertTrue(
                ErrorTexts.errorText(shown.model()).exists(text -> text.contains("HTTP 404: Not Found")),
                ErrorTexts.errorText(shown.model()).toString());
    }

    @Test
    void aChangeMadeIsWaitedForUntilGitHubListsIt() {
        StackCmd.MakeStack cmd =
                new StackCmd.MakeStack(List.empty(), List.of(new PrNumber(96), new PrNumber(97)), Option.none());

        Model changed = step(TestModels.running(MODEL, cmd), new StackMsg.Changed(cmd, List.empty()));
        assertEquals(
                Option.of(new StackChange.Stacked(
                        List.of(new PrNumber(96), new PrNumber(97)),
                        Option.none(),
                        MODEL.now().plus(StackUpdate.SETTLING))),
                changed.part(GitHubState.PART).stackChange());

        // GitHub still lists them as they were: they are read again.
        Update.Next waiting = Update.update(changed, new StackMsg.Tick());
        assertTrue(
                waiting.cmds().exists(LoadRepositoryPullRequests.class::isInstance),
                waiting.cmds().toString());
        assertEquals(
                changed.part(GitHubState.PART).stackChange(),
                waiting.model().part(GitHubState.PART).stackChange());

        // Once it lists them as one stack, nothing more is read for it.
        Model listed = STACKED.mapPart(
                GitHubState.PART,
                gitHub -> gitHub.withStackChange(changed.part(GitHubState.PART).stackChange()));
        Update.Next seen = Update.update(listed, new StackMsg.Tick());
        assertEquals(List.empty(), seen.cmds());
        assertEquals(Option.none(), seen.model().part(GitHubState.PART).stackChange());
    }

    @Test
    void aChangeGitHubDoesNotListInTimeIsGivenUpOn() {
        Model late = MODEL.mapPart(
                GitHubState.PART,
                gitHub -> gitHub.withStackChange(Option.of(new StackChange.Stacked(
                        List.of(new PrNumber(96), new PrNumber(97)),
                        Option.none(),
                        MODEL.now().minusSeconds(1)))));

        Update.Next given = Update.update(late, new StackMsg.Tick());

        assertEquals(List.empty(), given.cmds());
        assertEquals(Option.none(), given.model().part(GitHubState.PART).stackChange());
        assertEquals(Option.none(), given.model().outcome().notice());
    }

    @Test
    void aStackToBeRebasedThatGitHubDoesNotListInTimeSaysItWasNotRebased() {
        Model late = MODEL.mapPart(
                GitHubState.PART,
                gitHub -> gitHub.withStackChange(Option.of(new StackChange.Stacked(
                        List.of(new PrNumber(96), new PrNumber(97)),
                        Option.none(),
                        MODEL.now().minusSeconds(1),
                        true))));

        Update.Next given = Update.update(late, new StackMsg.Tick());

        assertEquals(List.empty(), given.cmds());
        assertEquals(Option.none(), given.model().part(GitHubState.PART).stackChange());
        Notice notice = given.model().outcome().notice().get();
        assertEquals(new Notice.StackNotListed(new PrNumber(96)), notice);
        assertEquals(
                "GitHub did not list the stack of #96 in time, so it was not rebased: rebase it from the stack menu"
                        + " (s) once it shows.",
                NoticeText.of(notice));
    }

    @Test
    void anUnstackedStackIsShownOnceNoPullRequestIsInItAnyMore() {
        StackChange gone = new StackChange.Unstacked(12, MODEL.now());

        assertEquals(
                false,
                gone.shownIn(STACKED.part(GitHubState.PART).pullRequests().get()));
        assertEquals(
                true, gone.shownIn(MODEL.part(GitHubState.PART).pullRequests().get()));
    }

    @Test
    void aBranchBehindTheOneBelowIsMarkedAndAskedAbout() {
        // feat/c lacks the last 2 commits of feat/b, as on its remote.
        Model lagging = MODEL.mapRepo(repo -> repo.withBaseDistance(HashMap.of(
                name("feat/c"),
                new BaseDistance(
                        new BranchRef.Remote(new RemoteBranch("origin", name("feat/b"))), new Sha("bbbb"), 1, 2))));
        // feat/b's pull request is on feat/a already, so no base changes: only the lag is asked about.
        Model onA = lagging.mapPart(
                GitHubState.PART,
                gitHub -> gitHub.withPullRequests(
                        gitHub.pullRequests().map(open -> open.put(name("feat/b"), pr(97, "feat/b", "feat/a")))));
        Model picking = step(step(menu(onA, "feat/b"), new StackMsg.MakeChosen()), new StackMsg.TopChosen(2));

        Update.Next confirmed = Update.update(picking, new StackMsg.PickConfirmed());

        StackPopups.Confirm confirm =
                confirmed.model().popup(StackPopups.Confirm.class).get();
        assertEquals(List.of(new StackPopups.Diverged(name("feat/c"), name("feat/b"), 2)), confirm.diverged());
        assertEquals(
                "GitHub does not merge the stack until these are rebased onto the branch below:\n"
                        + "feat/c is 2 commits behind feat/b",
                StackDialogs.changes(confirm));
    }

    @Test
    void linkingAndRebasingIsOfferedOnlyForBranchesNotOnTheOneBelow() {
        StackCmd.MakeStack cmd =
                new StackCmd.MakeStack(List.empty(), List.of(new PrNumber(96), new PrNumber(97)), Option.none());
        Model diverged = MODEL.openPopup(
                new StackPopups.Confirm(cmd, List.of(new StackPopups.Diverged(name("feat/b"), name("feat/a"), 1))));

        Update.Next linked = Update.update(diverged, new StackMsg.LinkAndRebaseChosen());

        assertEquals(List.of(cmd.rebasing()), linked.cmds());
        assertEquals(
                List.empty(),
                Update.update(
                                MODEL.openPopup(new StackPopups.Confirm(cmd, List.empty())),
                                new StackMsg.LinkAndRebaseChosen())
                        .cmds());
    }

    @Test
    void aStackLinkedToBeRebasedIsRebasedOnceGitHubListsIt() {
        StackCmd.MakeStack cmd = new StackCmd.MakeStack(
                        List.empty(), List.of(new PrNumber(96), new PrNumber(97)), Option.none())
                .rebasing();
        Model changed = step(TestModels.running(MODEL, cmd), new StackMsg.Changed(cmd, List.empty()));
        // The base is rebased onto as fetched from origin.
        Model fetched = TestModels.remoteBranchesRead(
                        STACKED,
                        List.of(new RemoteRef(
                                new RemoteBranch("origin", name("main")), new Sha("aaaa"), Instant.EPOCH)))
                .model();
        Model listed = fetched.mapPart(
                GitHubState.PART,
                gitHub -> gitHub.withStackChange(changed.part(GitHubState.PART).stackChange()));

        Update.Next settled = Update.update(listed, new StackMsg.Tick());

        assertEquals(Option.none(), settled.model().part(GitHubState.PART).stackChange());
        assertTrue(
                settled.cmds().exists(StackRebaseCmd.ReadStack.class::isInstance),
                settled.cmds().toString());
    }
}

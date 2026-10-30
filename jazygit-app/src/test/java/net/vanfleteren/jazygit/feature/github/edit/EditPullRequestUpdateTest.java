package net.vanfleteren.jazygit.feature.github.edit;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.lastRead;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.github.edit.EditPullRequestCmd.EditBody;
import net.vanfleteren.jazygit.feature.github.edit.EditPullRequestCmd.SavePullRequest;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.LoadPullRequestDetails;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Branches;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestEdit;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Reading;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.LoadRepositoryPullRequests;
import org.junit.jupiter.api.Test;

/**
 * Editing the pull request that is shown: its title, body, base and whether it is a draft (ADR 59).
 */
class EditPullRequestUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", new BranchName("main"));
    // A local branch that has another name on its remote.
    private static final RemoteBranch ORIGIN_RELEASE = new RemoteBranch("origin", new BranchName("release-2"));
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);
    private static final PullRequestDetails DETAILS =
            StubGitHub.empty().pullRequestDetails(42).get().withBody("Why");
    private static final PullRequestPopup SHOWN = shown(DETAILS);
    private static final PullRequestFields OPENED = new PullRequestFields("A pull request", "Why", ORIGIN_MAIN, false);
    private static final EditPullRequestPopup EDITING =
            new EditPullRequestPopup(SHOWN, List.of(ORIGIN_MAIN, ORIGIN_RELEASE), OPENED, OPENED, true, true, false);
    private static final ExecutedCommand NVIM = ExecutedCommand.of("nvim", "PR_BODY.md");
    // GitHub, where making pull request 42 a draft fails.
    private static final StubGitHub NOT_READY = new StubGitHub() {
        @Override
        public Either<GhFailure, List<ExecutedCommand>> setDraft(int number, boolean draft) {
            return Either.left(new GhFailure(
                    new GhError.CommandFailed(Option.of("could not convert"), 1),
                    List.of(ExecutedCommand.of("gh", "pr", "ready", "42", "--undo"))));
        }
    };

    private static PullRequestPopup shown(PullRequestDetails details) {
        return PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0).with(Either.right(details), 0);
    }

    /**
     * The sample model with main, the default branch, release, as release-2 on origin, the branch of the pull
     * request, and a branch that is not on the remote.
     */
    private static Model withBranches() {
        return TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                tracking("feature", "feature", false),
                                tracking("release", "release-2", false),
                                Fixtures.branch(new BranchName("local"), false, new Sha("cccc")),
                                tracking("main", "main", true)))
                .model();
    }

    private static net.vanfleteren.jazygit.git.model.Branch tracking(String name, String remote, boolean isDefault) {
        return Fixtures.branch(
                new BranchName(name),
                false,
                new Sha("aaaa"),
                Instant.EPOCH,
                true,
                isDefault,
                Option.of(
                        new Upstream(new BranchRef.Remote(new RemoteBranch("origin", new BranchName(remote))), 0, 0)));
    }

    private static Model editing(PullRequestFields fields) {
        return withBranches().openPopup(EDITING.withFields(fields));
    }

    private static Env env(StubGitProvider git, StubGitHub gitHub, Terminal terminal) {
        return new Env(git, gitHub, SettingsStore.NONE, RecentRepositories.NONE, Repositories.NONE, terminal);
    }

    private static Env env(StubGitHub gitHub) {
        return env(new StubGitProvider() {}, gitHub, Terminal.NONE);
    }

    private static Next saving(PullRequestFields fields) {
        return Update.update(editing(fields), new EditPullRequestMsg.Confirmed());
    }

    @Test
    void editingStartsFromTheDetailsWithTheBaseFirstThenTheOtherBranchesOnTheRemote() {
        Next next = Update.update(withBranches().openPopup(SHOWN), new EditPullRequestMsg.Requested());

        assertEquals(Option.of(EDITING), next.model().popup());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theBaseIsListedFirstAlsoWhenItIsNotOnTheRemote() {
        PullRequestDetails gone = DETAILS.withBranches(new Branches("feature", "gone"));

        EditPullRequestPopup popup = Update.update(
                        withBranches().openPopup(shown(gone)), new EditPullRequestMsg.Requested())
                .model()
                .popup(EditPullRequestPopup.class)
                .get();

        RemoteBranch base = new RemoteBranch("origin", new BranchName("gone"));
        assertEquals(List.of(base, ORIGIN_MAIN, ORIGIN_RELEASE), popup.bases());
        assertEquals(base, popup.fields().base());
    }

    @Test
    void noEditBeforeTheDetailsAreRead() {
        Model loading = loaded().openPopup(PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0));
        Model failed = loaded().openPopup(PullRequestPopup.loading(FEATURE, FEATURE_PR, Instant.EPOCH, 0)
                .with(Either.left(refused("offline")), 0));

        assertEquals(
                loading,
                Update.update(loading, new EditPullRequestMsg.Requested()).model());
        assertEquals(
                failed,
                Update.update(failed, new EditPullRequestMsg.Requested()).model());
    }

    @Test
    void theBaseOfOneStackedOnAnotherCannotBeChanged() {
        PullRequestStack second = new PullRequestStack(3, 2, 2, List.empty());
        PullRequestStack first = new PullRequestStack(3, 1, 2, List.empty());

        EditPullRequestPopup above = opened(DETAILS.withStack(Option.of(second)));
        EditPullRequestPopup bottom = opened(DETAILS.withStack(Option.of(first)));

        assertFalse(above.baseChangeable());
        assertTrue(above.draftChangeable());
        assertTrue(bottom.baseChangeable());
    }

    @Test
    void neitherTheBaseNorTheDraftOfAClosedOrMergedOneCanBeChanged() {
        for (State state : List.of(State.CLOSED, State.MERGED)) {
            EditPullRequestPopup popup =
                    opened(DETAILS.withStatus(DETAILS.status().withState(state)));

            assertFalse(popup.baseChangeable(), state.name());
            assertFalse(popup.draftChangeable(), state.name());
        }
    }

    private static EditPullRequestPopup opened(PullRequestDetails details) {
        return Update.update(withBranches().openPopup(shown(details)), new EditPullRequestMsg.Requested())
                .model()
                .popup(EditPullRequestPopup.class)
                .get();
    }

    @Test
    void whatIsTypedPickedAndTickedIsKeptInThePopup() {
        Model typed = Update.update(editing(OPENED), new EditPullRequestMsg.Edited("Better title", "Because", true))
                .model();
        Model moved = Update.update(
                        typed, new EditPullRequestMsg.BaseMoved(ListCursor.START.at(1), Option.of(ORIGIN_RELEASE)))
                .model();

        assertEquals(
                Option.of(EDITING.withFields(new PullRequestFields("Better title", "Because", ORIGIN_RELEASE, true))),
                moved.popup());
        assertEquals(ListCursor.START.at(1), moved.listCursor());
    }

    @Test
    void aFilterThatShowsNoBaseKeepsTheBase() {
        ListCursor filtered = new ListCursor(Option.of(0), Option.of("zzz"));

        Model moved = Update.update(editing(OPENED), new EditPullRequestMsg.BaseMoved(filtered, Option.none()))
                .model();

        assertEquals(Option.of(EDITING), moved.popup());
        assertEquals(filtered, moved.listCursor());
    }

    @Test
    void aBaseOrDraftThatCannotBeChangedStaysAsItIs() {
        Model fixed = withBranches().openPopup(EDITING.withBaseChangeable(false).withDraftChangeable(false));

        Model moved = Update.update(
                        fixed, new EditPullRequestMsg.BaseMoved(ListCursor.START.at(1), Option.of(ORIGIN_RELEASE)))
                .model();
        Model ticked = Update.update(fixed, new EditPullRequestMsg.Edited("A pull request", "Why", true))
                .model();

        assertEquals(fixed, moved);
        assertEquals(fixed.popup(), ticked.popup());
    }

    @Test
    void savingOnlyChangesWhatWasChangedAndKeepsThePopupOpenMeanwhile() {
        Next next = saving(OPENED.withBody("Because"));

        SavePullRequest cmd = new SavePullRequest(
                FEATURE, 42, new PullRequestEdit(Option.none(), Option.of("Because"), Option.none()), Option.none());
        assertEquals(List.of(cmd), next.cmds());
        assertEquals(
                Option.of(EDITING.withFields(OPENED.withBody("Because")).withSaving(true)),
                next.model().popup());
        assertFalse(cmd.changesGitHub());
        // An edit does not touch the branch.
        assertEquals(Option.none(), next.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void aNewBaseIsSavedByItsNameOnTheRemoteAndADraftApart() {
        Next next = saving(OPENED.withBase(ORIGIN_RELEASE).withDraft(true));

        SavePullRequest cmd = new SavePullRequest(
                FEATURE,
                42,
                new PullRequestEdit(Option.none(), Option.none(), Option.of(new GhBranch("release-2"))),
                Option.of(true));
        assertEquals(List.of(cmd), next.cmds());
        assertTrue(cmd.changesGitHub());
    }

    @Test
    void theTitleIsSavedWithoutTheWhitespaceAroundIt() {
        assertEquals(List.empty(), saving(OPENED.withTitle("  A pull request ")).cmds());
        assertEquals(
                List.of(new SavePullRequest(
                        FEATURE,
                        42,
                        new PullRequestEdit(Option.of("Better"), Option.none(), Option.none()),
                        Option.none())),
                saving(OPENED.withTitle(" Better\t")).cmds());
    }

    @Test
    void withNothingChangedSavingGoesBackToThePullRequest() {
        Next next = saving(OPENED);

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of(SHOWN), next.model().popup());
    }

    @Test
    void aBlankTitleIsNotSaved() {
        Model blank = editing(OPENED.withTitle(" \t"));

        Next next = Update.update(blank, new EditPullRequestMsg.Confirmed());

        assertEquals(List.empty(), next.cmds());
        assertEquals(blank, next.model());
    }

    @Test
    void whileItIsSavedNothingCanBeChangedSavedAgainOrLeft() {
        Model saving = saving(OPENED.withBody("Because")).model();

        assertEquals(
                saving,
                Update.update(saving, new EditPullRequestMsg.Edited("x", "y", true))
                        .model());
        assertEquals(
                saving,
                Update.update(saving, new EditPullRequestMsg.BaseMoved(ListCursor.START, Option.of(ORIGIN_RELEASE)))
                        .model());
        assertEquals(
                List.empty(),
                Update.update(saving, new EditPullRequestMsg.Confirmed()).cmds());
        assertEquals(
                saving,
                Update.update(saving, new EditPullRequestMsg.Cancelled()).model());
        assertEquals(
                List.empty(),
                Update.update(saving, new EditPullRequestMsg.EditorRequested()).cmds());
    }

    @Test
    void cancellingDropsWhatWasChangedAndGoesBackToThePullRequest() {
        Model cancelled = Update.update(editing(OPENED.withTitle("Half a title")), new EditPullRequestMsg.Cancelled())
                .model();

        assertEquals(Option.of(SHOWN), cancelled.popup());
        assertEquals(
                Option.of(EDITING),
                Update.update(cancelled, new EditPullRequestMsg.Requested())
                        .model()
                        .popup());
    }

    @Test
    void detailsReadWhileEditingShowInThePullRequestGoneBackToButNotInTheFields() {
        Model editing = editing(OPENED.withTitle("Mine"));
        PullRequestDetails changed = DETAILS.withTitle("Theirs").withBody("Elsewhere");

        Model read = Update.update(editing, new PullRequestMsg.DetailsLoaded(42, 0, changed))
                .model();

        EditPullRequestPopup popup = read.popup(EditPullRequestPopup.class).get();
        assertEquals(shown(changed), popup.from());
        assertEquals(OPENED.withTitle("Mine"), popup.fields());
        assertEquals(OPENED, popup.opened());
    }

    @Test
    void aSavedEditGoesBackToThePullRequestAndReadsItsDetailsAgain() {
        Next saving = saving(OPENED.withBody("Because"));
        SavePullRequest cmd = (SavePullRequest) saving.cmds().head();

        Next saved = Update.update(saving.model(), cmd.run(env(StubGitHub.empty())));

        assertEquals(
                Option.of(SHOWN.readingAgain(Reading.BY_ITSELF, saved.model().now(), lastRead(saved.model()))),
                saved.model().popup());
        assertTrue(
                saved.cmds().contains(new LoadPullRequestDetails(42, lastRead(saved.model()))),
                saved.cmds().toString());
        assertEquals(
                "gh pr edit 42 --body Because",
                saved.model().outcome().commandLog().last().commands().last().line());
    }

    @Test
    void aNewBaseOrDraftSavedReadsThePullRequestsAgain() {
        Next saving = saving(OPENED.withDraft(true));
        SavePullRequest cmd = (SavePullRequest) saving.cmds().head();

        Next saved = Update.update(saving.model(), cmd.run(env(StubGitHub.empty())));

        assertEquals(
                List.of("gh pr ready 42 --undo"),
                saved.model().outcome().commandLog().last().commands().map(ExecutedCommand::line));
        assertTrue(
                saved.cmds().exists(LoadRepositoryPullRequests.class::isInstance),
                saved.cmds().toString());
    }

    @Test
    void anEditSavedAfterThePopupClosedOnlyLogsIt() {
        SavePullRequest cmd = new SavePullRequest(
                FEATURE, 42, new PullRequestEdit(Option.of("New"), Option.none(), Option.none()), Option.none());

        Next saved = Update.update(loaded(), cmd.succeeded(List.empty()));

        assertEquals(Option.none(), saved.model().popup());
        assertFalse(
                saved.cmds().exists(LoadPullRequestDetails.class::isInstance),
                saved.cmds().toString());
    }

    @Test
    void anEditGitHubRefusesKeepsThePopupWithWhatWasTypedAndShowsWhy() {
        Next saving = saving(OPENED.withTitle("New"));
        SavePullRequest cmd = (SavePullRequest) saving.cmds().head();
        StubGitHub refusing = new StubGitHub() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> editPullRequest(int number, PullRequestEdit edit) {
                return Either.left(new GhFailure(
                        new GhError.CommandFailed(Option.of("could not edit: network down"), 1),
                        List.of(ExecutedCommand.of("gh", "pr", "edit", "42"))));
            }
        };

        Next failed = Update.update(saving.model(), cmd.run(env(refusing)));

        assertEquals(
                Option.of(EDITING.withFields(OPENED.withTitle("New"))),
                failed.model().popup());
        assertEquals(
                Option.of("Editing the pull request of feature failed: could not edit: network down"),
                errorText(failed.model()));
        // It can be saved again.
        assertEquals(
                List.of(cmd),
                Update.update(failed.model(), new EditPullRequestMsg.Confirmed())
                        .cmds());
    }

    @Test
    void whenOnlyTheDraftFailsTheRestCountsAsSavedAndTheNextSaveOnlyChangesTheDraft() {
        PullRequestFields typed =
                OPENED.withTitle("New").withBase(ORIGIN_RELEASE).withDraft(true);
        Next saving = saving(typed);
        SavePullRequest cmd = (SavePullRequest) saving.cmds().head();

        Next failed = Update.update(saving.model(), cmd.run(env(NOT_READY)));

        EditPullRequestPopup popup =
                failed.model().popup(EditPullRequestPopup.class).get();
        assertEquals(typed.withDraft(false), popup.opened());
        assertEquals(typed, popup.fields());
        assertFalse(popup.saving());
        // Both commands are logged, and the dialog under the popup reads what was saved.
        assertEquals(
                List.of("gh pr edit 42 --title New --base release-2", "gh pr ready 42 --undo"),
                failed.model().outcome().commandLog().last().commands().map(ExecutedCommand::line));
        assertTrue(
                failed.cmds().contains(new LoadPullRequestDetails(42, lastRead(failed.model()))),
                failed.cmds().toString());
        assertEquals(Reading.BY_ITSELF, popup.from().reading());
        // The new base shows on the branch.
        assertTrue(
                failed.cmds().exists(LoadRepositoryPullRequests.class::isInstance),
                failed.cmds().toString());
        assertEquals(
                List.of(new SavePullRequest(
                        FEATURE,
                        42,
                        new PullRequestEdit(Option.none(), Option.none(), Option.none()),
                        Option.of(true))),
                Update.update(failed.model(), new EditPullRequestMsg.Confirmed())
                        .cmds());
    }

    @Test
    void whenOnlyTheDraftFailsAfterTheTitleWasSavedThePullRequestsAreNotReadAgain() {
        Next saving = saving(OPENED.withTitle("New").withDraft(true));
        SavePullRequest cmd = (SavePullRequest) saving.cmds().head();

        Next failed = Update.update(saving.model(), cmd.run(env(NOT_READY)));

        assertTrue(
                failed.cmds().contains(new LoadPullRequestDetails(42, lastRead(failed.model()))),
                failed.cmds().toString());
        assertFalse(
                failed.cmds().exists(LoadRepositoryPullRequests.class::isInstance),
                failed.cmds().toString());
    }

    @Test
    void theBodyIsOpenedInTheEditorWithTheTerminalHandedOver() {
        assertEquals(
                List.of(new EditBody("Why")),
                Update.update(editing(OPENED), new EditPullRequestMsg.EditorRequested())
                        .cmds());

        java.util.List<String> events = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                events.add("leave");
                T result = program.get();
                events.add("come back");
                return result;
            }
        };
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(
                    String fileName, String text) {
                events.add("edit " + fileName + " " + text);
                return Either.right(Tuple.of("Because", List.of(NVIM)));
            }
        };

        assertEquals(
                new EditPullRequestMsg.EditorClosed("Because", List.of(NVIM)),
                new EditBody("Why").run(env(git, StubGitHub.empty(), terminal)));
        assertEquals(java.util.List.of("leave", "edit PR_BODY.md Why", "come back"), events);
    }

    @Test
    void theEditedBodyIsShownInThePopupToSave() {
        Next edited = Update.update(editing(OPENED), new EditPullRequestMsg.EditorClosed("Because", List.of(NVIM)));

        assertEquals(
                Option.of(EDITING.withFields(OPENED.withBody("Because"))),
                edited.model().popup());
        assertEquals(List.empty(), edited.cmds());
        assertEquals(
                EditPullRequestCmd.EDIT_BODY,
                edited.model().outcome().commandLog().last().operation());
    }

    @Test
    void anEditorThatFailsKeepsTheBodyAndShowsWhy() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(
                    String fileName, String text) {
                return Either.left(new GitFailure<>(new GitError.EditorFailed("nvim", 1), List.of()));
            }
        };

        Model failed = Update.update(
                        editing(OPENED), new EditBody("Why").run(env(git, StubGitHub.empty(), Terminal.NONE)))
                .model();

        assertEquals(Option.of(EDITING), failed.popup());
        AppError.OperationFailed error = assertInstanceOf(
                AppError.OperationFailed.class, failed.outcome().error().get());
        assertEquals(EditPullRequestCmd.EDIT_BODY, error.operation());
    }
}

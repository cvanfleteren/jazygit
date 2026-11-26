package net.vanfleteren.jazygit.features.github.checks;

import static net.vanfleteren.jazygit.features.github.checks.RerunPlanTest.check;
import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.checks.JobLog;
import net.vanfleteren.jazygit.core.gh.checks.StubChecks;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.changes.edit.EditCmd;
import net.vanfleteren.jazygit.features.changes.edit.EditFeature;
import net.vanfleteren.jazygit.features.changes.edit.EditMsg;
import net.vanfleteren.jazygit.features.changes.edit.EditPopup;
import net.vanfleteren.jazygit.features.changes.edit.OpenFileCmd;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.checks.branches.BranchChecksMsg;
import net.vanfleteren.jazygit.features.github.checks.branches.GitHubChecks;
import net.vanfleteren.jazygit.features.github.checks.timings.PendingChecks;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import org.junit.jupiter.api.Test;

/**
 * Picking a check in the checks popup and opening its log in an editor (ADR 160).
 */
class CheckLogUpdateTest {

    private static final RemoteBranch FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final BranchRef MINE = new BranchRef.Local(new BranchName("mine"));
    private static final Check LINT = check("lint", 1, "Build", Checks.FAILING, "FAILURE");
    private static final Check TEST = check("test", 1, "Build", Checks.PASSING, "SUCCESS");
    private static final Check VERIFY = check("verify", 1, "Build", Checks.PASSING, "SUCCESS");
    private static final Path FILE = Path.of("/cache/logs/owner/repo/1-1-42.log");
    private static final ExecutedCommand READ =
            ExecutedCommand.of("gh", "api", "repos/owner/repo/actions/jobs/42/logs");
    private static final EditorProgram NVIM =
            EditorProgram.ofFile("Neovim", List.of("nvim", "+{line}", "{file}"), true, Option.none());
    private static final EditorProgram CODE =
            EditorProgram.ofFile("Code", List.of("code", "-g", "{file}:{line}"), false, Option.of('c'));

    /**
     * The checks popup on {@code mine}, which follows {@code origin/feature}, showing {@code checks} as read, with
     * {@code editors} set to open files.
     */
    private static Model shown(List<Check> checks, EditorProgram... editors) {
        Branch mine = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(FEATURE), 0, 0)));
        Model model = Update.update(
                        TestModels.localBranchesRead(
                                        // The feature that opens files answers the log asked for (ADR 206).
                                        loaded().withReactions(new Reactions(EditFeature.FEATURE.reactions()))
                                                .mapPreferences(preferences -> preferences.withSettings(preferences
                                                        .settings()
                                                        .with(Settings.EDITORS, new Editors(List.of(editors))))),
                                        List.of(mine))
                                .model(),
                        new BranchChecksMsg.Loaded(new GitHubChecks(
                                HashSet.of(new RemoteName("origin")),
                                HashMap.of(new BranchName("feature"), Checks.FAILING))))
                .model();
        Model asked = Update.update(model, new ChecksMsg.Requested(MINE)).model();
        return Update.update(asked, new ChecksMsg.Loaded(FEATURE, read(checks))).model();
    }

    private static PendingChecks read(List<Check> checks) {
        return new PendingChecks(new RepoSlug("owner/repo"), HashMap.of(new BranchName("feature"), checks));
    }

    private static ChecksPopup checks(Model model) {
        return model.popup(ChecksPopup.class).get();
    }

    private static Option<String> picked(Model model) {
        return checks(model).pickedCheck(model).map(Check::name);
    }

    private static Model update(Model model, Msg... msgs) {
        return List.of(msgs).foldLeft(model, (at, msg) -> Update.update(at, msg).model());
    }

    @Test
    void theFirstCheckListedIsPickedAtFirstTheFailedOneFirst() {
        assertEquals(Option.of("lint"), picked(shown(List.of(TEST, LINT, VERIFY))));
    }

    @Test
    void nAndCapitalNPickTheNextAndThePreviousOneAsFarAsThereAre() {
        Model model = shown(List.of(TEST, LINT, VERIFY));

        assertEquals(Option.of("test"), picked(update(model, new ChecksMsg.Stepped(1))));
        assertEquals(
                Option.of(picked(update(model, new ChecksMsg.Stepped(9))).get()),
                picked(update(model, new ChecksMsg.Stepped(2))));
        assertEquals(Option.of("lint"), picked(update(model, new ChecksMsg.Stepped(-1))));
    }

    @Test
    void theChecksScrollAsLittleAsItTakesToShowTheOnePicked() {
        List<Check> many =
                List.range(0, 20).map(i -> check("check" + (char) ('a' + i), 1, "Build", Checks.PASSING, "SUCCESS"));
        Model model = shown(many);

        Model down = update(model, new ChecksMsg.Stepped(16));
        Model up = update(down.withListCursor(ListCursor.START.at(4)), new ChecksMsg.Stepped(-14));

        assertEquals(Option.of(1), down.listCursor().line());
        assertEquals(Option.of(2), up.listCursor().line());
        assertEquals(
                Option.of(0),
                update(model, new ChecksMsg.Stepped(3)).listCursor().line());
    }

    @Test
    void aClickPicksTheCheckClicked() {
        Model model = shown(List.of(TEST, LINT, VERIFY));

        assertEquals(Option.of("verify"), picked(update(model, new ChecksMsg.Clicked(CheckPick.of(VERIFY)))));
    }

    @Test
    void theCheckPickedStaysPickedWhileTheChecksAreReadAgainUntilItsJobIsGone() {
        Model model = update(shown(List.of(TEST, LINT, VERIFY)), new ChecksMsg.Clicked(CheckPick.of(VERIFY)));
        Check verifyAgain = check("verify", 2, "Build", Checks.PASSING, "SUCCESS");
        Check rerun = new Check(
                "verify",
                Checks.PASSING,
                verifyAgain
                        .run()
                        .map(run -> new CheckRun(
                                run.id() + 1,
                                run.workflow(),
                                run.conclusion(),
                                run.startedAt(),
                                run.completedAt(),
                                run.runId(),
                                run.runCreatedAt(),
                                2,
                                run.event())));

        Model same = update(model, new ChecksMsg.Loaded(FEATURE, read(List.of(VERIFY, LINT, TEST))));
        Model again = update(model, new ChecksMsg.Loaded(FEATURE, read(List.of(rerun, LINT, TEST))));

        assertEquals(Option.of("verify"), picked(same));
        assertEquals(Option.of("lint"), picked(again));
    }

    @Test
    void eOnACheckWithoutALogSaysWhyAndReadsNothing() {
        Check running = check("e2e", 2, "E2E", Checks.PENDING, "");
        Check skipped = check("deploy", 3, "Deploy", Checks.PASSING, "SKIPPED");
        Check status = new Check("ci/status", Checks.PASSING);

        List.of(running, skipped, status)
                .zip(List.of(NoLog.NOT_DONE, NoLog.SKIPPED, NoLog.NOT_ACTIONS))
                .forEach(expected -> {
                    Model model = update(shown(List.of(expected._1)), new ChecksMsg.LogRequested());
                    Next next = Update.update(shown(List.of(expected._1)), new ChecksMsg.LogRequested());
                    assertEquals(Option.of(expected._2), checks(model).noLog(model), expected._1.name());
                    assertEquals(List.empty(), next.cmds(), expected._1.name());
                });
    }

    @Test
    void whyThereIsNoLogGoesOnceAnotherCheckIsPicked() {
        Model model = update(
                shown(List.of(check("e2e", 2, "E2E", Checks.PENDING, ""), VERIFY)),
                new ChecksMsg.LogRequested(),
                new ChecksMsg.Stepped(1));

        assertEquals(Option.none(), checks(model).noLog(model));
    }

    @Test
    void eReadsTheLogOfTheJobPickedInTheBackground() {
        Next next = Update.update(shown(List.of(LINT, VERIFY)), new ChecksMsg.LogRequested());

        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());
        assertEquals(List.of(cmd), next.cmds());
        assertTrue(cmd.background());
        assertEquals(Option.of("lint"), cmd.subject());
    }

    @Test
    void theLogIsReadOfItsRunAttemptAndJob() {
        java.util.List<java.util.List<Object>> asked = new java.util.ArrayList<>();
        Env env = new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of().withChecks(new StubChecks() {
            @Override
            public Either<GhFailure, JobLog> jobLog(RepoSlug repository, long runId, int attempt, long jobId) {
                asked.add(java.util.List.of(repository, runId, attempt, jobId));
                return Either.right(new JobLog(FILE, 3, List.of(READ)));
            }
        }));
        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());

        assertEquals(new ChecksMsg.LogRead(cmd, FILE.toString(), 3, List.of(READ)), cmd.run(env));
        assertEquals(java.util.List.of(java.util.List.of("owner/repo", 1L, 1, (long) "lint".hashCode())), asked);
    }

    @Test
    void aLogGitHubDoesNotGiveIsAFailedOperation() {
        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());

        assertInstanceOf(
                OperationMsg.Failed.class, cmd.run(new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of())));
    }

    @Test
    void withoutAnEditorSetToOpenFilesTheLogOpensInGitsEditorAndTheReadIsLogged() {
        Model model = shown(List.of(LINT));
        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());

        Next next = Update.update(model, new ChecksMsg.LogRead(cmd, FILE.toString(), 3, List.of(READ)));

        assertEquals(List.of(new EditCmd(List.of(FILE.toString()))), next.cmds());
        assertEquals(List.of(READ), next.model().outcome().commandLog().last().commands());
        assertTrue(next.model().popup(ChecksPopup.class).isDefined());
    }

    @Test
    void theOneEditorSetOpensItAtTheFirstError() {
        Model model = shown(List.of(LINT), NVIM);
        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());

        Next next = Update.update(model, new ChecksMsg.LogRead(cmd, FILE.toString(), 3, List.empty()));

        assertEquals(List.of(new OpenFileCmd(NVIM, FILE.toString(), 3)), next.cmds());
        assertTrue(next.model().popup(ChecksPopup.class).isDefined());
    }

    @Test
    void ofMoreEditorsOneIsChosenOverTheChecksWhichEscapeGoesBackTo() {
        Model model = update(shown(List.of(LINT, VERIFY), NVIM, CODE), new ChecksMsg.Stepped(1))
                .withListCursor(ListCursor.START.at(0));
        CheckLogCmd cmd = new CheckLogCmd(
                new RepoSlug("owner/repo"), "verify", VERIFY.run().get());

        Model asking = update(model, new ChecksMsg.LogRead(cmd, FILE.toString(), 7, List.empty()));
        Next chosen = Update.update(asking, new EditMsg.Chosen(CODE));
        Model cancelled = update(asking, new EditMsg.Cancelled());

        EditPopup menu = asking.popup(EditPopup.class).get();
        assertEquals(Option.of(Phrase.of("github.dialog.logEditor.title", "verify")), menu.title());
        assertEquals(List.of(NVIM, CODE), menu.editors().map(lettered -> lettered._2));
        assertEquals(List.of(new OpenFileCmd(CODE, FILE.toString(), 7)), chosen.cmds());
        assertEquals(Option.of(checks(model)), chosen.model().popup(ChecksPopup.class));
        assertEquals(Option.of(checks(model)), cancelled.popup(ChecksPopup.class));
        assertEquals(Option.of("verify"), picked(cancelled));
    }

    @Test
    void theChecksUnderTheMenuKeepHowFarTheyWereShownToBeWhenWhatChecksTakeChanges() {
        Check e2e = check("e2e", 2, "E2E", Checks.PENDING, "");
        Model model = shown(List.of(e2e, LINT), NVIM, CODE);
        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());
        Model asking = update(model, new ChecksMsg.LogRead(cmd, FILE.toString(), 3, List.empty()));
        ChecksPopup kept = checks(model).checkTimesChanging(asking);

        Model back = update(asking.checkTimesChanged(asking), new EditMsg.Cancelled());

        // The checks are running, so being told changes them: the menu has to pass it on.
        assertNotEquals(checks(model), kept);
        assertEquals(Option.of(kept), back.popup(ChecksPopup.class));
    }

    @Test
    void whyThereIsNoLogStaysWithTheCheckItIsAboutAndGoesOnceItHasOne() {
        Check e2e = check("e2e", 2, "E2E", Checks.PENDING, "");
        Check e2eDone = check("e2e", 2, "E2E", Checks.PASSING, "SUCCESS");
        Model asked = update(shown(List.of(e2e, VERIFY)), new ChecksMsg.LogRequested());

        // lint fails meanwhile, and is listed first.
        Model failed = update(asked, new ChecksMsg.Loaded(FEATURE, read(List.of(e2e, VERIFY, LINT))));
        Model done = update(failed, new ChecksMsg.Loaded(FEATURE, read(List.of(e2eDone, VERIFY, LINT))));

        assertEquals(Option.of("e2e"), picked(failed));
        assertEquals(Option.of(NoLog.NOT_DONE), checks(failed).noLog(failed));
        assertEquals(Option.of("e2e"), picked(done));
        assertEquals(Option.none(), checks(done).noLog(done));
    }

    @Test
    void aRerunAnsweredWhileTheEditorsAreAskedForIsShownOnceBackToTheChecks() {
        Model shown = shown(List.of(LINT, VERIFY), NVIM, CODE);
        Next confirmed = Update.update(update(shown, new ChecksMsg.RerunRequested()), new ChecksMsg.RerunConfirmed());
        RerunCmd rerun = (RerunCmd) confirmed.cmds().head();
        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());

        Model back = update(
                confirmed.model(),
                new ChecksMsg.LogRead(cmd, FILE.toString(), 3, List.empty()),
                new ChecksMsg.Rerun(
                        rerun, rerun.runs().map(run -> new RerunCmd.Outcome(run, Option.none())), List.empty()),
                new EditMsg.Cancelled());

        assertTrue(checks(back).asked().get().outcomes().isDefined());
    }

    @Test
    void ofMoreEditorsTheFirstOpensTheLogWhenAnotherPopupIsOverTheChecks() {
        Model asking = update(shown(List.of(LINT, VERIFY), NVIM, CODE), new ChecksMsg.RerunRequested());
        CheckLogCmd cmd =
                new CheckLogCmd(new RepoSlug("owner/repo"), "lint", LINT.run().get());

        Next next = Update.update(asking, new ChecksMsg.LogRead(cmd, FILE.toString(), 3, List.empty()));

        assertEquals(List.of(new OpenFileCmd(NVIM, FILE.toString(), 3)), next.cmds());
        assertTrue(next.model().popup(RerunPopup.class).isDefined());
    }
}

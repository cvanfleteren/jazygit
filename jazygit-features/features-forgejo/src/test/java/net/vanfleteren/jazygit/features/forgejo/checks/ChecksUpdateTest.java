package net.vanfleteren.jazygit.features.forgejo.checks;

import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.FEATURE;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.NOW;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.PR_HEAD;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.TIP;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.job;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.read;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.readOf;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.changes.edit.EditFeature;
import net.vanfleteren.jazygit.features.changes.edit.EditMsg;
import net.vanfleteren.jazygit.features.changes.edit.EditPopup;
import net.vanfleteren.jazygit.features.forge.checks.CheckPick;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.checks.ForgeCheck;
import net.vanfleteren.jazygit.features.forge.checks.NoLog;
import net.vanfleteren.jazygit.features.forge.checks.RerunNote;
import net.vanfleteren.jazygit.features.forge.checks.RerunPlan;
import net.vanfleteren.jazygit.features.forge.checks.RerunPopup;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.forgejo.builds.BuildsUpdateTest;
import net.vanfleteren.jazygit.features.forgejo.builds.Tip;
import net.vanfleteren.jazygit.features.forgejo.show.ForgejoPullRequestPopup;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Showing the checks on Forgejo one by one, as GitHub's are (ADR 195).
 */
class ChecksUpdateTest {

    /**
     * The model of the builds tests, at NOW, with the tip of {@code origin/feature} read on Forgejo as {@code state}.
     */
    private static Model model(CommitStatus.State state) {
        Model model = BuildsUpdateTest.model().withNow(NOW);
        Tip tip = new Tip(FEATURE.remote(), TIP);
        return new BuildsMsg.Read(List.of(tip), HashMap.of(TIP, state), HashSet.empty())
                .apply(model)
                .model();
    }

    private static Model showing(List<ForgejoCheck> checks) {
        return model(CommitStatus.State.FAILED).openPopup(read(checks));
    }

    @Test
    void cOnALocalBranchReadsTheChecksOfTheTipOfTheBranchItFollows() {
        Next next = new ChecksMsg.Requested(ForgejoChecksPort.PORT, new BranchRef.Local(new BranchName("feature")))
                .apply(model(CommitStatus.State.RUNNING));

        assertTrue(next.model().popup(ChecksPopup.class).isDefined());
        assertEquals(
                List.of(new LoadChecks(FEATURE, Option.of(TIP))), next.cmds().filter(LoadChecks.class::isInstance));
    }

    @Test
    void cOnABranchWithoutBuildsDoesNothing() {
        Next next = new ChecksMsg.Requested(ForgejoChecksPort.PORT, new BranchRef.Remote(FEATURE))
                .apply(model(CommitStatus.State.NONE));

        assertTrue(next.model().popup(ChecksPopup.class).isEmpty());
        assertTrue(next.cmds().isEmpty());
    }

    @Test
    void whatIsReadIsShown() {
        Model model = model(CommitStatus.State.RUNNING).openPopup(ChecksFixtures.LOADING);
        List<ForgejoCheck> checks = List.of(job("b", CommitStatus.State.RUNNING, 12, 0, 30, Option.none()));

        Model shown = new ChecksMsg.Read(ForgejoChecksPort.PORT, FEATURE, readOf(checks))
                .apply(model)
                .model();

        assertEquals(
                checks,
                ForgejoChecksPort.checks(shown.popup(ChecksPopup.class).get().checksRead()));
    }

    @Test
    void aTickReadsThemAgainWhileOneRunsButNotWhenAllAreDone() {
        Model running = showing(List.of(job("b", CommitStatus.State.RUNNING, 12, 0, 30, Option.none())));
        Model done = showing(List.of(job("b", CommitStatus.State.PASSED, 12, 0, 30, Option.of(5))));

        assertEquals(
                1,
                new ChecksMsg.Tick(ForgejoChecksPort.PORT).apply(running).cmds().size());
        assertTrue(new ChecksMsg.Tick(ForgejoChecksPort.PORT).apply(done).cmds().isEmpty());
    }

    @Test
    void nPicksTheNextCheckListed() {
        Model model = showing(List.of(
                job("a", CommitStatus.State.PASSED, 12, 0, 30, Option.of(5)),
                job("b", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5))));

        Model stepped = new ChecksMsg.Stepped(1).apply(model).model();

        // The failed one is listed first, and picked first; n picks the passed one after it.
        assertEquals(
                Option.of("a"),
                stepped.popup(ChecksPopup.class)
                        .flatMap(popup -> popup.pickedCheck(stepped))
                        .map(ForgeCheck::name));
    }

    @Test
    void eOnAJobDoneReadsItsLog() {
        Model model = showing(List.of(job("b", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5))));

        Next next = new ChecksMsg.LogRequested().apply(model);

        assertEquals(
                List.of(new CheckLogCmd(FEATURE, "b", new ForgejoCheck.ActionsJob(12, 1))),
                next.cmds().filter(CheckLogCmd.class::isInstance));
    }

    @Test
    void eOnAStatusOfAnotherCiSaysItHasNoLog() {
        Model model = showing(List.of(status("woodpecker", CommitStatus.State.FAILED, 30)));

        Next next = new ChecksMsg.LogRequested().apply(model);

        assertTrue(next.cmds().isEmpty());
        assertEquals(
                Option.of(NoLog.NOT_ACTIONS),
                next.model().popup(ChecksPopup.class).flatMap(popup -> popup.noLog(next.model())));
    }

    @Test
    void rAsksToRerunTheFailedJobsOfARunThatIsDone() {
        Model model = showing(List.of(
                job("ci / lint (push)", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5)),
                job("ci / build (push)", CommitStatus.State.PASSED, 12, 0, 30, Option.of(5))));

        Model asking = new ChecksMsg.RerunRequested().apply(model).model();
        RerunPopup popup = asking.popup(RerunPopup.class).get();
        assertEquals(
                List.of(new RerunPlan.Run(
                        12,
                        "ci",
                        "push",
                        List.of(ForgejoChecksPort.of(
                                job("ci / lint (push)", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5)))))),
                popup.plan().runs());

        Next confirmed = new ChecksMsg.RerunConfirmed().apply(asking);
        assertEquals(1, confirmed.cmds().filter(RerunCmd.class::isInstance).size());
        assertTrue(confirmed.model().popup(ChecksPopup.class).get().rerunAwaited(NOW));
    }

    @Test
    void rWhileTheFailedRunStillRunsSaysSo() {
        Model model = showing(List.of(
                job("ci / lint (push)", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5)),
                job("ci / build (push)", CommitStatus.State.RUNNING, 12, 0, 30, Option.none())));

        Model told = new ChecksMsg.RerunRequested().apply(model).model();

        assertEquals(
                Option.of(new RerunNote.Nothing(RerunPlan.Why.RUNNING)),
                told.popup(ChecksPopup.class).flatMap(ChecksPopup::rerun));
    }

    @Test
    void aRerunRefusedIsAnErrorAndTheChecksSayHowItWent() {
        Model model = showing(List.of(job("ci / lint (push)", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5))));
        Model asked = new ChecksMsg.RerunConfirmed()
                .apply(new ChecksMsg.RerunRequested().apply(model).model())
                .model();
        RerunPlan.Run run = new RerunPlan.Run(
                12,
                "ci",
                "push",
                List.of(ForgejoChecksPort.of(
                        job("ci / lint (push)", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5)))));
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(run));

        Model answered = new ChecksMsg.Rerun(
                        ForgejoChecksPort.PORT,
                        FEATURE,
                        cmd,
                        List.of(new RerunPlan.Outcome(
                                run, Option.of(new ForgejoFailure.Asked(new ForgejoError.Refused(403, "no"))))),
                        List.empty())
                .apply(asked)
                .model();

        assertTrue(answered.outcome().error().isDefined());
        assertFalse(answered.popup(ChecksPopup.class).get().rerunAwaited(NOW));
    }

    @Test
    void fromThePullRequestDialogTheChecksAreOfTheCommitItMerges() {
        ForgejoPullRequestPopup dialog = ChecksFixtures.pullRequest();

        Next next = new ChecksMsg.PullRequestChecksRequested(ForgejoChecksPort.PORT)
                .apply(model(CommitStatus.State.RUNNING).openPopup(dialog));

        assertEquals(Option.of(dialog), next.model().popup(ChecksPopup.class).flatMap(ChecksPopup::from));
        assertEquals(
                List.of(new LoadChecks(FEATURE, Option.of(PR_HEAD))),
                next.cmds().filter(LoadChecks.class::isInstance));
    }

    @Test
    void fromThePullRequestDialogTheyAreReadAgainOnTheCommitItMergesThoughTheRemoteBranchIsBehind() {
        // origin/feature is still at TIP: what was pushed to the pull request since was not fetched.
        Model opened = new ChecksMsg.PullRequestChecksRequested(ForgejoChecksPort.PORT)
                .apply(model(CommitStatus.State.RUNNING).openPopup(ChecksFixtures.pullRequest()))
                .model();
        Model read = new ChecksMsg.Read(
                        ForgejoChecksPort.PORT,
                        FEATURE,
                        readOf(PR_HEAD, List.of(job("b", CommitStatus.State.RUNNING, 12, 0, 30, Option.none()))))
                .apply(opened)
                .model();

        assertEquals(
                List.of(new LoadChecks(FEATURE, Option.of(PR_HEAD))),
                new ChecksMsg.Tick(ForgejoChecksPort.PORT).apply(read).cmds().filter(LoadChecks.class::isInstance));
        assertEquals(
                List.of(new LoadChecks(FEATURE, Option.of(PR_HEAD))),
                new ChecksMsg.RefreshChosen().apply(read).cmds().filter(LoadChecks.class::isInstance));
    }

    @Test
    void escapeGoesBackToThePullRequestDialogTheyWereOpenedFrom() {
        ForgejoPullRequestPopup dialog = ChecksFixtures.pullRequest();
        Model opened = new ChecksMsg.PullRequestChecksRequested(ForgejoChecksPort.PORT)
                .apply(model(CommitStatus.State.RUNNING).openPopup(dialog))
                .model();

        assertEquals(
                Option.of(dialog), new ChecksMsg.Closed().apply(opened).model().popup(ForgejoPullRequestPopup.class));
    }

    @Test
    void aClickPicksTheCheckByItsName() {
        Model model = showing(List.of(
                job("a", CommitStatus.State.PASSED, 12, 0, 30, Option.of(5)),
                job("b", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5))));

        Model clicked = new ChecksMsg.Clicked(CheckPick.of(
                        ForgejoChecksPort.of(job("a", CommitStatus.State.PASSED, 12, 0, 30, Option.of(5)))))
                .apply(model)
                .model();

        assertEquals(
                Option.of("a"),
                clicked.popup(ChecksPopup.class)
                        .flatMap(popup -> popup.pickedCheck(clicked))
                        .map(ForgeCheck::name));
    }

    @Test
    void escapeGoesBackToNothingWhenNotOpenedFromAPullRequest() {
        Model model = showing(List.of(job("b", CommitStatus.State.PASSED, 12, 0, 30, Option.of(5))));

        assertTrue(new ChecksMsg.Closed().apply(model).model().popup().isEmpty());
    }

    @Test
    void ofMoreEditorsOneIsChosenOverTheChecksWhichAreToldWhatIsReadMeanwhile() {
        EditorProgram nvim = EditorProgram.ofFile("Neovim", List.of("nvim", "+{line}", "{file}"), true, Option.none());
        EditorProgram code =
                EditorProgram.ofFile("Code", List.of("code", "-g", "{file}:{line}"), false, Option.of('c'));
        List<ForgejoCheck> running = List.of(job("b", CommitStatus.State.RUNNING, 12, 1, 30, Option.none()));
        List<ForgejoCheck> failed = List.of(job("b", CommitStatus.State.FAILED, 12, 1, 30, Option.of(5)));
        // The feature that opens files answers the log asked for (ADR 206).
        Model model = showing(running)
                .withReactions(new Reactions(EditFeature.FEATURE.reactions()))
                .mapPreferences(preferences -> preferences.withSettings(
                        preferences.settings().with(Settings.EDITORS, new Editors(List.of(nvim, code)))));
        CheckLogCmd cmd = new CheckLogCmd(FEATURE, "b", new ForgejoCheck.ActionsJob(12, 1));

        Model asking = new ChecksMsg.LogRead(cmd, "b", "/logs/12-1.log", 3, List.empty())
                .apply(model)
                .model();
        Model read = new ChecksMsg.Read(ForgejoChecksPort.PORT, FEATURE, readOf(failed))
                .apply(asking)
                .model();
        Model back = Update.update(read, new EditMsg.Cancelled()).model();

        EditPopup menu = asking.popup(EditPopup.class).get();
        assertEquals(Option.of(Phrase.of("forge.dialog.logEditor.title", "b")), menu.title());
        assertEquals(model.popup(), menu.under());
        assertEquals(
                failed,
                ForgejoChecksPort.checks(back.popup(ChecksPopup.class).get().checksRead()));
    }

    @Test
    void theLogOpensAtTheFirstStepThatFailedElseAtItsEnd() {
        assertEquals(2, CheckLogCmd.line("set up\n❌  Failure - Main test\nclean up\n"));
        assertEquals(3, CheckLogCmd.line("a\nb\nc\n"));
    }
}

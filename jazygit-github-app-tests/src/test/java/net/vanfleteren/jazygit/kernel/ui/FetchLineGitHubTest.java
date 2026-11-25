package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestCmd;
import net.vanfleteren.jazygit.features.changes.fetch.FetchCmd;
import net.vanfleteren.jazygit.features.github.create.NewPullRequestPopup;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import org.junit.jupiter.api.Test;

/**
 * The tests of FetchLineTest with GitHub in the app (ADR 171).
 */
class FetchLineGitHubTest {

    private static final Instant START = Instant.parse("2026-10-26T10:00:00Z");

    private static String rendered(Model model, Instant now, int width) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> FetchLine.render(model, now, width))) {
            return RenderedText.ofSize(runner, () -> FetchLine.render(model, now, width), width, 1)
                    .strip();
        }
    }

    private static String rendered(Model model, Instant now) throws Exception {
        return rendered(model, now, 80);
    }

    private static Model running(Model model, Cmd cmd, Instant started) {
        return model.mapJobs(jobs -> jobs.withStarted(List.of(cmd), started));
    }

    private static String spinning(Instant now, String text) {
        return TimeText.spinnerFrame(now) + " " + text;
    }

    @Test
    void theLabelsOfCommandsAndOperations() {
        assertEquals("Working…", FetchLine.labelText(Label.WORKING));
        assertEquals(
                "Fetching origin",
                FetchLine.labelText(new FetchCmd.Fetch(new RemoteName("origin"), List.empty())
                        .label()
                        .get()));
        assertEquals("Reading pull request #12345", FetchLine.labelText(Label.of("state.work.pullRequest", "12345")));
        BranchName feature = new BranchName("feature");
        BranchRef base = new BranchRef.Local(new BranchName("main"));
        assertEquals(
                "Reading the title of feature",
                FetchLine.labelText(new NewPullRequestCmd.ReadTitle(
                                feature,
                                base,
                                new NewPullRequestPopup.Opening(
                                        feature, new RemoteBranch("origin", feature), List.of(new BranchName("main"))))
                        .label()
                        .get()));
        assertEquals(
                "Reading the title of feature",
                FetchLine.labelText(new NewPullRequestCmd.RereadTitle(feature, new BranchName("main"), base)
                        .label()
                        .get()));
        assertEquals(
                "Push feature",
                FetchLine.labelText(Label.of("state.work.operation", Label.of("branches.log.title.push"), "feature")));
    }

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void onThePullRequestsScreenSaysHowLongAgoTheyWereRead() {
        Model prs = TestModels.loaded()
                .withNow(NOW)
                .withScreen(PrOverview.SCREEN)
                .mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", NOW)))
                .withPart(
                        PrOverview.PART,
                        PrOverview.INITIAL.withFreshness(Freshness.NEVER
                                .succeeded("mine", NOW.minusSeconds(3 * 3600))
                                .failed("repository")));

        assertEquals("read 3h ago · last read failed", FetchLine.freshness(prs));
    }

    private static String fetchedAgo(Duration since) {
        return FetchLine.freshness(TestModels.loaded()
                .withNow(NOW)
                .mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", NOW.minus(since)))));
    }
}

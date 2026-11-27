package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.builds.BuildPages;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The runs of a workflow on the builds screen, under the line of the default branch (ADR 212).
 */
class BuildsScreenBodyTest {

    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");
    private static final RepoWorkflow BUILD = new RepoWorkflow("W_build", "Build", ".github/workflows/build.yml", true);

    private static String line(BuildsScreenBody.DefaultLine onDefault) {
        return List.ofAll(new BuildsScreenBody(
                                onDefault,
                                BuildsTextTest.shown(BuildsTextTest.someRuns()),
                                Option.none(),
                                Option.none())
                        .line(Theme.SpanStyles.NONE)
                        .spans())
                .map(Span::content)
                .mkString();
    }

    private static BuildsScreenBody.DefaultLine read(
            int passed, int done, Build.Outcome last, Option<Duration> failing) {
        return new BuildsScreenBody.DefaultLine("main", passed, done, Option.of(last), failing, Option.none());
    }

    /**
     * The build {@code id} of Build on the default branch, {@code id} hours ago, which ended as {@code conclusion}.
     */
    private static Build build(long id, String conclusion) {
        Instant start = T.minusSeconds(3600 * id);
        return new Build(
                "Build",
                "push",
                id,
                1,
                new Sha("abcdef" + id),
                "Commit " + id,
                start,
                List.of(new Build.Job("verify", conclusion, Option.of(start), Option.of(start.plusSeconds(60)))));
    }

    private static Model model(BuildPages onDefault) {
        return TestModels.loaded()
                .withPart(
                        BuildsScreen.PART,
                        BuildsScreen.INITIAL
                                .withWorkflows(new Loadable.Loaded<>(List.of(BUILD)))
                                .withDefaultName(Option.of(new BranchName("main")))
                                .withOnDefault(onDefault));
    }

    @Test
    void theDefaultBranchSaysSinceWhenItFailsAndHowManyOfItsBuildsPassed() {
        assertEquals(
                "main ✗ failing for 2h · 14 of 15 passed",
                line(read(14, 15, Build.Outcome.FAILED, Option.of(Duration.ofHours(2)))));
        assertEquals("main ✓ 15 of 15 passed", line(read(15, 15, Build.Outcome.PASSED, Option.none())));
    }

    @Test
    void aCancelledLastBuildIsNotTakenForOneThatPassed() {
        assertEquals("main ⊘ 14 of 15 passed", line(read(14, 15, Build.Outcome.CANCELLED, Option.none())));
    }

    @Test
    void aWorkflowWithoutBuildsOnTheDefaultBranchSaysSo() {
        assertEquals(
                "main: no builds of this workflow among its last commits",
                line(new BuildsScreenBody.DefaultLine("main", 0, 0, Option.none(), Option.none(), Option.none())));
    }

    @Test
    void aDefaultBranchThatCouldNotBeReadSaysSoRatherThanThatItHasNoBuilds() {
        assertEquals(
                "The default branch could not be read: rate limit",
                line(new BuildsScreenBody.DefaultLine(
                        "", 0, 0, Option.none(), Option.none(), Option.of("rate limit"))));
    }

    @Test
    void theLineIsOfTheNewestBuildThatIsDoneAndOfTheFailuresBeforeIt() {
        BuildPages read = BuildPages.asked(25)
                .readCommits(Option.of(new BuildsPage(
                        List.of(build(1, "CANCELLED"), build(2, "FAILURE"), build(3, "SUCCESS")), 3, Option.none())));
        BuildsScreenBody.DefaultLine cancelled =
                BuildsScreenSource.defaultLine(model(read), model(read).part(BuildsScreen.PART), BUILD);

        assertEquals(Option.of(Build.Outcome.CANCELLED), cancelled.last());
        assertEquals(Option.none(), cancelled.failingFor());
        assertEquals(1, cancelled.passed());
        assertEquals(3, cancelled.done());
    }

    @Test
    void aFailedReadOfTheDefaultBranchReachesTheLine() {
        Model failed = model(BuildPages.asked(25).failed(TestModels.refused("rate limit")));

        assertTrue(BuildsScreenSource.defaultLine(failed, failed.part(BuildsScreen.PART), BUILD)
                .unread()
                .isDefined());
    }

    @Test
    void theRunsAreDrawnToTheWidthOfTheirArea() {
        assertTrue(new BuildsScreenBody(
                        new BuildsScreenBody.DefaultLine("main", 0, 0, Option.none(), Option.none(), Option.none()),
                        BuildsTextTest.shown(BuildsTextTest.someRuns()),
                        Option.none(),
                        Option.none())
                .drawnToWidth());
    }
}

package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The runs of a workflow on the builds screen, under the line of the default branch (ADR 212).
 */
class BuildsScreenBodyTest {

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

    @Test
    void theDefaultBranchSaysSinceWhenItFailsAndHowManyOfItsBuildsPassed() {
        assertEquals(
                "main ✗ failing for 2h · 14 of 15 passed",
                line(new BuildsScreenBody.DefaultLine("main", 14, 15, Option.of(Duration.ofHours(2)))));
        assertEquals("main ✓ 15 of 15 passed", line(new BuildsScreenBody.DefaultLine("main", 15, 15, Option.none())));
    }

    @Test
    void aWorkflowWithoutBuildsOnTheDefaultBranchSaysSo() {
        assertEquals(
                "main: no builds of this workflow among its last commits",
                line(new BuildsScreenBody.DefaultLine("main", 0, 0, Option.none())));
    }

    @Test
    void theRunsAreDrawnToTheWidthOfTheirArea() {
        assertTrue(new BuildsScreenBody(
                        new BuildsScreenBody.DefaultLine("main", 0, 0, Option.none()),
                        BuildsTextTest.shown(BuildsTextTest.someRuns()),
                        Option.none(),
                        Option.none())
                .drawnToWidth());
    }
}

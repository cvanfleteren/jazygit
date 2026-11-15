package net.vanfleteren.jazygit.ui.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The builds of a branch as the content panel draws them (ADR 131).
 */
class BuildsTextTest {

    private static final Instant MIDNIGHT = Instant.parse("2026-11-13T00:00:00Z");

    private static Build.Job job(String name, String conclusion, long from, long to) {
        return new Build.Job(
                name, conclusion, Option.of(MIDNIGHT.plusSeconds(from)), Option.of(MIDNIGHT.plusSeconds(to)));
    }

    /**
     * The {@code index}th build of {@code workflow}, an hour after the one before, which took {@code took} seconds
     * and ended as {@code conclusion}.
     */
    private static Build build(String workflow, int index, String conclusion, long took) {
        long start = index * 3600L;
        return new Build(
                workflow,
                "push",
                index,
                1,
                "abcdef0" + index,
                "Commit " + index,
                MIDNIGHT.plusSeconds(start),
                List.of(
                        job("package", "SUCCESS", start + 5, start + 60),
                        job("acceptance (1/2)", conclusion, start + 60, start + took),
                        job("acceptance (2/2)", "SUCCESS", start + 60, start + took - 30),
                        job("release", "SKIPPED", start + took, start + took)));
    }

    private static String text(Line line) {
        return List.ofAll(line.spans()).map(Span::content).mkString();
    }

    private static List<String> drawn(List<Build> builds) {
        return BuildsText.lines(builds, HashMap.empty(), Option.none(), ZoneOffset.UTC, Theme.SpanStyles.NONE, 80)
                .map(BuildsTextTest::text);
    }

    // 30 builds of Publish, the newest first, which took 4 minutes, but #20 that failed in 5 and #25 in 20.
    private static final List<Build> PUBLISH = List.range(0, 30)
            .map(index -> build(
                    "Publish",
                    index,
                    index == 20 ? "FAILURE" : "SUCCESS",
                    index == 25 ? 1200 : index == 20 ? 300 : 240))
            .reverse();

    @Test
    void theTabsNameEachWorkflowAndOnlyTheOneShownHasItsBuilds() {
        List<Build> builds = PUBLISH.prepend(build("Nix", 40, "FAILURE", 100));

        List<String> lines = drawn(builds);
        assertEquals("✗ Nix │ ✓ Publish", lines.head());
        assertEquals("Nix · push · 1 builds · 14 Nov – 14 Nov", lines.get(2));
        assertFalse(lines.exists(line -> line.startsWith("Publish · push")), lines.mkString("\n"));

        List<String> publish = BuildsText.lines(
                        builds,
                        HashMap.empty(),
                        Option.of(new BuildsView.Pipeline("Publish", "push")),
                        ZoneOffset.UTC,
                        Theme.SpanStyles.NONE,
                        80)
                .map(BuildsTextTest::text);
        assertEquals("Publish · push · 30 builds · 13 Nov – 14 Nov", publish.get(2));
    }

    @Test
    void theSummaryCountsHowTheBuildsEndedAndHowLongTheyTook() {
        String summary = drawn(PUBLISH).get(3);

        assertTrue(summary.startsWith("✓ 29  ✗ 1  ⊘ 0"), summary);
        assertTrue(summary.contains("passed 97%   median 4m00   p90 4m00"), summary);
        assertTrue(summary.contains("last 4m00 +0s"), summary);
    }

    @Test
    void theChartIsScaledBelowTheSlowestBuildWhichItMarks() {
        List<String> lines = drawn(PUBLISH);
        List<String> chart = lines.drop(5).take(8);

        assertTrue(chart.head().startsWith("   8m┤"), chart.mkString("\n"));
        assertTrue(chart.head().contains("▲"), chart.mkString("\n"));
        assertTrue(chart.last().startsWith("     │" + "█".repeat(20)), chart.mkString("\n"));
        assertTrue(lines.exists(line -> line.startsWith("     └┴") && line.contains("┴")), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.trim().equals("13 Nov                  14 Nov")), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.trim().equals("✗")), lines.mkString("\n"));
    }

    @Test
    void theShardsOfAJobAreOneJobAndASkippedJobNone() {
        List<String> lines = drawn(PUBLISH);

        assertTrue(lines.exists(line -> line.startsWith("package    ")), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.startsWith("acceptance ")), lines.mkString("\n"));
        assertFalse(lines.exists(line -> line.startsWith("release ")), lines.mkString("\n"));
        assertFalse(lines.exists(line -> line.startsWith("acceptance (1/2) ") && line.contains("▆")));
    }

    @Test
    void theLastBuildShowsItsJobsOnOneTimeLine() {
        List<String> lines = drawn(PUBLISH);
        int header = lines.indexWhere(line -> line.contains("· the last build, 4m00"));

        assertTrue(lines.get(header).startsWith("✓ abcdef0 Commit 29"), lines.get(header));
        assertTrue(lines.get(header + 1).startsWith("package       │ █"), lines.get(header + 1));
        assertTrue(lines.get(header + 1).endsWith("55s"), lines.get(header + 1));
    }

    @Test
    void theBuildsAreListedTheNewestFirst() {
        List<String> lines = drawn(PUBLISH);
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals("✓ abcdef0  14 Nov 05:00   4m00     +0s  Commit 29", lines.get(header + 1));
        assertTrue(
                lines.exists(line -> line.startsWith("✗ abcdef0  13 Nov 20:00   5m00") && line.endsWith("Commit 20")));
    }

    @Test
    void durationsAreShortest() {
        assertEquals("45s", BuildsText.duration(Duration.ofSeconds(45)));
        assertEquals("4m08", BuildsText.duration(Duration.ofSeconds(248)));
        assertEquals("1h02", BuildsText.duration(Duration.ofMinutes(62)));
        assertEquals("−27s", BuildsText.signed(Duration.ofSeconds(-27)));
    }

    @Test
    void theMedianOfAnEvenNumberIsBetweenTheMiddleTwo() {
        assertEquals(
                Option.of(Duration.ofSeconds(15)),
                BuildsText.median(List.of(
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(20),
                        Duration.ofSeconds(5))));
        assertEquals(Option.none(), BuildsText.median(List.empty()));
    }

    @Test
    void aPercentileIsTheNearestRank() {
        List<Duration> tens = List.range(1, 11).map(Duration::ofSeconds);

        assertEquals(Option.of(Duration.ofSeconds(9)), BuildsText.percentile(tens, 0.9));
        assertEquals(Option.of(Duration.ofSeconds(10)), BuildsText.percentile(tens, 0.95));
        assertEquals(Option.of(Duration.ofSeconds(1)), BuildsText.percentile(tens, 0.0));
        assertEquals(Option.none(), BuildsText.percentile(List.empty(), 0.9));
    }
}

package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.view.BuildsShown;
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

    /**
     * What the builds of a branch with {@code builds} show, {@code chosen} the tab chosen.
     */
    static BuildsShown shown(List<Build> builds, Option<BuildsView.Pipeline> chosen) {
        BuildsView view = BuildsView.reading(new BranchName("main"), new RemoteBranch("origin", new BranchName("main")))
                .read(Option.of(new BuildsPage(builds, 1, Option.none())));
        return chosen.map(view::withSelected).getOrElse(view).shownOf().get();
    }

    private static List<String> drawn(List<Build> builds) {
        return BuildsText.lines(shown(builds, Option.none()), ZoneOffset.UTC, Theme.SpanStyles.NONE, 80)
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
                        shown(builds, Option.of(new BuildsView.Pipeline("Publish", "push"))),
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
        assertTrue(lines.exists(line -> line.trim().matches("13 Nov +14 Nov")), lines.mkString("\n"));
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
        assertTrue(lines.get(header).endsWith("· the last build, 4m00, 14 Nov 05:00 · queued 5s"), lines.get(header));
        assertTrue(lines.get(header + 1).strip().matches("took\\s+waited\\s+vs median"), lines.get(header + 1));
        // package took 55 s, as in every build; how long it waited is not known without the file of its workflow.
        assertTrue(lines.get(header + 2).startsWith("package       │ █"), lines.get(header + 2));
        assertTrue(lines.get(header + 2).endsWith("55s       –        +0s"), lines.get(header + 2));
    }

    /**
     * What the builds of {@code builds} show with the build of the run {@code run} picked.
     */
    private static BuildsShown picked(List<Build> builds, long run) {
        return BuildsView.reading(new BranchName("main"), new RemoteBranch("origin", new BranchName("main")))
                .read(Option.of(new BuildsPage(builds, 1, Option.none())))
                .withPicked(Option.of(run))
                .shownOf()
                .get();
    }

    @Test
    void aBuildPickedShowsItsJobsOnTheTimeLineAndIsMarkedInTheList() {
        List<String> lines = BuildsText.lines(picked(PUBLISH, 25), ZoneOffset.UTC, Theme.SpanStyles.NONE, 80)
                .map(BuildsTextTest::text);

        String header = lines.find(line -> line.contains("· the build picked")).get();
        assertTrue(header.startsWith("✓ abcdef0 Commit 25"), header);
        assertTrue(header.contains("the build picked, 20m00, 14 Nov 01:00"), header);
        assertFalse(lines.exists(line -> line.contains("· the last build")), lines.mkString("\n"));
        assertEquals(
                List.of("✓▸abcdef0  14 Nov 01:00  20m00      5s     +16m00  Commit 25"),
                lines.filter(line -> line.startsWith("✓▸")));
    }

    @Test
    void aBuildPickedThatNoLongerShowsGivesWayToTheLastOne() {
        List<String> lines = BuildsText.lines(picked(PUBLISH, 99), ZoneOffset.UTC, Theme.SpanStyles.NONE, 80)
                .map(BuildsTextTest::text);

        assertTrue(lines.exists(line -> line.contains("· the last build, 4m00")), lines.mkString("\n"));
        assertFalse(lines.exists(line -> line.contains("▸")), lines.mkString("\n"));
    }

    @Test
    void aBuildIsFoundByARowOfTheListOrAColumnOfTheChart() {
        BuildsShown shown = shown(PUBLISH, Option.none());
        List<String> lines = drawn(PUBLISH);
        int list = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals(Option.of(29L), BuildsText.buildAt(shown, 80, list + 1, 3));
        assertEquals(Option.of(0L), BuildsText.buildAt(shown, 80, lines.size() - 1, 3));
        assertEquals(Option.none(), BuildsText.buildAt(shown, 80, list, 3));
        // The chart has 30 builds of two columns each in its 73, the oldest first, after its scale.
        assertEquals(Option.of(0L), BuildsText.buildAt(shown, 80, 12, 6));
        assertEquals(Option.of(1L), BuildsText.buildAt(shown, 80, 12, 8));
        assertEquals(Option.of(29L), BuildsText.buildAt(shown, 80, 5, 6 + 2 * 29 + 1));
        assertEquals(Option.none(), BuildsText.buildAt(shown, 80, 12, 3));
        assertEquals(Option.none(), BuildsText.buildAt(shown, 80, 3, 10));
    }

    @Test
    void theBuildsAreListedTheNewestFirst() {
        List<String> lines = drawn(PUBLISH);
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals("✓ abcdef0  14 Nov 05:00   4m00      5s        +0s  Commit 29", lines.get(header + 1));
        assertTrue(
                lines.exists(line -> line.startsWith("✗ abcdef0  13 Nov 20:00   5m00") && line.endsWith("Commit 20")));
    }

    @Test
    void howLongABuildWaitedForItsFirstJobIsListedButNotOfARerun() {
        // The newest waited 2m48 for its first job, as when another run of its workflow held it back; the one
        // before is a re-run, whose run keeps when its first attempt started.
        Build held = new Build(
                "Publish",
                "push",
                2,
                1,
                "abcdef02",
                "Commit 2",
                MIDNIGHT.plusSeconds(7200),
                List.of(job("package", "SUCCESS", 7200 + 168, 7200 + 413)));
        Build rerun = new Build(
                "Publish",
                "push",
                1,
                2,
                "abcdef01",
                "Commit 1",
                MIDNIGHT.plusSeconds(3600),
                List.of(job("package", "SUCCESS", 5000, 5240)));
        List<String> lines = drawn(List.of(held, rerun));
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals("✓ abcdef0  13 Nov 02:00   6m53    2m48      +1m26  Commit 2", lines.get(header + 1));
        assertEquals("✓ abcdef0  13 Nov 01:00   4m00              −1m26  Commit 1", lines.get(header + 2));
    }

    @Test
    void aBuildWhoseJobsWaitForARunnerIsNotTakenForARerun() {
        // Its only job has no runner yet: how long it waits is not known yet, unlike a re-run's, which never is.
        Build waiting = new Build(
                "Publish",
                "push",
                3,
                1,
                "abcdef03",
                "Commit 3",
                MIDNIGHT.plusSeconds(10800),
                List.of(new Build.Job("package", "", Option.none(), Option.none())));
        List<String> lines = drawn(List.of(waiting, build("Publish", 1, "SUCCESS", 240)));
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertTrue(
                lines.get(header + 1).endsWith("abcdef0  13 Nov 03:00      –       –             Commit 3"),
                lines.get(header + 1));
    }

    @Test
    void aDayOfOneDigitIsAlignedWithOneOfTwo() {
        // Builds on 13 and 3 November: the day of the second is padded, so the columns after it stay put.
        List<String> lines =
                drawn(List.of(build("Publish", 1, "SUCCESS", 240), build("Publish", -240, "SUCCESS", 300)));
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals("  commit   when           time  queued  vs median  title", lines.get(header));
        assertEquals("✓ abcdef0  13 Nov 01:00   4m00      5s       −30s  Commit 1", lines.get(header + 1));
        assertEquals("✓ abcdef0   3 Nov 00:00   5m00      5s       +30s  Commit -240", lines.get(header + 2));
    }

    @Test
    void aDayOfTwoDigitsIsReservedForEvenFarEast() {
        // Only builds on the 3rd in Auckland, at UTC+13: the column is as wide as with a build on the 13th, so it
        // stays put when older builds are read.
        List<String> lines = BuildsText.lines(
                        shown(
                                List.of(build("Publish", -240, "SUCCESS", 240), build("Publish", -250, "SUCCESS", 300)),
                                Option.none()),
                        ZoneId.of("Pacific/Auckland"),
                        Theme.SpanStyles.NONE,
                        80)
                .map(BuildsTextTest::text);
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals("  commit   when           time  queued  vs median  title", lines.get(header));
        assertTrue(lines.get(header + 1).startsWith("✓ abcdef0   3 Nov 13:00   4m00"), lines.get(header + 1));
        assertTrue(lines.get(header + 2).startsWith("✓ abcdef0   3 Nov 03:00   5m00"), lines.get(header + 2));
    }

    @Test
    void aTimeBelowAnHourIsReservedFor() {
        // Builds of a few minutes: their titles start where those of builds of twenty minutes do.
        List<String> quick = drawn(List.of(build("Publish", 1, "SUCCESS", 120), build("Publish", 0, "SUCCESS", 180)));
        List<String> slow = drawn(PUBLISH);
        String title = "  queued  vs median  title";

        assertEquals(
                slow.find(line -> line.contains(title)).get(),
                quick.find(line -> line.contains(title)).get());
        assertTrue(quick.exists(line -> line.startsWith("✓ abcdef0  13 Nov 01:00   2m00")), quick.mkString("\n"));
        assertEquals(
                slow.find(line -> line.endsWith("Commit 29")).get().indexOf("Commit"),
                quick.find(line -> line.endsWith("Commit 1")).get().indexOf("Commit"));
    }

    /**
     * What {@code builds} of every branch show, the first of them run for {@code first}.
     */
    private static List<String> drawnOnEveryBranch(List<Build> builds, Build.Origin first) {
        Build head = builds.head();
        List<Build> ran = builds.tail()
                .prepend(new Build(
                        head.workflow(),
                        head.event(),
                        head.runId(),
                        head.attempt(),
                        head.sha(),
                        head.title(),
                        head.createdAt(),
                        head.jobs(),
                        head.file(),
                        first));
        BuildsShown shown = shown(ran, Option.none());
        BuildsShown everyBranch = new BuildsShown(
                shown.tabs(),
                shown.lasts(),
                shown.index(),
                true,
                shown.builds(),
                shown.of(),
                shown.mine(),
                shown.baseline(),
                shown.needs(),
                shown.picked());
        return BuildsText.lines(everyBranch, ZoneOffset.UTC, Theme.SpanStyles.NONE, 120)
                .map(BuildsTextTest::text);
    }

    @Test
    void theColumnOfWhatABuildRanForIsAsWideAsWhatItShows() {
        List<String> lines =
                drawnOnEveryBranch(PUBLISH, new Build.Origin(Option.none(), Option.none(), Option.of(501)));
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals("    for  commit   when           time  queued  vs median  title", lines.get(header));
        assertEquals("✓   #501 abcdef0  14 Nov 05:00   4m00      5s        +0s  Commit 29", lines.get(header + 1));
        assertEquals("✓   –    abcdef0  14 Nov 04:00   4m00      5s        +0s  Commit 28", lines.get(header + 2));
    }

    @Test
    void aLongBranchIsCutToTheWidestTheColumnOfWhatABuildRanForIs() {
        List<String> lines = drawnOnEveryBranch(
                PUBLISH, new Build.Origin(Option.none(), Option.of("feature/a-long-branch"), Option.none()));
        int header = lines.indexWhere(line -> line.contains("commit   when"));

        assertEquals("    for           commit   when           time  queued  vs median  title", lines.get(header));
        assertTrue(lines.get(header + 1).startsWith("✓   feature/a-lo… abcdef0"), lines.get(header + 1));
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

    @Test
    void aJobThatFailedIsAsHighInTheStripAsOneThatPassed() {
        // acceptance failed in build 20 of the 30; the strip of the last 30 starts after the names.
        String acceptance =
                drawn(PUBLISH).find(line -> line.startsWith("acceptance ")).get();

        assertEquals("▆".repeat(30), acceptance.substring("acceptance ".length(), "acceptance ".length() + 30));
    }

    @Test
    void theHeadersOfTheJobsShowInFullWithOneBuild() {
        List<String> lines = drawn(List.of(build("Publish", 0, "SUCCESS", 240)));
        String header = lines.find(line -> line.startsWith("job")).get();
        String row = lines.find(line -> line.startsWith("package ")).get();

        assertTrue(header.contains("last 1  time  median"), header);
        // The strip and the sparkline sit under their headers, the numbers under theirs.
        assertEquals(header.indexOf("last 1"), row.indexOf("▆"), header + "\n" + row);
        assertEquals(
                header.indexOf("median") + "median".length(), row.indexOf("55s") + "55s".length(), header + "\n" + row);
    }

    @Test
    void theNewestTwentyShowWithWiderBarsAndTheTitleSaysOfHowMany() {
        BuildsShown twenty = shown(PUBLISH, Option.none())
                .withBuilds(new net.vanfleteren.jazygit.state.Loadable.Loaded<>(PUBLISH.take(20)));
        List<String> lines = BuildsText.lines(twenty, ZoneOffset.UTC, Theme.SpanStyles.NONE, 80)
                .map(BuildsTextTest::text);

        assertEquals("Publish · push · last 20 of 30 builds · 13 Nov – 14 Nov", lines.get(2));
        // 73 columns for 20 builds: three each, a bar of two and a column between them.
        String bottom = lines.drop(5).take(8).last();
        assertTrue(bottom.startsWith("     │" + "██ ".repeat(20)), bottom);
    }

    @Test
    void tenBuildsGetBarsWithAColumnBetweenThem() {
        BuildsShown ten = shown(PUBLISH, Option.none())
                .withBuilds(new net.vanfleteren.jazygit.state.Loadable.Loaded<>(PUBLISH.take(10)));
        String bottom = BuildsText.lines(ten, ZoneOffset.UTC, Theme.SpanStyles.NONE, 80)
                .map(BuildsTextTest::text)
                .drop(5)
                .take(8)
                .last();

        assertTrue(bottom.startsWith("     │████ ████ ████ "), bottom);
    }
}

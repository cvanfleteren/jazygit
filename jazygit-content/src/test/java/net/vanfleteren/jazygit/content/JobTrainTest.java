package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The jobs of a build on one time line, each hanging off the jobs it waits for (ADR 131), with how long each waited
 * and took against its median (ADR 133).
 */
class JobTrainTest {

    private static final Instant START = Instant.parse("2026-11-15T08:00:00Z");

    private static Build.Job job(String name, long from, long to) {
        return new Build.Job(name, "SUCCESS", Option.of(START.plusSeconds(from)), Option.of(START.plusSeconds(to)));
    }

    // The build of b17c669 on jazygit's main branch.
    private static final Build BUILD = new Build(
            "Publish",
            "push",
            1,
            1,
            "b17c669",
            "A sealed Focus",
            START,
            List.of(
                    job("verify / changes", 2, 5),
                    job("build", 2, 243),
                    job("verify / package", 7, 85),
                    job("verify / verify", 7, 134),
                    job("verify / acceptance (1/2)", 86, 202),
                    job("verify / acceptance (2/2)", 86, 225),
                    job("verify / build-ok", 226, 230),
                    job("snapshot", 244, 256)));

    // As Workflow.needs gives them: a job that needs none needs an empty set.
    private static final Map<String, Set<String>> NEEDS = HashMap.<String, Set<String>>of(
                    "verify / changes", HashSet.empty(),
                    "build", HashSet.empty())
            .put("verify / package", HashSet.of("verify / changes"))
            .merge(HashMap.of(
                    "verify / verify", HashSet.of("verify / changes"),
                    "verify / acceptance (1/2)", HashSet.of("verify / package"),
                    "verify / acceptance (2/2)", HashSet.of("verify / package"),
                    "verify / build-ok",
                            HashSet.of("verify / verify", "verify / acceptance (1/2)", "verify / acceptance (2/2)"),
                    "snapshot", HashSet.of("verify / build-ok", "build")));

    // The time line is 50 columns wide, after the names and before the numbers.
    private static final int PANEL = 50 + 23 + 2 + 10 + 8 + 11;

    private static List<String> drawn(Map<String, Set<String>> needs) {
        return text(JobTrain.lines(BUILD, needs, HashMap.empty(), PANEL, Theme.SpanStyles.NONE))
                .tail();
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> List.ofAll(line.spans()).map(Span::content).mkString());
    }

    @Test
    void theShardsOfAJobAreOneRowWithTheQuickestAndTheSlowest() {
        List<String> rows = drawn(NEEDS);

        String shards =
                rows.find(row -> row.startsWith("verify / acceptance ×2")).get();
        assertTrue(shards.contains("█▒"), shards);
        assertTrue(shards.contains("1m56–2m19"), shards);
        assertEquals(7, rows.size());
    }

    @Test
    void aJobComesAfterAllItNeedsAsCloseUnderTheLastOfThemAsCanBe() {
        assertEquals(
                List.of(
                        "verify / changes",
                        "verify / package",
                        "verify / acceptance",
                        "verify / verify",
                        "verify / build-ok",
                        "build",
                        "snapshot"),
                JobTrain.ordered(JobTrain.groups(BUILD, NEEDS)).map(JobTrain.Group::name));
    }

    @Test
    void aLineDropsFromTheEndOfAJobToAnArrowBeforeEachJobThatNeedsIt() {
        List<String> rows = drawn(NEEDS);

        assertTrue(rows.get(0).contains("█╮"), rows.mkString("\n"));
        assertTrue(rows.get(1).contains("├▶█"), rows.mkString("\n"));
        assertTrue(rows.get(2).contains("│"), rows.mkString("\n"));
        assertTrue(rows.get(3).contains("╰▶█"), rows.mkString("\n"));
        assertTrue(rows.get(4).contains("╰─") && rows.get(4).contains("┴▶█"), rows.mkString("\n"));
        assertTrue(rows.get(6).contains("┴▶█"), rows.mkString("\n"));
    }

    @Test
    void withoutWhatTheyNeedTheJobsShowByWhenTheyStartedWithoutLines() {
        List<String> rows = drawn(HashMap.empty());

        assertTrue(rows.head().startsWith("verify / changes"), rows.mkString("\n"));
        assertTrue(rows.get(1).startsWith("build"), rows.mkString("\n"));
        assertFalse(rows.exists(row -> row.contains("▶") || row.contains("╮")), rows.mkString("\n"));
    }

    @Test
    void aLineTurnsWhereItLeavesACell() {
        assertEquals("╰", JobTrain.Direction.box(HashSet.of(JobTrain.Direction.UP, JobTrain.Direction.RIGHT)));
        assertEquals("┼", JobTrain.Direction.box(HashSet.of(JobTrain.Direction.values())));
    }

    @Test
    void everyRowFitsInThePanelWithALongJobName() {
        Build longNames = new Build(
                "Publish",
                "push",
                1,
                1,
                "abc",
                "Title",
                START,
                List.of(
                        job("Publish fat-jar and native image / build", 2, 120),
                        job("verify / acceptance (1/2)", 2, 100),
                        job("verify / acceptance (2/2)", 2, 110)));

        List<String> rows =
                text(JobTrain.lines(longNames, HashMap.empty(), HashMap.empty(), 80, Theme.SpanStyles.NONE));

        assertTrue(rows.forAll(row -> row.length() <= 80), rows.mkString("\n"));
        assertTrue(rows.exists(row -> row.contains("1m38–1m48")), rows.mkString("\n"));
    }

    @Test
    void theTimeLineAndTheNamesSpreadOverAWidePanel() {
        Build longNames = new Build(
                "Publish",
                "push",
                1,
                1,
                "abc",
                "Title",
                START,
                List.of(job("Publish fat-jar and native image / build", 2, 120), job("verify", 120, 240)));

        List<String> rows =
                text(JobTrain.lines(longNames, HashMap.empty(), HashMap.empty(), 200, Theme.SpanStyles.NONE));

        assertTrue(rows.forAll(row -> row.length() <= 200), rows.mkString("\n"));
        assertTrue(
                rows.exists(row -> row.startsWith("Publish fat-jar and native image / build │")), rows.mkString("\n"));
        // The time line takes what the names and the numbers leave: well over the 60 columns it was held to.
        String verify = rows.find(row -> row.startsWith("verify ")).get();
        assertTrue(verify.lastIndexOf('│') - verify.indexOf('│') > 100, verify);
    }

    @Test
    void eachJobSaysHowLongItWaitedAfterWhatItNeedsAndHowItTookAgainstItsMedian() {
        List<String> rows = text(JobTrain.lines(
                BUILD,
                NEEDS,
                HashMap.of("build", Duration.ofSeconds(200), "verify / verify", Duration.ofSeconds(130)),
                PANEL,
                Theme.SpanStyles.NONE));

        assertTrue(rows.head().strip().matches("took\\s+waited\\s+vs median"), rows.head());
        // The package waited from 5 s, when the changes it needs ended, to 7 s.
        assertTrue(
                rows.find(row -> row.startsWith("verify / package")).get().endsWith("1m18      2s"),
                rows.mkString("\n"));
        // build took 4m01 against 3m20; verify took 2m07 against 2m10.
        assertTrue(
                rows.find(row -> row.startsWith("build")).get().endsWith("4m01      2s       +41s"),
                rows.mkString("\n"));
        assertTrue(rows.find(row -> row.startsWith("verify / verify")).get().endsWith("−3s"), rows.mkString("\n"));
    }

    @Test
    void theJobsThatDecidedHowLongTheBuildTookAreTheLastToEndAndWhatItWaitedFor() {
        // snapshot ended last; of what it needs, build ended after build-ok.
        assertEquals(HashSet.of("snapshot", "build"), JobTrain.critical(JobTrain.groups(BUILD, NEEDS)));
        Theme.SpanStyles bold = cssClass -> cssClass.equals(Theme.Classes.BOLD) ? Style.EMPTY.bold() : Style.EMPTY;
        List<Line> lines = JobTrain.lines(BUILD, NEEDS, HashMap.empty(), PANEL, bold);
        List<String> emphasised = lines.tail()
                .filter(line -> line.spans().get(0).style().equals(Style.EMPTY.bold()))
                .map(line -> line.spans().get(0).content().strip());
        assertEquals(List.of("build", "snapshot"), emphasised);
    }

    @Test
    void withoutWhatTheyNeedNoJobIsSaidToHaveDecided() {
        Theme.SpanStyles bold = cssClass -> cssClass.equals(Theme.Classes.BOLD) ? Style.EMPTY.bold() : Style.EMPTY;
        assertFalse(JobTrain.lines(BUILD, HashMap.empty(), HashMap.empty(), PANEL, bold)
                .tail()
                .exists(line -> line.spans().get(0).style().equals(Style.EMPTY.bold())));
    }

    @Test
    void withoutWhatTheyNeedNoJobIsSaidToHaveWaitedForARunner() {
        List<String> rows = drawn(HashMap.empty());

        // From the start of the build, snapshot would seem to have waited 4 minutes for a runner.
        assertTrue(rows.forAll(row -> row.endsWith("       –")), rows.mkString("\n"));
    }

    @Test
    void aJobThatNeedsOneThatDidNotRunIsNotSaidToHaveWaited() {
        // snapshot needs release too, which was skipped: when release was done is not known. No line comes from it.
        List<String> rows = drawn(NEEDS.put("snapshot", HashSet.of("verify / build-ok", "build", "release")));

        assertTrue(rows.find(row -> row.startsWith("snapshot")).get().endsWith("12s       –"), rows.mkString("\n"));
        assertTrue(rows.find(row -> row.startsWith("build")).get().endsWith("4m01      2s"), rows.mkString("\n"));
    }

    @Test
    void aReRunSaysNothingOfHowLongItsJobsWaited() {
        // build passed in the first attempt; snapshot ran again hours later, in the second.
        Build reRun = new Build(
                "Publish",
                "push",
                1,
                2,
                "b17c669",
                "A sealed Focus",
                START,
                BUILD.jobs()
                        .map(job -> job.name().equals("snapshot")
                                ? new Build.Job(
                                        "snapshot",
                                        "SUCCESS",
                                        Option.of(START.plusSeconds(3 * 3600)),
                                        Option.of(START.plusSeconds(3 * 3600 + 12)))
                                : job));

        List<String> rows = text(JobTrain.lines(reRun, NEEDS, HashMap.empty(), PANEL, Theme.SpanStyles.NONE))
                .tail();

        assertTrue(rows.forAll(row -> row.endsWith("       –")), rows.mkString("\n"));
    }

    @Test
    void aJobNotMatchedInTheWorkflowIsNotSaidToHaveWaitedButTheOthersAre() {
        // One shard of acceptance, and snapshot, matched no job of the workflow: what they need is not known.
        List<String> rows = drawn(NEEDS.remove("verify / acceptance (2/2)").remove("snapshot"));

        assertTrue(rows.find(row -> row.startsWith("verify / acceptance")).get().endsWith("–"), rows.mkString("\n"));
        assertTrue(rows.find(row -> row.startsWith("snapshot")).get().endsWith("–"), rows.mkString("\n"));
        assertTrue(
                rows.find(row -> row.startsWith("verify / package")).get().endsWith("1m18      2s"),
                rows.mkString("\n"));
        assertTrue(rows.find(row -> row.startsWith("build")).get().endsWith("4m01      2s"), rows.mkString("\n"));
    }

    @Test
    void aBuildStillRunningHasNoJobThatDecidedAndARunningJobNoTimeAgainstItsMedian() {
        Build running = new Build(
                "Publish",
                "push",
                1,
                1,
                "b17c669",
                "A sealed Focus",
                START,
                BUILD.jobs()
                        .filter(job -> !job.name().equals("snapshot"))
                        .map(job -> job.name().equals("build")
                                ? new Build.Job("build", "", job.startedAt(), Option.none())
                                : job));
        Theme.SpanStyles bold = cssClass -> cssClass.equals(Theme.Classes.BOLD) ? Style.EMPTY.bold() : Style.EMPTY;

        List<Line> lines = JobTrain.lines(running, NEEDS, HashMap.of("build", Duration.ofSeconds(200)), PANEL, bold);

        assertFalse(lines.tail().exists(line -> line.spans().get(0).style().equals(Style.EMPTY.bold())));
        String build = text(lines).find(row -> row.startsWith("build")).get();
        assertTrue(build.endsWith("      2s          –"), build);
    }
}

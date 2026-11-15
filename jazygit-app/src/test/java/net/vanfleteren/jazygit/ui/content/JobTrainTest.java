package net.vanfleteren.jazygit.ui.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The jobs of a build on one time line, each hanging off the jobs it waits for (ADR 131).
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

    private static final Map<String, Set<String>> NEEDS = HashMap.of(
            "verify / package", HashSet.of("verify / changes"),
            "verify / verify", HashSet.of("verify / changes"),
            "verify / acceptance (1/2)", HashSet.of("verify / package"),
            "verify / acceptance (2/2)", HashSet.of("verify / package"),
            "verify / build-ok",
                    HashSet.of("verify / verify", "verify / acceptance (1/2)", "verify / acceptance (2/2)"),
            "snapshot", HashSet.of("verify / build-ok", "build"));

    private static List<String> drawn(Map<String, Set<String>> needs) {
        return JobTrain.lines(BUILD, needs, 50 + 23 + 2 + 10, Theme.SpanStyles.NONE)
                .map(line -> List.ofAll(line.spans()).map(Span::content).mkString());
    }

    @Test
    void theShardsOfAJobAreOneRowWithTheQuickestAndTheSlowest() {
        List<String> rows = drawn(NEEDS);

        String shards =
                rows.find(row -> row.startsWith("verify / acceptance ×2")).get();
        assertTrue(shards.contains("█▒"), shards);
        assertTrue(shards.endsWith("1m56–2m19"), shards);
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

        List<String> rows = JobTrain.lines(longNames, HashMap.empty(), 80, Theme.SpanStyles.NONE)
                .map(line -> List.ofAll(line.spans()).map(Span::content).mkString());

        assertTrue(rows.forAll(row -> row.length() <= 80), rows.mkString("\n"));
        assertTrue(rows.exists(row -> row.endsWith("1m38–1m48")), rows.mkString("\n"));
    }
}

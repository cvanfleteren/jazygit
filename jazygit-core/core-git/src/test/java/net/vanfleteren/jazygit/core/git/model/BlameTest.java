package net.vanfleteren.jazygit.core.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;

/**
 * Blame as {@code git blame --porcelain} prints it (ADR 147).
 */
class BlameTest {

    private static final String FIRST = "b88f03010644341ff85d4d2a56aa12f5bdf2ed69";
    private static final String SECOND = "1ed8617a2a5e40bd1b87cf5eab5352e4ce753c7e";
    private static final String ZEROS = "0".repeat(40);

    @Test
    void eachLineIsBlamedOnTheCommitNamedBeforeIt() {
        Blame blame = Blame.parse(String.join(
                        "\n",
                        details(FIRST, "1 1 1", "Alex Moreau", 1795021394, "first one", "boundary"),
                        "filename f.txt",
                        "\ta",
                        details(SECOND, "2 2 1", "Sam Lee", 1795021500, "second", null),
                        "previous " + FIRST + " f.txt",
                        "filename f.txt",
                        "\tB",
                        FIRST + " 3 3 1",
                        "\tc",
                        ""))
                .get();

        assertEquals(3, blame.lines().size());
        Blame.Line first = blame.at(1).get();
        assertEquals(
                new Blame.Commit(new Sha(FIRST), "Alex Moreau", Instant.ofEpochSecond(1795021394), "first one", true),
                first.commit());
        assertEquals("f.txt", first.path());
        assertEquals(Option.none(), first.previous());
        Blame.Line second = blame.at(2).get();
        assertEquals("Sam Lee", second.commit().author());
        assertFalse(second.commit().boundary());
        assertEquals(Option.of(new Blame.Previous(new Sha(FIRST), "f.txt")), second.previous());
        assertEquals(first.commit(), blame.at(3).get().commit());
        assertEquals("f.txt", blame.at(3).get().path());
        assertEquals(Option.none(), blame.at(4));
        assertEquals(Option.none(), blame.at(0));
    }

    @Test
    void aCommitNamedAgainWithAnotherPathHasThatPathForItsLines() {
        Blame blame = Blame.parse(String.join(
                        "\n",
                        details(FIRST, "1 1 1", "A", 1, "s", null),
                        "filename old.txt",
                        "\ta",
                        FIRST + " 7 2 1",
                        "previous " + SECOND + " \"caf\\303\\251.txt\"",
                        "filename \"caf\\303\\251 new.txt\"",
                        "\tb"))
                .get();

        assertEquals("old.txt", blame.at(1).get().path());
        Blame.Line moved = blame.at(2).get();
        assertEquals("café new.txt", moved.path());
        assertEquals(7, moved.original());
        assertEquals(Option.of(new Blame.Previous(new Sha(SECOND), "café.txt")), moved.previous());
    }

    @Test
    void aLineWhoseTextLooksLikeAHeaderIsStillALine() {
        Blame blame = Blame.parse(String.join(
                        "\n",
                        details(FIRST, "1 1 1", "A", 1, "s", null),
                        "filename f.txt",
                        "\t" + FIRST + " 1 1 1",
                        FIRST + " 2 2",
                        "\t",
                        ""))
                .get();

        assertEquals(2, blame.lines().size());
    }

    @Test
    void linesNotCommittedYetAreBlamedOnZeros() {
        Blame blame = Blame.parse(String.join(
                        "\n",
                        details(ZEROS, "5 1 1", "Not Committed Yet", 1795021394, "Version of g.txt from g.txt", null),
                        "filename g.txt",
                        "\te"))
                .get();

        assertFalse(blame.at(1).get().commit().committed());
        assertTrue(blame.shades().isEmpty());
    }

    @Test
    void aFileOfABranchWithoutCommitsIsOneRunNotCommittedYet() {
        Blame blame = Blame.uncommitted(3, "g.txt");

        assertEquals(3, blame.lines().size());
        assertTrue(blame.lines().forAll(line -> !line.commit().committed()));
        assertEquals(List.of(1, 2, 3), blame.lines().map(Blame.Line::original).toList());
        assertTrue(blame.startsRun(1));
        assertFalse(blame.startsRun(2));
        assertTrue(blame.shades().isEmpty());
    }

    @Test
    void whatIsNoPorcelainIsNoBlame() {
        assertTrue(Blame.parse("fatal: no such path").isEmpty());
        // A commit named for the first time without its details.
        assertTrue(Blame.parse(FIRST + " 1 1 1\n\ta\n").isEmpty());
        // A header without its line.
        assertTrue(Blame.parse(details(FIRST, "1 1 1", "A", 1, "s", null) + "\nfilename f.txt\n")
                .isEmpty());
        // A line missing in between.
        assertTrue(Blame.parse(String.join(
                        "\n",
                        details(FIRST, "1 1 1", "A", 1, "s", null),
                        "filename f.txt",
                        "\ta",
                        FIRST + " 3 3",
                        "\tc"))
                .isEmpty());
    }

    @Test
    void nothingBlamedIsAnEmptyFile() {
        assertEquals(new Blame(Vector.empty()), Blame.parse("").get());
    }

    @Test
    void aRunStartsWhereTheCommitChanges() {
        Blame blame = blame(List.of(1L, 1L, 2L, 1L));

        assertEquals(
                List.of(true, false, true, true, false), List.rangeClosed(1, 5).map(blame::startsRun));
    }

    @Test
    void commitsAreShadedByRankNewestFirstInFiveGroups() {
        // Ten commits, written at seconds 1 to 10: two in each shade, the newest in 0.
        Blame blame = blame(List.rangeClosed(1L, 10L));

        assertEquals(
                List.of(4, 4, 3, 3, 2, 2, 1, 1, 0, 0),
                blame.lines()
                        .map(line -> blame.shades().get(line.commit().sha()).get())
                        .toList());
    }

    @Test
    void fewCommitsAreSpreadOverTheShades() {
        Blame blame = blame(List.of(1L, 100L, 1L));

        assertEquals(HashMap.of(sha(1), 2, sha(100), 0), blame.shades());
    }

    /**
     * A blame of one line for each of {@code times}, each blamed on a commit written at that second, named after it.
     */
    private static Blame blame(List<Long> times) {
        return new Blame(times.map(time -> new Blame.Line(
                        new Blame.Commit(sha(time), "A", Instant.ofEpochSecond(time), "s", false),
                        1,
                        "f.txt",
                        Option.<Blame.Previous>none()))
                .toVector());
    }

    private static Sha sha(long time) {
        return new Sha("%040d".formatted(time).replace('0', 'a'));
    }

    private static String details(String sha, String numbers, String author, long time, String summary, String extra) {
        return String.join(
                "\n",
                List.of(
                                sha + " " + numbers,
                                "author " + author,
                                "author-mail <a@x>",
                                "author-time " + time,
                                "author-tz +0100",
                                "committer " + author,
                                "committer-mail <a@x>",
                                "committer-time " + time,
                                "committer-tz +0100",
                                "summary " + summary)
                        .appendAll(Option.of(extra)));
    }
}

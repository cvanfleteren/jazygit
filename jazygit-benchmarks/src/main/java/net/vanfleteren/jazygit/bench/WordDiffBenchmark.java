package net.vanfleteren.jazygit.bench;

import io.vavr.collection.List;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.WordDiff;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * How long the rich view of a diff takes before it is styled (ADR 44): parsing the diff, and comparing the words of
 * its changed lines, which both run where the diff is drawn. Each {@link Shape} is a diff of a kind that is common, or
 * that is slow.
 *
 * <p>Run with {@code java -jar jazygit-benchmarks/target/benchmarks.jar WordDiff}; add {@code -p shape=CODE} for one
 * shape, or {@code -prof gc} for what is allocated.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class WordDiffBenchmark {

    @Param
    public Shape shape;

    private String diff;
    private List<List<DiffLine>> hunks;

    @Setup
    public void setUp() {
        diff = shape.diff();
        hunks = UnifiedDiff.parse(diff).files().flatMap(FileDiff::hunks).map(Hunk::lines);
    }

    /**
     * Parsing the text of the diff into files, hunks and numbered lines.
     */
    @Benchmark
    public UnifiedDiff parse() {
        return UnifiedDiff.parse(diff);
    }

    /**
     * Comparing the words of the changed lines of the parsed diff.
     */
    @Benchmark
    public List<List<List<Segment>>> segments() {
        return WordDiff.segments(hunks);
    }

    /**
     * Both, as the rich view does before it styles the lines.
     */
    @Benchmark
    public List<List<List<Segment>>> parseAndSegments() {
        return WordDiff.segments(
                UnifiedDiff.parse(diff).files().flatMap(FileDiff::hunks).map(Hunk::lines));
    }

    /**
     * The kinds of diff measured. Each is close to the 5000 lines a diff is cut off at ({@code CliDiff.MAX_LINES}), or
     * says why not.
     */
    public enum Shape {
        /**
         * A commit to code: 100 files of 4 hunks, each with context around 3 lines changed in a word or two.
         */
        CODE {
            @Override
            String diff() {
                return files(
                        100,
                        file -> List.range(0, 4).flatMap(hunk -> {
                            int start = 1 + hunk * 40;
                            List<String> before = List.range(0, 3).map(i -> code(file, start + 3 + i, "count"));
                            List<String> after = List.range(0, 3).map(i -> code(file, start + 3 + i, "total"));
                            return List.of("@@ -%d,9 +%d,9 @@ class C%d {".formatted(start, start, file))
                                    .appendAll(context(file, start, 3))
                                    .appendAll(before.map(line -> "-" + line))
                                    .appendAll(after.map(line -> "+" + line))
                                    .appendAll(context(file, start + 6, 3));
                        }));
            }
        },
        /**
         * Thousands of long, similar lines, as in a generated or data file: the comparisons are bounded by
         * {@link WordDiff#BUDGET} only.
         */
        LONG_SIMILAR {
            @Override
            String diff() {
                String words = List.range(0, 249).map(i -> "word" + i).mkString(" ");
                return files(
                        1,
                        file -> List.of("@@ -1,2000 +1,2000 @@")
                                .appendAll(List.range(0, 2000).map(i -> "-" + words + " " + i))
                                .appendAll(List.range(0, 2000).map(i -> "+" + words + " x" + i)));
            }
        },
        /**
         * Lines that were rewritten: every pair is compared, and has too little in common to be highlighted.
         */
        REWRITTEN {
            @Override
            String diff() {
                return files(
                        1,
                        file -> List.of("@@ -1,2500 +1,2500 @@")
                                .appendAll(List.range(0, 2500).map(i -> "-" + sentence("old", i)))
                                .appendAll(List.range(0, 2500).map(i -> "+" + sentence("new", i))));
            }
        },
        /**
         * An added file: nothing to compare, only lines to walk through.
         */
        ADDED {
            @Override
            String diff() {
                return "diff --git a/New.java b/New.java\nnew file mode 100644\n--- /dev/null\n+++ b/New.java\n"
                        + "@@ -0,0 +1,5000 @@\n"
                        + List.range(0, 5000)
                                .map(i -> "+" + code(0, i, "value"))
                                .mkString("", "\n", "\n");
            }
        };

        /**
         * The text of the diff, as git prints it.
         */
        abstract String diff();

        private static String files(int count, java.util.function.IntFunction<List<String>> hunks) {
            return List.range(0, count)
                    .flatMap(file -> List.of(
                                    "diff --git a/src/F%d.java b/src/F%d.java".formatted(file, file),
                                    "index 1234567..89abcde 100644",
                                    "--- a/src/F%d.java".formatted(file),
                                    "+++ b/src/F%d.java".formatted(file))
                            .appendAll(hunks.apply(file)))
                    .mkString("", "\n", "\n");
        }

        private static List<String> context(int file, int start, int count) {
            return List.range(0, count).map(i -> " " + code(file, start + i, "count"));
        }

        private static String code(int file, int line, String name) {
            return "        int %s%d = compute(file%d, line%d) + offset * 2; // keep %d"
                    .formatted(name, line, file, line, line);
        }

        private static String sentence(String word, int line) {
            return List.range(0, 30).map(i -> word + (line * 31 + i)).mkString(" ");
        }
    }
}

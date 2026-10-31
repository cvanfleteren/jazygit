package net.vanfleteren.jazygit.ui;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.bench.WordDiffBenchmark.Shape;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.Collapsed;
import net.vanfleteren.jazygit.state.DiffLines;
import net.vanfleteren.jazygit.state.PlacedRemarks;
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
 * What the diff of a pull request with review threads costs (ADR 55, 60): placing the threads in it, drawing it, and
 * the lines of the cursor in it, which a key of the review works out again. The diff is {@link Shape#CODE}, 100 files
 * of 4 hunks; the threads are spread over its files and hunks, a third of them on several lines, and some on a whole
 * file or outdated.
 *
 * <p>Run with {@code java -jar jazygit-benchmarks/target/benchmarks.jar ReviewedDiff}; add {@code -prof gc} for what
 * is allocated.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ReviewedDiffBenchmark {

    private static final Theme.SpanStyles STYLES =
            cssClass -> Style.EMPTY.fg(Color.WHITE).bg(Color.BLACK);

    @Param({"10", "100", "1000"})
    public int threads;

    private ParsedDiff parsed;
    private ContentView.ShownRemarks remarks;
    private Anchor.OnLine first;
    private Anchor.OnLine last;

    @Setup
    public void setUp() {
        parsed = DiffText.of(Shape.CODE.diff()).parsed();
        List<ReviewThread> all = List.range(0, threads).map(ReviewedDiffBenchmark::thread);
        remarks = new ContentView.ShownRemarks(
                all, Instant.EPOCH, Option.none(), true, Collapsed.NONE, HashSet.empty(), HashSet.empty());
        first = new Anchor.OnLine("src/F99.java", Side.RIGHT, 1 + 3 * 40 + 6);
        last = new Anchor.OnLine("src/F99.java", Side.RIGHT, 1 + 3 * 40 + 8);
    }

    /**
     * Placing the threads in the diff, which drawing it and its stops both start with.
     */
    @Benchmark
    public PlacedRemarks place() {
        return PlacedRemarks.place(parsed.diff(), remarks.threads(), true);
    }

    /**
     * Drawing the diff with its threads, on the render thread.
     */
    @Benchmark
    public RichDiff.Drawn draw() {
        return RichDiff.drawn(parsed, true, true, Option.some(remarks), STYLES);
    }

    /**
     * The lines of the diff and a range of them in its last file, as a key of the review works them out.
     */
    @Benchmark
    public Option<Vector<DiffLines.Line>> lines() {
        return DiffLines.of(parsed.diff()).between(first, last);
    }

    /**
     * Thread {@code i}: on file {@code i} of the 100, in hunk {@code i / 100} of its 4, on an added line on the right
     * or a removed one on the left; every third on the two lines before it too, every 25th about its whole file and
     * every 40th outdated. Each has a reply.
     */
    private static ReviewThread thread(int i) {
        int file = i % 100;
        int start = 1 + (i / 100 % 4) * 40;
        boolean left = i % 2 == 1;
        int line = start + 3 + i % 3;
        Subject subject = i % 25 == 0 ? Subject.FILE : Subject.LINE;
        boolean outdated = i % 40 == 1;
        Option<Integer> on = subject == Subject.FILE || outdated ? Option.none() : Option.some(line);
        return new ReviewThread(
                "src/F%d.java".formatted(file),
                left ? Side.LEFT : Side.RIGHT,
                on,
                i % 3 == 0 ? on.map(number -> number - 2) : Option.none(),
                left ? Side.LEFT : Side.RIGHT,
                Option.some(line),
                false,
                outdated,
                subject,
                List.of(comment(i, 0), comment(i, 1)),
                0,
                "thread" + i,
                ReviewThread.Rights.ALL);
    }

    private static ReviewThread.Comment comment(int thread, int index) {
        return new ReviewThread.Comment(
                "someone",
                Instant.EPOCH,
                "A remark on this line, number %d of thread %d.".formatted(index, thread),
                Option.none(),
                "comment" + thread + "-" + index,
                String.valueOf(thread * 10 + index),
                CommentRights.ALL);
    }
}

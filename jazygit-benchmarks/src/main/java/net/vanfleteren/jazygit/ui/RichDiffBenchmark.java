package net.vanfleteren.jazygit.ui;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import io.vavr.collection.List;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.bench.WordDiffBenchmark.Shape;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
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
 * How long the rich view of a diff holds up the render thread (ADR 50), and what it costs where the diff is read. In
 * the package of {@link RichDiff}, which the app keeps to itself.
 *
 * <p>Run with {@code java -jar jazygit-benchmarks/target/benchmarks.jar RichDiff}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class RichDiffBenchmark {

    private static final Theme.SpanStyles STYLES =
            cssClass -> Style.EMPTY.fg(Color.WHITE).bg(Color.BLACK);

    @Param
    public Shape shape;

    private String text;
    private ParsedDiff parsed;

    @Setup
    public void setUp() {
        text = shape.diff();
        parsed = DiffText.of(text).parsed();
    }

    /**
     * Reading the diff, on the thread that ran git: parsing it and comparing its words.
     */
    @Benchmark
    public DiffText read() {
        return DiffText.of(text);
    }

    /**
     * Drawing the diff, on the render thread: styling what was parsed when it was read, in a theme that gives each
     * class a background, as a changed line without one is not coloured by its syntax (ADR 53).
     */
    @Benchmark
    public List<Line> draw() {
        return RichDiff.lines(parsed, true, STYLES);
    }
}

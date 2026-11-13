package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Repo;
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
import net.vanfleteren.jazygit.ui.files.FileTreeList;
import net.vanfleteren.jazygit.ui.files.FilesPanel;

/**
 * What all files of a large working tree cost the render thread (ADR 108): a key typed in the filter matches every
 * path and builds the tree of those that match, and a reload merges all files with the changed ones and finds the
 * directories to start collapsed. The budget of a key typed is a frame, 16 ms, at 100 000 files. In the package of
 * {@link FileTreeList}, which the app keeps to itself.
 *
 * <p>Run with {@code java -jar jazygit-benchmarks/target/benchmarks.jar AllFiles}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class AllFilesBenchmark {

    @Param({"100000"})
    public int files;

    private List<FileEntry> entries;
    private Repo repo;

    @Setup
    public void setUp() {
        // Paths like a large source tree: modules, packages and files, with a test for each class.
        List<String> paths = List.range(0, files)
                .map(i -> "module" + i % 40 + "/src/" + (i % 2 == 0 ? "main" : "test") + "/java/net/example/package"
                        + i % 250 + "/Class" + i + (i % 2 == 0 ? ".java" : "Test.java"));
        List<FileEntry> changed = paths.take(100).map(path -> new FileEntry(path, ChangeType.MODIFIED, false, true));
        repo = Repo.LOADING
                .withStatus(new Loadable.Loaded<>(
                        new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), changed)))
                .withAllFiles(new Loadable.Loaded<>(paths));
        entries = repo.workingFiles(true).toOption().get();
    }

    /**
     * A key typed in the filter: every path matched, and the tree built of those that match.
     */
    @Benchmark
    public List<FileTree<FileEntry>> filter() {
        return FileTree.of(FileTreeList.matching(entries, Option.of("Class12Test")));
    }

    /**
     * A key typed in the filter that starts words, as ADR 106 matches them.
     */
    @Benchmark
    public List<FileTree<FileEntry>> filterByWords() {
        return FileTree.of(FileTreeList.matching(entries, Option.of("CT")));
    }

    /**
     * A reload: all files merged with the changed ones.
     */
    @Benchmark
    public Loadable<List<FileEntry>> merge() {
        return repo.workingFiles(true);
    }

    /**
     * The directories to start collapsed, once all files are first read.
     */
    @Benchmark
    public Set<String> startCollapsed() {
        return FilesPanel.unchangedDirectories(entries);
    }
}

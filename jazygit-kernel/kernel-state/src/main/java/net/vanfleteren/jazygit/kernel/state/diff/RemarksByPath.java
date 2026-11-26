package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * Remark threads found by the path of their file, so that the threads of each file of a diff are found without going
 * through all of them for each (ADR 55, 171).
 *
 * @param byPath for each path, the threads on it, each with where it is among all the threads
 */
public record RemarksByPath(Map<RepoPath, List<Tuple2<RemarkThread, Integer>>> byPath) {

    /**
     * {@code threads} by the path of their file, and by the path it had before.
     */
    public static RemarksByPath of(List<RemarkThread> threads) {
        return new RemarksByPath(threads.zipWithIndex()
                .flatMap(thread -> thread._1()
                        .origin()
                        .filter(origin -> !origin.equals(thread._1().path()))
                        .toList()
                        .prepend(thread._1().path())
                        .map(path -> new Tuple2<>(path, thread)))
                .groupBy(Tuple2::_1)
                .mapValues(found -> found.map(Tuple2::_2)));
    }

    /**
     * The threads on the file with the path {@code path}, renamed from {@code origin} if it was, as
     * {@link RemarkThread#isOn} matches them, in their order.
     */
    public List<RemarkThread> on(RepoPath path, Option<RepoPath> origin) {
        return List.of(path)
                .appendAll(origin.filter(old -> !old.equals(path)))
                .flatMap(each -> byPath.get(each).getOrElse(List.empty()))
                .filter(thread -> thread._1().isOn(path, origin))
                .distinctBy(Tuple2::_2)
                .sortBy(Tuple2::_2)
                .map(Tuple2::_1);
    }
}

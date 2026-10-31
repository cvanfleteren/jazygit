package net.vanfleteren.jazygit.gh.model;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * Review threads found by the path of their file, so that the threads of each file of a diff are found without going
 * through all of them for each (ADR 55).
 *
 * @param byPath for each path, the threads on it, each with where it is among all the threads
 */
public record ThreadsByPath(Map<String, List<Tuple2<ReviewThread, Integer>>> byPath) {

    /**
     * {@code threads} by the path of their file.
     */
    public static ThreadsByPath of(List<ReviewThread> threads) {
        return new ThreadsByPath(
                threads.zipWithIndex().groupBy(thread -> thread._1().path()));
    }

    /**
     * The threads on the file with the path {@code path}, renamed from {@code origin} if it was, as
     * {@link ReviewThread#isOn} matches them, in their order.
     */
    public List<ReviewThread> on(String path, Option<String> origin) {
        Option<String> other = origin.filter(old -> !old.equals(path));
        List<Tuple2<ReviewThread, Integer>> found = List.of(path)
                .appendAll(other)
                .flatMap(each -> byPath.get(each).getOrElse(List.empty()))
                .filter(thread -> thread._1().isOn(path, origin));
        return (other.isEmpty() ? found : found.sortBy(Tuple2::_2)).map(Tuple2::_1);
    }
}

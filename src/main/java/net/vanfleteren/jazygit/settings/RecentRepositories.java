package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Try;
import java.nio.file.Path;

/**
 * The repositories opened last, by the root of their working tree, the last one opened first. They are state
 * that jazygit keeps by itself rather than settings: losing them costs nothing, so failing to read them gives
 * none, and failing to write them only says so.
 */
public interface RecentRepositories {

    /**
     * The repositories that are kept at most; the ones opened longest ago go first.
     */
    int MAX = 20;

    /**
     * Keeps nothing: there are no recent repositories, and opening one does not change that.
     */
    RecentRepositories NONE = new RecentRepositories() {
        @Override
        public List<Path> load() {
            return List.empty();
        }

        @Override
        public Try<List<Path>> opened(Path workTree) {
            return Try.success(List.empty());
        }

        @Override
        public Try<List<Path>> forget(List<Path> workTrees) {
            return Try.success(List.empty());
        }
    };

    /**
     * The recent repositories, the last one opened first; none when they cannot be read.
     */
    List<Path> load();

    /**
     * Keeps that the repository at {@code workTree} was opened just now, and gives the recent repositories then.
     */
    Try<List<Path>> opened(Path workTree);

    /**
     * {@code recent} after the repository at {@code workTree} was opened: it comes first, once, and the ones
     * opened longest ago drop off past {@link #MAX}.
     */
    static List<Path> opened(List<Path> recent, Path workTree) {
        return recent.remove(workTree).prepend(workTree).take(MAX);
    }

    /**
     * Drops {@code workTrees}, which are no repositories anymore, and gives the recent repositories then.
     */
    Try<List<Path>> forget(List<Path> workTrees);
}

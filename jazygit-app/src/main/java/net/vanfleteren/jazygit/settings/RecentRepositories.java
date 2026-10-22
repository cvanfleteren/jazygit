package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.util.function.Function;

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
        public Try<List<Path>> opened(Path worktree) {
            return Try.success(List.empty());
        }

        @Override
        public Try<List<Path>> forget(List<Path> worktrees) {
            return Try.success(List.empty());
        }
    };

    /**
     * The recent repositories, the last one opened first; none when they cannot be read.
     */
    List<Path> load();

    /**
     * Keeps that the repository at {@code worktree} was opened just now, and gives the recent repositories then.
     * A repository is kept once, by the worktree opened last.
     */
    Try<List<Path>> opened(Path worktree);

    /**
     * {@code recent} after the repository at {@code worktree} was opened: it comes first, once, and the ones
     * opened longest ago drop off past {@link #MAX}.
     */
    static List<Path> opened(List<Path> recent, Path worktree) {
        return opened(recent, worktree, directory -> Option.none());
    }

    /**
     * {@code recent} after the worktree at {@code worktree} was opened, as {@link #opened(List, Path)}; the other
     * worktrees of its repository, as {@code repository} tells which one a worktree belongs to, give way to it.
     */
    static List<Path> opened(List<Path> recent, Path worktree, Function<Path, ? extends Option<?>> repository) {
        Option<?> opened = repository.apply(worktree);
        return recent.filter(other -> !other.equals(worktree)
                        && !(opened.isDefined() && repository.apply(other).equals(opened)))
                .prepend(worktree)
                .take(MAX);
    }

    /**
     * Drops {@code worktrees}, which are no repositories anymore, and gives the recent repositories then.
     */
    Try<List<Path>> forget(List<Path> worktrees);
}

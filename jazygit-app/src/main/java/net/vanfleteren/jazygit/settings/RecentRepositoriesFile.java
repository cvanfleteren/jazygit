package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The recent repositories in the state file, which only jazygit writes. Every change reads the file again first,
 * under a lock, so the repositories another jazygit kept meanwhile stay.
 */
public final class RecentRepositoriesFile implements RecentRepositories {

    /**
     * The key of the recent repositories in the state file.
     */
    static final String KEY = "recent-repositories";

    private final Path file;
    // Which repository a worktree belongs to, so that one is kept once; none when that cannot be told.
    private final Function<Path, Option<?>> repository;

    /**
     * Recent repositories that are told apart by their worktree only.
     */
    public RecentRepositoriesFile(Path file) {
        this(file, directory -> Option.none());
    }

    /**
     * @param repository which repository the worktree at a path belongs to, so that a repository is kept once, by
     *                   the worktree opened last; none when that cannot be told
     */
    public RecentRepositoriesFile(Path file, Function<Path, Option<?>> repository) {
        this.file = file;
        this.repository = repository;
    }

    private static List<Path> paths(CommentedConfig config) {
        return config.getOptional(KEY)
                .filter(java.util.List.class::isInstance)
                .map(found -> List.ofAll((java.util.List<?>) found))
                .map(values -> values.filter(String.class::isInstance)
                        .map(String.class::cast)
                        .flatMap(value -> Try.of(() -> Path.of(value)).filter(Path::isAbsolute)))
                .map(paths -> paths.distinct().take(MAX))
                .orElse(List.empty());
    }

    @Override
    public List<Path> load() {
        return !Files.exists(file)
                ? List.empty()
                : TomlFile.read(file).map(RecentRepositoriesFile::paths).getOrElse(List.empty());
    }

    @Override
    public Try<List<Path>> opened(Path worktree) {
        return change(recent -> RecentRepositories.opened(recent, worktree, repository));
    }

    @Override
    public Try<List<Path>> forget(List<Path> worktrees) {
        return change(recent -> recent.removeAll(worktrees));
    }

    /**
     * Writes what {@code change} makes of the recent repositories in the file, keeping what else it has.
     */
    private Try<List<Path>> change(Function<List<Path>, List<Path>> change) {
        AtomicReference<List<Path>> changed = new AtomicReference<>(List.empty());
        return TomlFile.change(file, config -> {
                    changed.set(change.apply(paths(config)));
                    config.set(KEY, changed.get().map(Path::toString).toJavaList());
                })
                .map(written -> changed.get());
    }
}

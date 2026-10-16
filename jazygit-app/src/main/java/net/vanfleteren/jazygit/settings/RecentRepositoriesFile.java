package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import io.vavr.collection.List;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * The recent repositories in the state file, which only jazygit writes. Every change reads the file again first,
 * so the repositories another jazygit kept meanwhile stay; when both write at the same moment, the last one wins.
 */
public final class RecentRepositoriesFile implements RecentRepositories {

    /**
     * The key of the recent repositories in the state file.
     */
    static final String KEY = "recent-repositories";

    private final Path file;

    public RecentRepositoriesFile(Path file) {
        this.file = file;
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
    public Try<List<Path>> opened(Path workTree) {
        return change(recent -> RecentRepositories.opened(recent, workTree));
    }

    @Override
    public Try<List<Path>> forget(List<Path> workTrees) {
        return change(recent -> recent.removeAll(workTrees));
    }

    /**
     * Writes what {@code change} makes of the recent repositories in the file. A file that cannot be read is
     * written anew, as nothing in it is worth keeping.
     */
    private Try<List<Path>> change(Function<List<Path>, List<Path>> change) {
        List<Path> changed = change.apply(load());
        CommentedConfig config = TomlFile.newConfig();
        config.set(KEY, changed.map(Path::toString).toJavaList());
        return TomlFile.write(file, config).map(written -> changed);
    }
}

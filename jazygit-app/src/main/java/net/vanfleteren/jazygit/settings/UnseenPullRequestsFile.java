package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.Config;
import io.vavr.collection.List;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The unseen pull requests in the state file, which only jazygit writes, in a table per repository:
 *
 * <pre>
 * [unseen-pull-requests."owner/name"]
 * highest = 42
 * numbers = [40, 42]
 * </pre>
 *
 * Every change reads the file again first, so what another jazygit kept meanwhile stays; when both write at the
 * same moment, the last one wins.
 */
public final class UnseenPullRequestsFile implements UnseenPullRequestsStore {

    /**
     * The key of the unseen pull requests in the state file.
     */
    static final String KEY = "unseen-pull-requests";

    private static final String HIGHEST = "highest";
    private static final String NUMBERS = "numbers";

    private final Path file;

    public UnseenPullRequestsFile(Path file) {
        this.file = file;
    }

    // As a list of keys, as a repository's name may have a dot in it.
    private static List<String> path(String repository, String key) {
        return List.of(KEY, repository, key);
    }

    private static Option<UnseenPullRequests> unseen(Config config, String repository) {
        return config.getOptional(path(repository, HIGHEST).toJavaList())
                .filter(Number.class::isInstance)
                .map(highest -> new UnseenPullRequests(
                        ((Number) highest).intValue(),
                        TreeSet.ofAll(config.getOptional(
                                        path(repository, NUMBERS).toJavaList())
                                .filter(java.util.List.class::isInstance)
                                .map(found -> List.ofAll((java.util.List<?>) found)
                                        .filter(Number.class::isInstance)
                                        .map(number -> ((Number) number).intValue()))
                                .orElse(List.empty()))))
                .map(Option::of)
                .orElse(Option.none());
    }

    @Override
    public Option<UnseenPullRequests> load(String repository) {
        return !Files.exists(file)
                ? Option.none()
                : TomlFile.read(file).toOption().flatMap(config -> unseen(config, repository));
    }

    @Override
    public Try<UnseenPullRequests> save(String repository, UnseenPullRequests unseen) {
        return TomlFile.change(file, config -> {
                    config.set(path(repository, HIGHEST).toJavaList(), unseen.highest());
                    config.set(
                            path(repository, NUMBERS).toJavaList(),
                            unseen.numbers().toJavaList());
                })
                .map(written -> unseen);
    }
}

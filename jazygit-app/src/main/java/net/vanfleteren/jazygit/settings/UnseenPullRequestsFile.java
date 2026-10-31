package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.Config;
import io.vavr.collection.List;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The unseen pull requests, which only jazygit writes, in a file of each repository (ADR 63):
 *
 * <pre>
 * highest = 42
 * numbers = [40, 42]
 * </pre>
 *
 * Every change reads the file again first, so what another jazygit kept meanwhile stays; when both write at the
 * same moment, the last one wins.
 */
public final class UnseenPullRequestsFile implements UnseenPullRequestsStore {

    /**
     * The key of the unseen pull requests in the state file, where they were kept before they were kept per
     * repository (ADR 63).
     */
    static final String FORMER_KEY = "unseen-pull-requests";

    private static final String FILE = "unseen-pull-requests.toml";
    private static final String HIGHEST = "highest";
    private static final String NUMBERS = "numbers";

    private final Path repositories;

    /**
     * The unseen pull requests of each repository, in its directory in {@code repositories}.
     */
    public UnseenPullRequestsFile(Path repositories) {
        this.repositories = repositories;
    }

    private Try<Path> file(String repository) {
        return SettingsPaths.repositoryFile(repositories, repository, FILE);
    }

    private static Option<UnseenPullRequests> unseen(Config config) {
        return config.getOptional(HIGHEST)
                .filter(Number.class::isInstance)
                .map(highest -> new UnseenPullRequests(
                        ((Number) highest).intValue(),
                        TreeSet.ofAll(config.getOptional(NUMBERS)
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
        return file(repository)
                .toOption()
                .filter(Files::exists)
                .flatMap(file -> TomlFile.read(file).toOption())
                .flatMap(UnseenPullRequestsFile::unseen);
    }

    @Override
    public Try<UnseenPullRequests> save(String repository, UnseenPullRequests unseen) {
        return file(repository)
                .flatMap(file -> TomlFile.change(file, config -> {
                    config.set(HIGHEST, unseen.highest());
                    config.set(NUMBERS, unseen.numbers().toJavaList());
                    return unseen;
                }));
    }
}

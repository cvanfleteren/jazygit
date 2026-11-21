package net.vanfleteren.jazygit.features.github.state;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import net.vanfleteren.jazygit.kernel.settings.RepositoryTable;

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
    public static final String FORMER_KEY = "unseen-pull-requests";

    private static final String FILE = "unseen-pull-requests.toml";
    private static final String HIGHEST = "highest";
    private static final String NUMBERS = "numbers";

    private final RepositoryTable<UnseenPullRequests> table;

    /**
     * The unseen pull requests of each repository, in its directory in {@code repositories}.
     */
    public UnseenPullRequestsFile(Path repositories) {
        this.table = new RepositoryTable<>(repositories, FILE, new RepositoryTable.Codec<>() {
            @Override
            public Option<UnseenPullRequests> decode(Map<String, Object> read) {
                return unseen(read);
            }

            @Override
            public Map<String, Object> encode(UnseenPullRequests unseen) {
                return LinkedHashMap.of(
                        HIGHEST, unseen.highest(), NUMBERS, unseen.numbers().toList());
            }
        });
    }

    private static Option<UnseenPullRequests> unseen(Map<String, Object> read) {
        return read.get(HIGHEST)
                .filter(Number.class::isInstance)
                .map(highest -> new UnseenPullRequests(
                        ((Number) highest).intValue(),
                        TreeSet.ofAll(read.get(NUMBERS)
                                .filter(List.class::isInstance)
                                .map(found -> ((List<?>) found)
                                        .filter(Number.class::isInstance)
                                        .map(number -> ((Number) number).intValue()))
                                .getOrElse(List.empty()))));
    }

    @Override
    public Option<UnseenPullRequests> load(String repository) {
        return table.load(repository);
    }

    @Override
    public Try<UnseenPullRequests> save(String repository, UnseenPullRequests unseen) {
        return table.change(repository, ignored -> unseen, unseen);
    }
}

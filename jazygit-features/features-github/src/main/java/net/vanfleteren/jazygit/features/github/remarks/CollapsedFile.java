package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.kernel.settings.RepositoryTable;

/**
 * The review threads and comments collapsed or opened against their default, in a file of each repository (ADR 63): a
 * table per pull request, with when one was last flipped, and a sub-table with whether each id flipped was collapsed by
 * default then:
 *
 * <pre>
 * [42]
 * touched = 2026-10-31T10:00:00Z
 *
 * [42.flipped]
 * PRRT_kwDO = true
 * </pre>
 *
 * Every change reads the file again first, under a lock, so what another jazygit kept meanwhile stays.
 */
public final class CollapsedFile implements CollapsedStore {

    private static final String FILE = "collapsed.toml";
    private static final String TOUCHED = "touched";
    private static final String FLIPPED = "flipped";

    private final RepositoryTable<Map<String, Object>> table;

    /**
     * What is flipped of the pull requests of each repository, in its directory in {@code repositories}.
     */
    public CollapsedFile(Path repositories) {
        // The file's tables as they are, one per pull request: one that cannot be read is dropped as it ages.
        this.table = new RepositoryTable<>(repositories, FILE, new RepositoryTable.Codec<>() {
            @Override
            public Option<Map<String, Object>> decode(Map<String, Object> read) {
                return Option.of(read);
            }

            @Override
            public Map<String, Object> encode(Map<String, Object> kept) {
                return kept;
            }
        });
    }

    private static Map<String, Boolean> flipped(Map<String, Object> pullRequests, String number) {
        return table(pullRequests, number)
                .flatMap(pullRequest -> table(pullRequest, FLIPPED))
                .map(found -> found.filterValues(Boolean.class::isInstance).mapValues(Boolean.class::cast))
                .<Map<String, Boolean>>map(found -> HashMap.ofEntries(found))
                .getOrElse(HashMap.empty());
    }

    @SuppressWarnings("unchecked") // A table of the file is keyed by text.
    private static Option<Map<String, Object>> table(Map<String, Object> in, String key) {
        return in.get(key).filter(Map.class::isInstance).map(found -> (Map<String, Object>) found);
    }

    private static Option<Instant> touched(Map<String, Object> pullRequests, String number) {
        return table(pullRequests, number)
                .flatMap(pullRequest -> pullRequest.get(TOUCHED))
                .flatMap(at -> at instanceof Instant instant
                        ? Option.of(instant)
                        : Try.of(() -> Instant.parse(String.valueOf(at))).toOption());
    }

    @Override
    public Map<String, Boolean> load(String repository, int number) {
        return table.load(repository)
                .map(pullRequests -> flipped(pullRequests, String.valueOf(number)))
                .getOrElse(HashMap.empty());
    }

    @Override
    public Try<Map<String, Boolean>> save(String repository, int number, Map<String, Boolean> flipped, Instant at) {
        Instant oldest = at.minus(KEPT);
        String key = String.valueOf(number);
        return table.change(
                        repository,
                        pullRequests -> {
                            // A pull request without a time it was flipped at is as old as can be.
                            Map<String, Object> kept = pullRequests.filterKeys(each -> !each.equals(key)
                                    && touched(pullRequests, each).exists(touched -> !touched.isBefore(oldest)));
                            return flipped.isEmpty()
                                    ? kept
                                    : kept.put(
                                            key,
                                            LinkedHashMap.<String, Object>of(
                                                    TOUCHED,
                                                    at,
                                                    FLIPPED,
                                                    LinkedHashMap.ofEntries(flipped.toList()
                                                            .map(each -> Tuple.of(each._1(), (Object) each._2())))));
                        },
                        LinkedHashMap.empty())
                .map(ignored -> flipped);
    }
}

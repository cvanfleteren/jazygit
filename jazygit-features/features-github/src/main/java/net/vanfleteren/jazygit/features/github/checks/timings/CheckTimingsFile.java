package net.vanfleteren.jazygit.features.github.checks.timings;

import io.vavr.Tuple;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.kernel.settings.RepositoryTable;

/**
 * How long the checks took, in a file of each repository, as it changes whenever checks are done (ADR 32, 63): when
 * its checks were last topped up from GitHub, and a table with the runs of each check, so no check can clash with
 * {@code topped-up}:
 *
 * <pre>
 * topped-up = 2026-10-25T10:00:00Z
 *
 * [checks]
 * "Build/acceptance" = [{id = 121, duration = 192, finished = 254, event = "pull_request", at = 2026-10-24T22:22:37Z}]
 * </pre>
 *
 * Durations are in seconds. Every change reads the file again first, under a lock, so what another jazygit kept
 * meanwhile stays.
 */
public final class CheckTimingsFile implements CheckTimingsStore {

    private static final String FILE = "check-timings.toml";
    private static final String TOPPED_UP = "topped-up";
    private static final String CHECKS = "checks";

    private static final String ID = "id";
    private static final String DURATION = "duration";
    private static final String FINISHED = "finished";
    private static final String EVENT = "event";
    private static final String AT = "at";

    private final RepositoryTable<Kept> table;

    /**
     * How long the checks of each repository took, in its directory in {@code repositories}.
     */
    public CheckTimingsFile(Path repositories) {
        this.table = new RepositoryTable<>(repositories, FILE, new RepositoryTable.Codec<>() {
            @Override
            public Option<Kept> decode(Map<String, Object> read) {
                return Option.of(kept(read));
            }

            @Override
            public Map<String, Object> encode(Kept kept) {
                return written(kept);
            }
        });
    }

    private static Kept kept(Map<String, Object> read) {
        return new Kept(runs(read), read.get(TOPPED_UP).flatMap(CheckTimingsFile::instant));
    }

    private static List<Run> runs(Map<String, Object> read) {
        return read.get(CHECKS)
                .filter(Map.class::isInstance)
                .map(found -> ((Map<?, ?>) found)
                        .toList()
                        .flatMap(entry -> entry._2() instanceof List<?> runs
                                ? runs.flatMap(run -> run(String.valueOf(entry._1()), run))
                                : List.empty()))
                .getOrElse(List.empty());
    }

    @SuppressWarnings("unchecked") // A table of the file is keyed by text.
    private static Option<Run> run(String check, Object run) {
        return run instanceof Map<?, ?> table
                ? Try.of(() -> {
                            Map<String, Object> fields = (Map<String, Object>) table;
                            return new Run(
                                    check,
                                    number(fields, ID),
                                    number(fields, DURATION),
                                    number(fields, FINISHED),
                                    fields.get(EVENT).map(String::valueOf).getOrElse(""),
                                    instant(fields.get(AT).get()).get());
                        })
                        .toOption()
                : Option.none();
    }

    private static long number(Map<String, Object> fields, String key) {
        return ((Number) fields.get(key).get()).longValue();
    }

    private static Option<Instant> instant(Object at) {
        return at instanceof Instant instant
                ? Option.of(instant)
                : Try.of(() -> Instant.parse(String.valueOf(at))).toOption();
    }

    private static Map<String, Object> written(Kept kept) {
        Map<String, Object> checks = LinkedHashMap.ofEntries(kept.runs()
                .groupBy(Run::check)
                .toList()
                .map(check -> Tuple.of(check._1(), (Object) check._2().map(CheckTimingsFile::fields))));
        return kept.toppedUp()
                .<Map<String, Object>>map(at -> LinkedHashMap.of(TOPPED_UP, at))
                .getOrElse(LinkedHashMap::empty)
                .put(CHECKS, checks);
    }

    private static Map<String, Object> fields(Run run) {
        return LinkedHashMap.<String, Object>empty()
                .put(ID, run.id())
                .put(DURATION, run.duration())
                .put(FINISHED, run.finished())
                .put(EVENT, run.event())
                .put(AT, run.at());
    }

    @Override
    public Option<Kept> load(RepoSlug repository) {
        return table.load(repository.value()).filter(kept -> !kept.equals(Kept.EMPTY));
    }

    @Override
    public Try<Kept> change(RepoSlug repository, UnaryOperator<Kept> change) {
        return table.change(repository.value(), change, Kept.EMPTY);
    }
}

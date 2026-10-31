package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.function.UnaryOperator;

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

    private final Path repositories;

    /**
     * How long the checks of each repository took, in its directory in {@code repositories}.
     */
    public CheckTimingsFile(Path repositories) {
        this.repositories = repositories;
    }

    private Try<Path> file(String repository) {
        return SettingsPaths.repositoryFile(repositories, repository, FILE);
    }

    private static Kept kept(Config config) {
        return new Kept(
                runs(config),
                Option.ofOptional(config.getOptional(TOPPED_UP))
                        .flatMap(at -> Try.of(() -> instant(at)).toOption()));
    }

    private static List<Run> runs(Config config) {
        return config.getOptional(CHECKS)
                .filter(Config.class::isInstance)
                .map(found -> List.ofAll(((Config) found).entrySet())
                        .flatMap(entry -> entry.getValue() instanceof java.util.List<?> runs
                                ? List.ofAll(runs).flatMap(run -> run(entry.getKey(), run))
                                : List.empty()))
                .orElse(List.empty());
    }

    private static Option<Run> run(String check, Object run) {
        return run instanceof Config fields
                ? Try.of(() -> new Run(
                                check,
                                fields.<Number>get(ID).longValue(),
                                fields.<Number>get(DURATION).longValue(),
                                fields.<Number>get(FINISHED).longValue(),
                                fields.<String>getOrElse(EVENT, ""),
                                instant(fields.get(AT))))
                        .toOption()
                : Option.none();
    }

    private static Instant instant(Object at) {
        return at instanceof TemporalAccessor temporal ? Instant.from(temporal) : Instant.parse((String) at);
    }

    private static void write(CommentedConfig config, Kept kept) {
        config.clear();
        kept.toppedUp().forEach(at -> config.set(TOPPED_UP, OffsetDateTime.ofInstant(at, ZoneOffset.UTC)));
        config.set(CHECKS, checks(config, kept.runs()));
    }

    private static Config checks(CommentedConfig parent, List<Run> runs) {
        Config table = parent.createSubConfig();
        runs.groupBy(Run::check)
                // As a list of keys, as the name of a check may have a dot in it.
                .forEach((check, ofCheck) -> table.set(
                        List.of(check).toJavaList(),
                        ofCheck.map(run -> fields(parent, run)).toJavaList()));
        return table;
    }

    private static Config fields(CommentedConfig parent, Run run) {
        Config fields = parent.createSubConfig();
        fields.set(ID, run.id());
        fields.set(DURATION, run.duration());
        fields.set(FINISHED, run.finished());
        fields.set(EVENT, run.event());
        fields.set(AT, OffsetDateTime.ofInstant(run.at(), ZoneOffset.UTC));
        return fields;
    }

    @Override
    public Option<Kept> load(String repository) {
        return file(repository)
                .toOption()
                .filter(Files::exists)
                .flatMap(file -> TomlFile.read(file).toOption())
                .map(CheckTimingsFile::kept)
                .filter(kept -> !kept.equals(Kept.EMPTY));
    }

    @Override
    public Try<Kept> change(String repository, UnaryOperator<Kept> change) {
        return file(repository)
                .flatMap(file -> TomlFile.change(file, config -> {
                    Kept changed = change.apply(kept(config));
                    write(config, changed);
                    return changed;
                }));
    }
}

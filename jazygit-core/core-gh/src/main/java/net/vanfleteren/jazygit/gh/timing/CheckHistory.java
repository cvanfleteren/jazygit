package net.vanfleteren.jazygit.gh.timing;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.function.Function;

/**
 * How long the checks of one repository took, as learned from their runs that passed (ADR 32).
 *
 * @param samples the runs of each check, by {@link CheckSample#key}, the newest first
 */
public record CheckHistory(Map<String, List<CheckSample>> samples) {

    public static final CheckHistory EMPTY = new CheckHistory(HashMap.empty());

    /**
     * This history learning {@code added} at {@code now}: a run already known is not learned twice, a check keeps its
     * {@link CheckTimings#SAMPLES} newest runs, and one whose newest run is older than {@link CheckTimings#FORGET_AFTER}
     * is forgotten.
     */
    public CheckHistory with(Iterable<CheckSample> added, Instant now) {
        Instant forgotten = now.minus(CheckTimings.FORGET_AFTER);
        return new CheckHistory(List.ofAll(samples.values().flatMap(Function.identity()))
                .appendAll(added)
                .distinctBy(CheckSample::id)
                .groupBy(CheckSample::check)
                .mapValues(runs -> runs.sorted(
                                Comparator.comparing(CheckSample::at).reversed())
                        .take(CheckTimings.SAMPLES))
                .filterValues(runs -> runs.head().at().isAfter(forgotten)));
    }

    /**
     * Of {@code runs}, those not learned yet.
     */
    public List<CheckSample> unknown(List<CheckSample> runs) {
        return runs.filter(run -> !samples.get(run.check()).exists(known -> known.exists(k -> k.id() == run.id())));
    }

    /**
     * How long {@code check} usually runs; none with fewer than {@link CheckTimings#MIN_SAMPLES} runs of it.
     */
    public Option<Duration> duration(String check) {
        return median(check, CheckSample::duration);
    }

    /**
     * How long after its workflow run was created {@code check} is usually done; none with fewer than
     * {@link CheckTimings#MIN_SAMPLES} runs of it.
     */
    public Option<Duration> finished(String check) {
        return median(check, CheckSample::finished);
    }

    /**
     * The checks of {@code workflow} that its runs started by {@code event} have, by {@link CheckSample#key}: those
     * with at least {@link CheckTimings#MIN_SAMPLES} runs started by it (ADR 36).
     */
    public List<String> checksOf(String workflow, String event) {
        String prefix = CheckSample.key(workflow, "");
        return List.ofAll(samples.filter((check, runs) -> check.startsWith(prefix)
                                && runs.count(run -> run.event().equals(event)) >= CheckTimings.MIN_SAMPLES)
                        .keySet())
                .sorted();
    }

    private Option<Duration> median(String check, Function<CheckSample, Duration> of) {
        return samples.get(check)
                .filter(runs -> runs.size() >= CheckTimings.MIN_SAMPLES)
                .map(runs -> {
                    List<Duration> sorted = runs.map(of).sorted();
                    int middle = sorted.size() / 2;
                    return sorted.size() % 2 == 1
                            ? sorted.get(middle)
                            : sorted.get(middle - 1).plus(sorted.get(middle)).dividedBy(2);
                });
    }
}

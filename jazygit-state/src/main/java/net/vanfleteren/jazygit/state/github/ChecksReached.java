package net.vanfleteren.jazygit.state.github;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckProgress;

/**
 * How far the checks of the attempts {@code runs} of workflow runs were shown to be, so a bar of how far they are
 * together does not go back when they are expected later than before (ADR 89).
 *
 * @param runs     the id of each workflow run, and its attempt
 * @param progress how far they were shown to be
 */
public record ChecksReached(Set<Tuple2<Long, Integer>> runs, double progress) {

    /**
     * How far the checks {@code all} are together at {@code now}: at least as far as they were {@code reached} to be,
     * while they are the same checks, of the same attempts of the same workflow runs. A workflow run again, or the
     * checks of another commit, start from 0.
     */
    public static double progress(
            Option<ChecksReached> reached, List<Check> all, Option<CheckTimes> times, Instant now) {
        double progress = CheckProgress.of(all, CheckTimes.eta(times, all), now);
        return reached.filter(before -> before.runs().equals(runs(all)))
                .map(before -> Math.max(before.progress(), progress))
                .getOrElse(progress);
    }

    /**
     * What to keep once the checks {@code all} were shown {@code shown} far: nothing once they are done, as they are
     * then shown full anyway, nor when they are only commit statuses, which no workflow run tells apart from those of
     * another commit, and which are counted, so do not go back.
     */
    public static Option<ChecksReached> keeping(List<Check> all, double shown) {
        Set<Tuple2<Long, Integer>> runs = runs(all);
        return Option.when(shown < 1 && !runs.isEmpty(), () -> new ChecksReached(runs, shown));
    }

    /**
     * The attempts of the workflow runs that the checks {@code all} are of.
     */
    static Set<Tuple2<Long, Integer>> runs(List<Check> all) {
        return all.flatMap(Check::run)
                .map(run -> Tuple.of(run.runId(), run.runAttempt()))
                .toSet();
    }
}

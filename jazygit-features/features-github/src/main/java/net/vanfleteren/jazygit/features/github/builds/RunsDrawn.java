package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import lombok.With;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;

/**
 * The runs of one workflow as the content panel draws them (ADR 131, 212): what they show is all that is given here,
 * not which tab, branch or filter they are of.
 *
 * @param workflow    the name of the workflow
 * @param builds      its runs, the newest first, once they are read
 * @param of          how many of its runs are read, of which {@code builds} show
 * @param everyBranch whether the runs are of every branch, so each says what it ran for and those of {@code mine} are
 *                    marked; else of one branch, of the event of the newest
 * @param mine        the commits whose runs are marked among those of every branch
 * @param baseline    how long its runs that passed took on every branch, to hold those of one branch against
 * @param needs       of the runs whose jobs show on one time line, by the id of the run, which job needs which
 * @param picked      the run whose jobs show on one time line, when it is among {@code builds}; else the last that
 *                    is done (ADR 133)
 * @param chart       how many of the newest runs the chart shows, at most; none for as many as fit. The median line
 *                    and the scale stay those of every run (ADR 212)
 */
@With
public record RunsDrawn(
        String workflow,
        Loadable<List<Build>> builds,
        int of,
        boolean everyBranch,
        Set<Sha> mine,
        Option<RunsDrawn.Baseline> baseline,
        Map<Long, Map<String, Set<String>>> needs,
        Option<Long> picked,
        Option<Integer> chart) {

    /**
     * The run whose jobs show on one time line: the one picked, else the last that is done (ADR 131, 133); none while
     * the runs are not read, or none of them is done.
     */
    public Option<Build> onTimeLine() {
        return builds.toOption()
                .flatMap(read -> picked.flatMap(run -> read.find(build -> build.runId() == run))
                        .orElse(() -> lastDoneOf(read)));
    }

    /**
     * The last of {@code builds}, the newest first, that is done and ran a job: the one whose jobs show on one time
     * line when none is picked (ADR 131).
     */
    public static Option<Build> lastDoneOf(List<Build> builds) {
        return builds.find(build -> build.duration().isDefined() && !build.ran().isEmpty());
    }

    /**
     * The middle of {@code durations}; none without any.
     */
    public static Option<Duration> median(List<Duration> durations) {
        List<Duration> sorted = durations.sorted();
        int size = sorted.size();
        return Option.when(
                size > 0,
                () -> size % 2 == 1
                        ? sorted.get(size / 2)
                        : sorted.get(size / 2 - 1).plus(sorted.get(size / 2)).dividedBy(2));
    }

    /**
     * How long the runs of a workflow that passed took on every branch.
     *
     * @param median in the middle
     * @param runs   how many runs that passed there are
     */
    public record Baseline(Duration median, int runs) {}
}

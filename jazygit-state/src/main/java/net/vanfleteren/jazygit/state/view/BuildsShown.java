package net.vanfleteren.jazygit.state.view;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;

/**
 * What the builds of a branch show, as the content panel draws it (ADR 131, 133).
 *
 * @param tabs        the workflows of the tabs, in their order
 * @param lasts       how the last build of each tab ended, as far as that is read
 * @param index       the index of the tab shown
 * @param allBranches whether the tab shows the runs of its workflow on every branch, not only those of the branch
 * @param builds      the builds of the tab shown, the newest first, as many as show, once they are read
 * @param of          how many builds of the tab are read, of which those show
 * @param mine        the commits of the branch, whose runs are marked among those of every branch
 * @param baseline    of the branch's builds of the tab: how long the runs of its workflow that passed took on every
 *                    branch, in the middle, and how many of those there are; none until those are read
 * @param needs       of the builds whose jobs show on one time line, by the id of their run, which job needs which
 * @param picked      the run of the build whose jobs show on one time line, when it is among those shown; else the
 *                    last that is done shows (ADR 133)
 */
public record BuildsShown(
        List<Pipeline> tabs,
        List<Option<Build.Outcome>> lasts,
        int index,
        boolean allBranches,
        @With Loadable<List<Build>> builds,
        int of,
        Set<String> mine,
        Option<Baseline> baseline,
        Map<Long, Map<String, Set<String>>> needs,
        Option<Long> picked) {

    /**
     * The workflow of the tab shown.
     */
    public Pipeline shown() {
        return tabs.get(index);
    }

    /**
     * The build whose jobs show on one time line: the one picked, else the last that is done (ADR 131, 133); none
     * while the builds are not read, or none of them is done.
     */
    public Option<Build> onTimeLine() {
        return builds.toOption()
                .flatMap(read -> picked.flatMap(run -> read.find(build -> build.runId() == run))
                        .orElse(() -> BuildsView.lastDoneOf(read)));
    }

    /**
     * How long the runs of a workflow that passed took on every branch.
     *
     * @param median in the middle
     * @param runs   how many runs that passed there are
     */
    public record Baseline(Duration median, int runs) {}
}

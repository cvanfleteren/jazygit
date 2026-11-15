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
 * What the builds of a branch show, as the content panel draws it (ADR 131).
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
        Map<Long, Map<String, Set<String>>> needs) {

    /**
     * The workflow of the tab shown.
     */
    public Pipeline shown() {
        return tabs.get(index);
    }

    /**
     * How long the runs of a workflow that passed took on every branch.
     *
     * @param median in the middle
     * @param runs   how many runs that passed there are
     */
    public record Baseline(Duration median, int runs) {}
}

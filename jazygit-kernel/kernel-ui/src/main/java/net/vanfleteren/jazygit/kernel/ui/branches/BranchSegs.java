package net.vanfleteren.jazygit.kernel.ui.branches;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The parts of a row the lists of the branches pane have in common, from what git says: how far a branch is from its
 * upstream and its base, its parent, whether it is checked out elsewhere, and the spinner of a busy one. What the
 * features show of it comes from its {@link BranchBadges} (ADR 167). The symbols are one column wide (ADR 45).
 */
final class BranchSegs {

    private BranchSegs() {}

    /**
     * What a busy branch shows on the left of its spinner: what is being done to it, for the operations that
     * talk to its remote.
     */
    static Option<String> busyLabel(Operation operation) {
        return operation.busyKey().map(Messages::get);
    }

    /**
     * The spinner of a branch that {@code operation} runs on, preceded by its label if it has one.
     */
    private static String spinner(Operation operation, Instant now) {
        return busyLabel(operation).map(label -> label + " ").getOrElse("") + TimeText.spinnerFrame(now);
    }

    /**
     * The spinner of each busy branch of {@code model}, all at {@code frame}.
     */
    static Function<BranchRef, Option<String>> spinners(Model model, Instant frame) {
        Map<BranchRef, Operation> busy = model.jobs().busy();
        return ref -> busy.get(ref).map(operation -> spinner(operation, frame));
    }

    /**
     * The spinner text of a busy branch, with a leading space; nothing for one that is not busy.
     */
    static Option<LoadableList.Seg> spinnerSeg(Option<String> spinner) {
        return spinner.map(text -> LoadableList.Seg.styled(" " + text, Theme.Classes.SPINNER));
    }

    /**
     * That the branch is checked out in another worktree, dimmed and with a leading space; nothing for one that
     * is not.
     */
    static List<LoadableList.Seg> worktree(Branch branch) {
        return Option.when(
                        branch.checkedOutElsewhere(),
                        () -> LoadableList.Seg.styled(" " + Messages.get("branches.worktree.short"), Theme.Classes.DIM))
                .toList();
    }

    /**
     * The commits not yet pushed in green and not yet pulled in red, as an arrow and a count each,
     * with a leading space; "gone" when its upstream was deleted on the remote; nothing for a branch that is
     * in sync, or that has no upstream.
     */
    static List<LoadableList.Seg> sync(Branch branch) {
        return List.of(
                        Option.of(branch.ahead())
                                .filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↑" + n, Theme.Classes.AHEAD)),
                        Option.of(branch.behind())
                                .filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↓" + n, Theme.Classes.BEHIND)),
                        branch.goneUpstream()
                                .map(upstream -> LoadableList.Seg.styled(
                                        " " + Messages.get("branches.gone"), Theme.Classes.GONE)))
                .flatMap(o -> o);
    }

    /**
     * How far {@code branch} is from its base, dimmed and each with a leading space: the commits it has that the base
     * does not, {@code ⇡2}, then those of the base it does not have, {@code ⇣58}; nothing for none, for a branch
     * without a base, or for one whose base is its upstream, which {@link #sync} already counts against (ADR 46).
     */
    static List<LoadableList.Seg> baseDistance(Branch branch, Option<BaseDistance> distance) {
        return distance.filter(read ->
                        !branch.upstream().exists(upstream -> upstream.branch().equals(read.base())))
                .toList()
                .flatMap(read -> List.of(Tuple.of("⇡", read.ahead()), Tuple.of("⇣", read.behind())))
                .filter(count -> count._2 > 0)
                .map(count -> LoadableList.Seg.styled(" " + count._1 + count._2, Theme.Classes.DIM));
    }

    /**
     * The parent of a branch, {@code on feat/a}, dimmed and with a leading space, cut first when its line does not
     * fit, so that what comes after it stays in view; nothing for none (ADR 72).
     */
    static List<LoadableList.Seg> parent(Option<BranchName> parent) {
        return parent.map(name -> LoadableList.Seg.styled(
                                " " + Messages.get("branches.on", name.value()), Theme.Classes.DIM)
                        .shrinking())
                .toList();
    }

    /**
     * {@code segs}, each with a leading space, set apart from what comes before them by one more space; nothing for
     * no segs (ADR 45).
     */
    static List<LoadableList.Seg> group(List<LoadableList.Seg> segs) {
        return segs.isEmpty() ? segs : segs.update(0, first -> first.prefixed(" "));
    }

    /**
     * The time a list shows of a branch (ADR 124): its age, counted from {@code since}, and the text of its badges that
     * changes with the time, {@code badges}.
     */
    static ShownTime shownTime(Instant since, BranchBadges.Shown badges) {
        return ShownTime.reading(now -> TimeText.age(Duration.between(since, now)) + " "
                + badges.timed().apply(now));
    }
}

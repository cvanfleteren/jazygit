package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.*;

/**
 * The parts of a row the lists of the branches pane have in common: how far a branch is from its upstream, how it
 * is doing on GitHub, and the spinner of a busy one.
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
                        () -> LoadableList.Seg.styled(" " + Messages.get("branches.worktree"), Theme.Classes.DIM))
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
     * The pull request of a branch, with a leading space: its number (dimmed for a draft), where it sits in its
     * stack, then how its CI checks are doing and where its review stands, each as a coloured symbol; nothing for a
     * branch without one.
     */
    static List<LoadableList.Seg> pullRequest(Option<PullRequest> pullRequest) {
        return pullRequest(pullRequest, Option.none());
    }

    /**
     * The pull request of a branch, as {@link #pullRequest(Option)}, but with how {@code merge} goes instead of its
     * checks and review while it goes, or went (ADR 23).
     */
    static List<LoadableList.Seg> pullRequest(Option<PullRequest> pullRequest, Option<MergeStatus> merge) {
        return pullRequest
                .toList()
                .flatMap(pr -> PullRequestSymbols.state(pr.stack(), pr.checks(), pr.review(), merge)
                        .map(BranchSegs::symbol)
                        .prepend(LoadableList.Seg.styled(
                                " #" + pr.number(), pr.draft() ? Theme.Classes.DIM : Theme.Classes.PULL_REQUEST)));
    }

    /**
     * How a branch is doing on GitHub, with a leading space: its pull request, if it has one, else how the CI
     * checks on its tip are doing; nothing for a branch with neither.
     */
    static List<LoadableList.Seg> gitHub(Option<PullRequest> pullRequest, Option<Checks> checks) {
        return gitHub(pullRequest, Option.none(), checks);
    }

    /**
     * How a branch is doing on GitHub, as {@link #gitHub(Option, Option)}, with how the merge of its pull request
     * goes, if there is one.
     */
    static List<LoadableList.Seg> gitHub(
            Option<PullRequest> pullRequest, Option<MergeStatus> merge, Option<Checks> checks) {
        return pullRequest.isDefined()
                ? pullRequest(pullRequest, merge)
                : checks.flatMap(PullRequestSymbols::checks)
                        .map(BranchSegs::symbol)
                        .toList();
    }

    private static LoadableList.Seg symbol(PullRequestSymbols.Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }
}

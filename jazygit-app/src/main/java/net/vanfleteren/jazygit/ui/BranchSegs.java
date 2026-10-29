package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.*;

/**
 * The parts of a row the lists of the branches pane have in common: how far a branch is from its upstream, how it
 * is doing on GitHub, and the spinner of a busy one. The symbols are one column wide (ADR 45).
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
     * How many commits of its base a branch does not have, dimmed and with a leading space: {@code ⇣58}; nothing for
     * none, or for a branch without a base (ADR 46).
     */
    static List<LoadableList.Seg> behind(Option<Integer> behind) {
        return behind.filter(n -> n > 0)
                .map(n -> LoadableList.Seg.styled(" ⇣" + n, Theme.Classes.DIM))
                .toList();
    }

    /**
     * {@code segs}, each with a leading space, set apart from what comes before them by one more space; nothing for
     * no segs (ADR 45).
     */
    static List<LoadableList.Seg> group(List<LoadableList.Seg> segs) {
        return segs.isEmpty()
                ? segs
                : segs.update(0, first -> new LoadableList.Seg(" " + first.text(), first.cssClass()));
    }

    /**
     * The pull request of a branch, with a leading space: how its CI checks are doing, where those of a branch
     * without one show too, then its number (dimmed for a draft), then apart where its review stands, then apart
     * where it sits in its stack; nothing for a branch without one (ADR 45).
     */
    static List<LoadableList.Seg> pullRequest(Option<PullRequest> pullRequest) {
        return pullRequest(pullRequest, Option.none());
    }

    /**
     * The pull request of a branch, as {@link #pullRequest(Option)}, but with how {@code merge} goes instead of its
     * checks and review while it goes, or went (ADR 24).
     */
    static List<LoadableList.Seg> pullRequest(Option<PullRequest> pullRequest, Option<MergeStatus> merge) {
        return pullRequest(pullRequest, merge, false, Option.none());
    }

    /**
     * The pull request of a branch, as {@link #pullRequest(Option, Option)}, with its checks waiting to start while
     * they run and {@code waiting} (ADR 35), and {@code after} after their symbol.
     */
    private static List<LoadableList.Seg> pullRequest(
            Option<PullRequest> pullRequest, Option<MergeStatus> merge, boolean waiting, Option<String> after) {
        return pullRequest
                .toList()
                .flatMap(pr -> merge.fold(
                                () -> checks(pr.checks(), waiting, after),
                                status -> List.of(symbol(PullRequestSymbols.merge(status))))
                        .append(LoadableList.Seg.styled(
                                " #" + pr.number(), pr.draft() ? Theme.Classes.DIM : Theme.Classes.PULL_REQUEST))
                        .appendAll(group(
                                merge.isDefined()
                                        ? List.empty()
                                        : PullRequestSymbols.review(pr.review())
                                                .map(BranchSegs::symbol)
                                                .toList()))
                        .appendAll(group(pr.stack()
                                .map(PullRequestSymbols::stack)
                                .map(BranchSegs::symbol)
                                .toList())));
    }

    /**
     * How checks that are {@code state} are doing, waiting to start while they run and {@code waiting}, with
     * {@code after} after their symbol, dimmed, while they run.
     */
    private static List<LoadableList.Seg> checks(Checks state, boolean waiting, Option<String> after) {
        return PullRequestSymbols.checks(state, waiting)
                .map(BranchSegs::symbol)
                .toList()
                .appendAll(after.filter(text -> state == Checks.PENDING)
                        .map(text -> LoadableList.Seg.styled(" " + text, Theme.Classes.DIM)));
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
        return gitHub(pullRequest, merge, checks, Option.none());
    }

    /**
     * How a branch is doing on GitHub, as {@link #gitHub(Option, Option, Option)}, with when its checks are expected
     * to be done, {@code eta}, after their symbol while they run (ADR 32).
     */
    static List<LoadableList.Seg> gitHub(
            Option<PullRequest> pullRequest, Option<MergeStatus> merge, Option<Checks> checks, Option<String> eta) {
        return gitHub(pullRequest, merge, checks, false, eta);
    }

    /**
     * How a branch is doing on GitHub, as {@link #gitHub(Option, Option, Option, Option)}, but while its checks all
     * wait to start, {@code waiting}, waiting and then how long they have at {@code now}: {@code ◌ 3m}; only
     * {@code ◌} for a re-run (ADR 35).
     */
    static List<LoadableList.Seg> gitHub(
            Option<PullRequest> pullRequest,
            Option<MergeStatus> merge,
            Option<Checks> checks,
            Option<Waiting> waiting,
            Option<String> eta,
            Instant now) {
        return gitHub(
                pullRequest,
                merge,
                checks,
                waiting.isDefined(),
                waiting.map(known -> known.since().map(since -> CheckTimesText.waited(Duration.between(since, now))))
                        .getOrElse(eta));
    }

    /**
     * How a branch is doing on GitHub, with its checks waiting to start while they run and {@code waiting}, and
     * {@code after} after their symbol.
     */
    private static List<LoadableList.Seg> gitHub(
            Option<PullRequest> pullRequest,
            Option<MergeStatus> merge,
            Option<Checks> checks,
            boolean waiting,
            Option<String> after) {
        return pullRequest.isDefined()
                ? pullRequest(pullRequest, merge, waiting, after)
                : checks.toList().flatMap(state -> checks(state, waiting, after));
    }

    /**
     * When the checks of a branch are expected to be done, as the lists show it, from what {@code model} knows
     * (ADR 32).
     */
    static Option<String> eta(Model model, Option<Eta> eta) {
        return eta.flatMap(known -> CheckTimesText.brief(known, model.gitHub().learningCheckTimes(), model.now()));
    }

    private static LoadableList.Seg symbol(PullRequestSymbols.Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }
}

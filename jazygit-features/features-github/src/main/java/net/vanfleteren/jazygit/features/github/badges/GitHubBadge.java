package net.vanfleteren.jazygit.features.github.badges;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.MergeStatus;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.timing.Eta;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.Waiting;
import net.vanfleteren.jazygit.features.github.widgets.CheckTimesText;
import net.vanfleteren.jazygit.features.github.widgets.PullRequestSymbols;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * How a branch is doing on GitHub, as the branches pane shows it among its badges (ADR 167): its pull request, with
 * how its checks are doing, its number, its review and where it sits in its stack, or, for a branch without one, how
 * the checks on its tip are doing. While the checks run, when they are expected to be done follows their symbol
 * (ADR 32), and while they all wait to start, how long they have (ADR 35). The symbols are one column wide (ADR 45).
 */
public final class GitHubBadge {

    private GitHubBadge() {}

    /**
     * What a row shows of {@code ref} in {@code model}: nothing for a local branch that is not read.
     */
    public static BranchBadges.Shown of(Model model, BranchRef ref) {
        GitHubState state = model.part(GitHubState.PART);
        return switch (ref) {
            case BranchRef.Local local ->
                model.repo()
                        .branch(local.branch())
                        .map(branch -> shown(model, state, branch))
                        .getOrElse(BranchBadges.Shown.NONE);
            case BranchRef.Remote remote ->
                shown(
                        state.pullRequest(remote.branch()),
                        state.checks(remote.branch()),
                        state.waiting(remote.branch()),
                        state.eta(remote.branch()),
                        state,
                        model.now());
        };
    }

    private static BranchBadges.Shown shown(Model model, GitHubState state, Branch branch) {
        return shown(
                state.pullRequest(branch),
                state.checks(branch),
                state.waiting(branch),
                state.eta(branch),
                state,
                model.now());
    }

    /**
     * What a row shows of a branch with {@code pullRequest} and {@code checks}, at {@code now}, the time of the model;
     * what does not change with the time is looked up once.
     */
    private static BranchBadges.Shown shown(
            Option<PullRequest> pullRequest,
            Option<Checks> checks,
            Option<Waiting> waiting,
            Option<Eta> eta,
            GitHubState state,
            Instant now) {
        boolean learning = state.learningCheckTimes();
        Option<MergeStatus> merge = state.merge(pullRequest);
        return new BranchBadges.Shown(
                gitHub(pullRequest, merge, checks, waiting, eta(eta, learning, now), now),
                timed(waiting, eta, learning));
    }

    /**
     * The text of the checks of a branch that changes with the time, at a time, as their segments show it after their
     * symbol (ADR 124): how long they have waited to start, or when they are expected to be done.
     */
    static Function<Instant, String> timed(Option<Waiting> waiting, Option<Eta> eta, boolean learning) {
        return at -> after(waiting, eta(eta, learning, at), at).getOrElse("");
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
                                " #" + pr.number().value(),
                                pr.draft() ? Theme.Classes.DIM : Theme.Classes.PULL_REQUEST))
                        .appendAll(group(
                                merge.isDefined()
                                        ? List.empty()
                                        : PullRequestSymbols.review(pr.review())
                                                .map(GitHubBadge::symbol)
                                                .toList()))
                        .appendAll(group(pr.stack()
                                .map(PullRequestSymbols::stack)
                                .map(GitHubBadge::symbol)
                                .toList())));
    }

    /**
     * How checks that are {@code state} are doing, waiting to start while they run and {@code waiting}, with
     * {@code after} after their symbol, dimmed, while they run.
     */
    private static List<LoadableList.Seg> checks(Checks state, boolean waiting, Option<String> after) {
        return PullRequestSymbols.checks(state, waiting)
                .map(GitHubBadge::symbol)
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
        return gitHub(pullRequest, merge, checks, waiting.isDefined(), after(waiting, eta, now));
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
     * What a list shows after the symbol of the checks of a branch at {@code now}: while they all wait to start,
     * {@code waiting}, how long they have; else when they are expected to be done, {@code eta}.
     */
    private static Option<String> after(Option<Waiting> waiting, Option<String> eta, Instant now) {
        return waiting.map(known -> known.since().map(since -> CheckTimesText.waited(Duration.between(since, now))))
                .getOrElse(eta);
    }

    /**
     * When the checks of a branch are expected to be done, as the lists show it at {@code now}, while the checks that
     * ran lately are read or not, {@code learning}.
     */
    private static Option<String> eta(Option<Eta> eta, boolean learning, Instant now) {
        return eta.flatMap(known -> CheckTimesText.brief(known, learning, now));
    }

    /**
     * {@code segs}, set apart from what comes before them by one more space, as the pane groups its own (ADR 45).
     */
    private static List<LoadableList.Seg> group(List<LoadableList.Seg> segs) {
        return segs.isEmpty() ? segs : segs.update(0, first -> first.prefixed(" "));
    }

    private static LoadableList.Seg symbol(PullRequestSymbols.Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }
}

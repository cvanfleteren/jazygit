package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * What GitHub reports about the branches of the repository, which the branches pane shows.
 *
 * @param pullRequests        the open pull requests on GitHub, by the remote branch they merge from; none until they
 *                            were read once, and the ones read last when reading them again failed
 * @param pullRequestsFailure why the pull requests could not be read the last time, if they could not
 * @param branchChecks        how the CI checks are doing on the branches on GitHub; none until they were read once,
 *                            and the ones read last when reading them again failed
 * @param reading             the reads from GitHub that are running; the line at the bottom says when the pull
 *                            requests are read
 * @param again               the running reads to start again once they are done, as they may have missed a change
 *                            just made; see {@link GitHubReads}
 * @param merges              the merges GitHub was asked for, by the number of their pull request, kept apart from
 *                            what is read so a read does not wipe how they went; only in memory (ADR 24)
 * @param checkTimes          how long the checks of the repository took, as far as it was learned; none until a read
 *                            told which repository it is (ADR 32)
 * @param pendingChecks       the checks one by one, with their timing, of the branches on GitHub whose checks are
 *                            pending; none until they were read once (ADR 32)
 * @param nextRead            the id the next read of the details of a pull request gets, higher than every one
 *                            given out before (ADR 34)
 * @param readsFrom           by the number of a pull request, the lowest id of a read of its details that is still
 *                            taken: past the last one taken, and past every one given out before a change to it, so a
 *                            read that started earlier does not show the change undone (ADR 34)
 */
@With
public record GitHubState(
        Option<Map<BranchName, PullRequest>> pullRequests,
        Option<Failure> pullRequestsFailure,
        Option<GitHubChecks> branchChecks,
        Set<Cmd> reading,
        Set<Cmd> again,
        Map<Integer, PullRequestMerge> merges,
        Option<CheckTimes> checkTimes,
        Option<PendingChecks> pendingChecks,
        long nextRead,
        Map<Integer, Long> readsFrom) {

    public static final GitHubState NOT_READ = new GitHubState(
            Option.none(),
            Option.none(),
            Option.none(),
            HashSet.empty(),
            HashSet.empty(),
            HashMap.empty(),
            Option.none(),
            Option.none(),
            0,
            HashMap.empty());

    /**
     * How the latest merge that merges pull request {@code number} went, or is going: the merge of it, or of one
     * above it in its stack. Only one merge merges it, as a later one replaces it ({@link #withMerge}).
     */
    public Option<MergeStatus> merge(int number) {
        return merges.values().find(merge -> merge.merges(number)).map(PullRequestMerge::status);
    }

    /**
     * How the latest merge of {@code pullRequest} went, or is going, if there is one.
     */
    public Option<MergeStatus> merge(Option<PullRequest> pullRequest) {
        return pullRequest.flatMap(pr -> merge(pr.number()));
    }

    /**
     * Whether GitHub is merging pull request {@code number} still, so it is not to be merged again meanwhile.
     */
    public boolean merging(int number) {
        return merge(number).exists(status -> status instanceof MergeStatus.Pending);
    }

    /**
     * The numbers of the open pull requests below pull request {@code number} in its stack, which merging it merges
     * too; none when it is not listed, or not stacked.
     */
    public List<Integer> below(int number) {
        List<PullRequest> open =
                pullRequests.map(byBranch -> byBranch.values().toList()).getOrElse(List.empty());
        return open.find(pr -> pr.number() == number)
                .flatMap(PullRequest::stack)
                .map(stack -> open.filter(other -> other.stack()
                                .exists(its -> its.number() == stack.number() && its.position() < stack.position()))
                        .map(PullRequest::number)
                        .sorted())
                .getOrElse(List.empty());
    }

    /**
     * This state with {@code merge} as the merge of its pull request, and of the ones below it: it replaces how earlier
     * merges went for those, each of which still tells how it went for its own pull request, when that is not one of
     * them.
     */
    public GitHubState withMerge(PullRequestMerge merge) {
        List<Integer> merged = merge.below().prepend(merge.number());
        return withMerges(merges.removeAll(merged)
                .mapValues(earlier -> earlier.withoutBelow(merged))
                .put(merge.number(), merge));
    }

    /**
     * This state with the pull request {@code details} are about as they were just read: as it now is, or no
     * longer listed once it is merged or closed. Unchanged when it is not listed.
     */
    /**
     * The state once the read with id {@link #nextRead} was given out.
     */
    public GitHubState withReadGivenOut() {
        return withNextRead(nextRead + 1);
    }

    /**
     * Whether read {@code read} of the details of pull request {@code number} is still taken when it comes back: no
     * later one was taken, and it started after the last change to it.
     */
    public boolean current(int number, long read) {
        return read >= readsFrom.getOrElse(number, 0L);
    }

    /**
     * The state once read {@code read} of the details of pull request {@code number} was taken: one that started
     * before it is no longer.
     */
    public GitHubState withReadTaken(int number, long read) {
        return withReadsFrom(readsFrom.put(number, read + 1));
    }

    /**
     * The state once pull request {@code number} was changed: no read of it given out before is taken anymore.
     */
    public GitHubState withChanged(int number) {
        return withReadsFrom(readsFrom.put(number, nextRead));
    }

    public GitHubState withRead(PullRequestDetails details) {
        return withPullRequests(pullRequests.map(open -> open.find(listed -> listed._2.number() == details.number())
                .map(listed -> details.listed().fold(() -> open.remove(listed._1), now -> open.put(listed._1, now)))
                .getOrElse(open)));
    }

    /**
     * The open pull request from {@code branch}: the one from the branch it follows on the remote, else from
     * the branch of the same name; none when it follows a branch on a remote other than GitHub.
     */
    public Option<PullRequest> pullRequest(Branch branch) {
        Option<RemoteBranch> upstream = branch.remoteUpstream();
        return upstream.exists(followed -> !mayBeOnGitHub(followed))
                ? Option.none()
                : pullRequests.flatMap(open ->
                        upstream.map(RemoteBranch::branch).flatMap(open::get).orElse(() -> open.get(branch.name())));
    }

    /**
     * The open pull request from {@code remote}, if it is a branch on GitHub.
     */
    public Option<PullRequest> pullRequest(RemoteBranch remote) {
        return onGitHub(remote) ? pullRequests.flatMap(open -> open.get(remote.branch())) : Option.none();
    }

    /**
     * How the CI checks are doing on the branch {@code branch} follows on GitHub, if it follows one.
     */
    public Option<Checks> checks(Branch branch) {
        return branch.remoteUpstream().flatMap(this::checks);
    }

    /**
     * How the CI checks are doing on {@code remote}, if it is a branch on GitHub.
     */
    public Option<Checks> checks(RemoteBranch remote) {
        return branchChecks
                .filter(checks -> checks.remotes().contains(remote.remote()))
                .flatMap(checks -> checks.byBranch().get(remote.branch()));
    }

    /**
     * When the checks on the branch {@code branch} follows on GitHub are expected to be done, while they are pending
     * and were read one by one (ADR 32).
     */
    public Option<Eta> eta(Branch branch) {
        return branch.remoteUpstream().flatMap(this::eta);
    }

    /**
     * When the checks on {@code remote}, a branch on GitHub, are expected to be done, while they are pending and were
     * read one by one (ADR 32).
     */
    public Option<Eta> eta(RemoteBranch remote) {
        return checks(remote)
                .filter(state -> state == Checks.PENDING)
                .flatMap(state -> pendingChecks)
                .flatMap(pending -> pending.byBranch()
                        .get(remote.branch())
                        .map(checks -> checkTimes
                                .filter(times -> times.repository().equals(pending.repository()))
                                .map(times -> times.eta(checks))
                                .getOrElse(() -> Eta.of(checks, CheckHistory.EMPTY))));
    }

    /**
     * Whether the checks that ran lately are being read from GitHub, to estimate from.
     */
    public boolean learningCheckTimes() {
        return checkTimes.exists(CheckTimes::toppingUp);
    }

    private boolean onGitHub(RemoteBranch remote) {
        return branchChecks.exists(checks -> checks.remotes().contains(remote.remote()));
    }

    /**
     * Whether {@code remote} is on GitHub, or could be, as which remotes are is not known yet.
     */
    private boolean mayBeOnGitHub(RemoteBranch remote) {
        return branchChecks.isEmpty() || onGitHub(remote);
    }
}

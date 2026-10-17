package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Upstream;

/**
 * What GitHub reports about the branches of the repository, which the branches pane shows.
 *
 * @param pullRequests        the open pull requests on GitHub, by the remote branch they merge from; none until they
 *                            were read once, and the ones read last when reading them again failed
 * @param pullRequestsFailure why the pull requests could not be read the last time, if they could not
 * @param branchChecks        how the CI checks are doing on the branches on GitHub; none until they were read once,
 *                            and the ones read last when reading them again failed
 * @param reading             the reads from GitHub that are running, once for each: one can be started again while
 *                            it runs, not to miss a change just made; the line at the bottom says when the pull
 *                            requests are read
 */
@With
public record GitHubState(
        Option<Map<Branch.Name, PullRequest>> pullRequests,
        Option<Failure> pullRequestsFailure,
        Option<GitHubChecks> branchChecks,
        List<Cmd> reading) {

    public static final GitHubState NOT_READ =
            new GitHubState(Option.none(), Option.none(), Option.none(), List.empty());

    /**
     * The open pull request from {@code branch}: the one from the branch it follows on the remote, else from
     * the branch of the same name; none when it follows a branch on a remote other than GitHub.
     */
    public Option<PullRequest> pullRequest(Branch branch) {
        Option<RemoteBranch> upstream = branch.remoteUpstream().map(Upstream::branch);
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
        return branch.remoteUpstream().flatMap(upstream -> checks(upstream.branch()));
    }

    /**
     * How the CI checks are doing on {@code remote}, if it is a branch on GitHub.
     */
    public Option<Checks> checks(RemoteBranch remote) {
        return branchChecks
                .filter(checks -> checks.remotes().contains(remote.remote()))
                .flatMap(checks -> checks.byBranch().get(remote.branch()));
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

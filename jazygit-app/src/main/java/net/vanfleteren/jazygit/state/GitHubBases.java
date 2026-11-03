package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * What GitHub says the branches are built on (ADR 72): by the branch an open pull request merges from, the branch
 * below it in its stack, else the base it merges into; and the protected branches, which are roots, as the main
 * branch is. All are branches as GitHub names them, so that this changes only when GitHub's answer does.
 *
 * @param byHead            by the branch a pull request merges from, the branch it is built on
 * @param protectedBranches the branches that are protected on GitHub; empty until they were read
 * @param remotes           the remotes on GitHub; none until they were read, when any of them may be
 */
public record GitHubBases(
        Map<BranchName, BranchName> byHead, Set<BranchName> protectedBranches, Option<Set<String>> remotes) {

    /**
     * What is known before GitHub said anything.
     */
    public static final GitHubBases NONE = new GitHubBases(HashMap.empty(), HashSet.empty(), Option.none());

    /**
     * What {@code gitHub} read last says the branches are built on.
     */
    public static GitHubBases of(GitHubState gitHub) {
        return new GitHubBases(
                gitHub.pullRequests()
                        .getOrElse(HashMap.empty())
                        .values()
                        .toList()
                        .flatMap(pr -> base(pr).map(base -> Tuple.of(new BranchName(pr.headBranch()), base)))
                        .toMap(pair -> pair),
                gitHub.protectedBranches().getOrElse(HashSet.empty()),
                gitHub.branchChecks().map(GitHubChecks::remotes));
    }

    /**
     * The branch {@code pr} is built on: the one below it in its stack, else its base; none when GitHub did not say.
     */
    private static Option<BranchName> base(PullRequest pr) {
        return pr.stack()
                .flatMap(PullRequestStack::branchBelow)
                .orElse(() -> Option.of(pr.baseBranch()).filter(base -> !base.isEmpty()))
                .map(BranchName::new);
    }

    /**
     * Whether {@code branch} is a root: the main branch, {@code main}, or one protected on GitHub. A root is never
     * built on another branch.
     */
    public boolean root(BranchName branch, Option<BranchName> main) {
        return root(branch, main, protectedBranches);
    }

    /**
     * Whether {@code branch} is a root: the main branch, {@code main}, or one of {@code protectedBranches}.
     */
    public static boolean root(BranchName branch, Option<BranchName> main, Set<BranchName> protectedBranches) {
        return main.contains(branch) || protectedBranches.contains(branch);
    }

    /**
     * The parent of each of {@code local} that is not a root and has one (ADR 72): the first of the branch it is built
     * on as GitHub says and the one recorded in {@code recorded}, {@code branch.<name>.gh-merge-base}, and only when
     * that is another of {@code local}. What GitHub says comes first, also when it names no local branch: a branch
     * whose pull request is on a colleague's branch that is not checked out has no parent.
     *
     * @param main the main branch, a root
     */
    public Map<BranchName, BranchName> parents(
            List<Branch> local, Option<BranchName> main, Map<BranchName, BranchName> recorded) {
        return local.filter(branch -> !root(branch.name(), main))
                .flatMap(branch -> onGitHub(branch)
                        .flatMap(byHead::get)
                        .fold(
                                () -> recorded.get(branch.name()).filter(parent -> isLocal(local, parent)),
                                onRemote -> localOf(local, onRemote))
                        .filter(parent -> !parent.equals(branch.name()))
                        .map(parent -> Tuple.of(branch.name(), parent)))
                .toMap(pair -> pair);
    }

    /**
     * The name {@code branch} has on GitHub: that of the branch it follows on the remote, else its own; none when it
     * follows a branch on a remote other than GitHub, as {@link GitHubState#pullRequest(Branch)} has it.
     */
    private Option<BranchName> onGitHub(Branch branch) {
        return branch.remoteUpstream()
                .fold(
                        () -> Option.of(branch.name()),
                        upstream -> remotes.forAll(onGitHub ->
                                        onGitHub.contains(upstream.remote().value()))
                                ? Option.of(upstream.branch())
                                : Option.none());
    }

    /**
     * The local branch that is {@code onRemote} on GitHub: the one that follows it, else the one of the same name.
     */
    private static Option<BranchName> localOf(List<Branch> local, BranchName onRemote) {
        return local.find(branch -> branch.remoteUpstream()
                        .exists(upstream -> upstream.branch().equals(onRemote)))
                .map(Branch::name)
                .orElse(() -> Option.of(onRemote).filter(name -> isLocal(local, name)));
    }

    private static boolean isLocal(List<Branch> local, BranchName name) {
        return local.exists(branch -> branch.name().equals(name));
    }
}

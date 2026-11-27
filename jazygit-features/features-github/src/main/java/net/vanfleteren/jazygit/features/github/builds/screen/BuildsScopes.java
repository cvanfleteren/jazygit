package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.github.failed.FailedBuilds;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;

/**
 * The panes of the builds screen and its content, with keys of their own, and what each acts on (ADR 212).
 */
public final class BuildsScopes {

    /**
     * The workflow highlighted in the workflows pane.
     */
    public static final Scope<RepoWorkflow> WORKFLOWS =
            new Scope<>(BuildsScreen.WORKFLOWS_PANE.value(), "github.panel.buildsWorkflows.title");

    /**
     * The run highlighted in the runs pane.
     */
    public static final Scope<Build> RUNS =
            new Scope<>(BuildsScreen.RUNS_PANE.value(), "github.panel.buildsRuns.title");

    /**
     * The run on the time line of the content panel.
     */
    public static final Scope<Build> CONTENT = new Scope<>("buildsScreenContent", "github.panel.buildsContent.title");

    /**
     * The panes of the screen, and its content, whose keys choose which runs show.
     */
    public static final List<Scope<?>> ALL = List.of(WORKFLOWS, RUNS, CONTENT);

    private BuildsScopes() {}

    /**
     * What failed in {@code run}, for the keys other features add to a failed build (ADR 161): of the local branch that
     * follows, on GitHub, the branch it ran for (else the branch whose builds show), with that branch's pull request.
     * A run of a branch no local one follows is handed nothing: the agent works in the worktree of a local branch, and
     * the head of another's pull request is not one.
     */
    public static Option<FailedBuild> failed(Model model, Option<Build> run) {
        BuildsScreen screen = model.part(BuildsScreen.PART);
        GitHubState github = model.part(GitHubState.PART);
        return run.filter(build -> build.outcome() == Build.Outcome.FAILED)
                .flatMap(build -> build.origin()
                        .branch()
                        .orElse(() -> switch (screen.filter()) {
                            case MINE -> screen.mine().map(mine -> mine.remote().branch());
                            case DEFAULT_BRANCH, EVERY_BRANCH -> screen.defaultName();
                        })
                        .flatMap(head -> following(model, head))
                        .map(local -> FailedBuilds.of(local.name(), build, github.pullRequest(local))));
    }

    /**
     * The local branch that follows {@code head} on GitHub, if one does.
     */
    private static Option<Branch> following(Model model, BranchName head) {
        GitHubState github = model.part(GitHubState.PART);
        return model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(local -> local.remoteUpstream()
                        .exists(remote -> remote.branch().equals(head) && github.mayBeOnGitHub(remote))));
    }
}

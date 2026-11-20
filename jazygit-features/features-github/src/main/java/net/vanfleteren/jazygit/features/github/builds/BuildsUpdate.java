package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.Pipeline;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Showing the builds of a local branch on GitHub, reading them some commits at a time, and choosing the tab and
 * whether it shows the runs of every branch (ADR 131); and what they read besides, each reading what it then needs.
 */
public final class BuildsUpdate {

    private BuildsUpdate() {}

    public static Next update(Model model, BuildsMsg msg) {
        return switch (msg) {
            case BuildsMsg.Toggled(BranchName branch) ->
                model.part(BuildsView.PART).exists(view -> view.branch().equals(branch))
                        ? Next.of(shown(model, Option.none()))
                        : onGitHub(model, branch)
                                .map(remote -> Next.of(
                                        shown(model, Option.of(kept(model, BuildsView.reading(branch, remote)))),
                                        new LoadBuilds(remote, Option.none())))
                                .getOrElse(() ->
                                        Next.of(shown(model, Option.of(kept(model, BuildsView.offGitHub(branch))))));
            case BuildsMsg.Closed() -> Next.of(shown(model, Option.none()));
            case BuildsMsg.Read(RemoteBranch branch, Option<String> after, var page) ->
                awaited(model, branch, after, view -> view.read(page));
            case BuildsMsg.Failed(RemoteBranch branch, Option<String> after, Failure failure) ->
                awaited(model, branch, after, view -> view.failed(failure));
            case BuildsMsg.Stepped(int by) -> BuildsReads.next(changed(model, view -> view.stepped(by)));
            case BuildsMsg.AllToggled() ->
                BuildsReads.next(changed(model, view -> view.withAllBranches(!view.allBranches())));
            case BuildsMsg.PickRequested() ->
                model.part(BuildsView.PART)
                        .flatMap(view -> view.shownOf())
                        .map(shown -> PopupUpdate.ask(
                                model,
                                new BuildsPopup(shown.tabs()
                                        .zip(shown.lasts())
                                        .map(tab -> new BuildsPopup.Choice(
                                                tab._1(),
                                                tab._1().label(shown.tabs()),
                                                tab._2(),
                                                count(model, tab._1()))))))
                        .getOrElse(() -> Next.of(model));
            case BuildsMsg.MoreRequested() -> more(model);
            case BuildsMsg.BuildPickRequested() ->
                model.part(BuildsView.PART)
                        .flatMap(view -> view.shownOf())
                        .flatMap(shown -> shown.builds()
                                .toOption()
                                .filter(builds -> !builds.isEmpty())
                                .map(builds -> PopupUpdate.ask(model, new BuildPickPopup(builds, shown.picked()))))
                        .getOrElse(() -> Next.of(model));
            case BuildsMsg.BuildPicked(Option<Long> run) ->
                BuildsReads.next(changed(model.withoutPopup(BuildPickPopup.class), view -> view.withPicked(run)));
            case BuildsMsg.BuildStepped(int by) -> BuildsReads.next(changed(model, view -> view.steppedPick(by)));
            case BuildsMsg.WindowRequested() ->
                PopupUpdate.ask(model, model.part(BuildsView.PART).map(view -> new BuildsWindowPopup(view.window())));
            case BuildsMsg.WindowPicked(Option<Integer> builds) ->
                BuildsReads.next(changed(model.withoutPopup(BuildsWindowPopup.class), view -> view.withWindow(builds)));
            case BuildsMsg.Picked(Pipeline pipeline) ->
                BuildsReads.next(changed(model.withoutPopup(BuildsPopup.class), view -> view.withSelected(pipeline)));
            case BuildsMsg.TabClicked(Pipeline pipeline) ->
                BuildsReads.next(changed(model, view -> view.withSelected(pipeline)));
            case BuildsMsg.BuildClicked(long run) -> BuildsReads.next(changed(model, view -> view.toggledPick(run)));
            case BuildsMsg.WorkflowsRead(var workflows) ->
                Next.of(changed(model, view -> view.withWorkflows(workflows)));
            case BuildsMsg.RunsRead(String id, var after, var page) ->
                runsAwaited(model, id, after, runs -> runs.read(page));
            case BuildsMsg.RunsFailed(String id, var after, var failure) ->
                runsAwaited(model, id, after, runs -> runs.failed(failure));
            case BuildsMsg.RepoWorkflowsRead(var workflows) ->
                BuildsReads.next(
                        changed(model, view -> view.withRepoWorkflows(Option.of(new Loadable.Loaded<>(workflows)))));
            case BuildsMsg.RepoWorkflowsFailed(var failure) ->
                Next.of(changed(model, view -> view.withRepoWorkflows(Option.of(new Loadable.Failed<>(failure)))));
        };
    }

    /**
     * {@code model} with what {@code read} makes of the runs of the workflow {@code id} from {@code after} on, when
     * those are still awaited; reading on from where they end while more are to be read.
     */
    private static Next runsAwaited(Model model, String id, Option<String> after, UnaryOperator<WorkflowRuns> read) {
        return model.part(BuildsView.PART)
                .flatMap(view -> view.runs()
                        .get(id)
                        .filter(runs -> runs.awaits(after))
                        .map(runs -> {
                            WorkflowRuns now = read.apply(runs);
                            Next shown = BuildsReads.next(shown(
                                    model, Option.of(view.withRuns(view.runs().put(id, now)))));
                            return now.reading() ? shown.and(new LoadWorkflowRuns(id, now.after())) : shown;
                        }))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code model}, reading older builds than those read, when older ones are left and none are being read: on every
     * branch, more runs of the workflow of the tab shown; else more commits of the branch (ADR 132).
     */
    private static Next more(Model model) {
        return model.part(BuildsView.PART)
                .flatMap(view -> view.allBranches() ? moreRuns(model, view) : moreCommits(model, view))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code model}, reading more runs on every branch of the workflow of the tab {@code view} shows.
     */
    private static Option<Next> moreRuns(Model model, BuildsView view) {
        return view.shownTab()
                .flatMap(tab -> view.workflowId(tab.workflow()))
                .flatMap(id -> view.runs()
                        .get(id)
                        .flatMap(WorkflowRuns::more)
                        .map(runs -> Next.of(
                                shown(model, Option.of(view.withRuns(view.runs().put(id, runs)))),
                                new LoadWorkflowRuns(id, runs.after()))));
    }

    /**
     * {@code model}, reading more commits of the branch {@code view} shows the builds of.
     */
    private static Option<Next> moreCommits(Model model, BuildsView view) {
        return view.onGitHub()
                .flatMap(remote -> view.more()
                        .map(more -> Next.of(shown(model, Option.of(more)), new LoadBuilds(remote, more.after()))));
    }

    /**
     * How many builds of {@code tab} are read: of the branch, or on every branch.
     */
    private static Option<Integer> count(Model model, Pipeline tab) {
        return model.part(BuildsView.PART)
                .flatMap(view -> view.allBranches()
                        ? view.runsOf(tab.workflow())
                                .flatMap(runs -> runs.builds().toOption())
                                .map(runs -> runs.size())
                        : Option.of(view.builds().getOrElse(List.empty()).count(tab::ran)));
    }

    /**
     * {@code model} with what {@code read} makes of the builds of the commits of {@code branch} from {@code after} on,
     * when those are still awaited; reading on from where they end while more are to be read, and what they show.
     */
    private static Next awaited(
            Model model, RemoteBranch branch, Option<String> after, UnaryOperator<BuildsView> read) {
        return model.part(BuildsView.PART)
                .filter(view -> view.awaits(branch, after))
                .map(read)
                .map(view -> {
                    Next shown = BuildsReads.next(shown(model, Option.of(view)));
                    return view.reading() ? shown.and(new LoadBuilds(branch, view.after())) : shown;
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code view} keeping the workflow chosen for the builds shown before, of another branch, and whether they showed
     * every branch: a branch with builds of the same workflow shows those (ADR 131).
     */
    private static BuildsView kept(Model model, BuildsView view) {
        return model.part(BuildsView.PART)
                .map(before -> before.selected()
                        .map(view::withSelected)
                        .getOrElse(view)
                        .withAllBranches(before.allBranches())
                        .withWindow(before.window()))
                .getOrElse(view);
    }

    /**
     * {@code model} with the builds view as {@code change} makes it.
     */
    private static Model changed(Model model, UnaryOperator<BuildsView> change) {
        return model.part(BuildsView.PART)
                .map(view -> shown(model, Option.of(change.apply(view))))
                .getOrElse(model);
    }

    private static Model shown(Model model, Option<BuildsView> builds) {
        return model.withPart(BuildsView.PART, builds);
    }

    /**
     * The branch on GitHub whose builds {@code branch} shows: the one it follows; none when it follows none, or one
     * on a remote that is not on GitHub, as the builds are read from the repository gh finds, which would show those
     * of another branch of that name.
     */
    private static Option<RemoteBranch> onGitHub(Model model, BranchName branch) {
        return model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(local -> local.name().equals(branch)))
                .flatMap(Branch::remoteUpstream)
                .filter(remote -> model.part(GitHubState.PART).mayBeOnGitHub(remote));
    }
}

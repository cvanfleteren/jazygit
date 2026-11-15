package net.vanfleteren.jazygit.feature.github.builds;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.BuildsView.FileAt;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;
import net.vanfleteren.jazygit.state.view.Workflow;

/**
 * Showing the builds of a local branch on GitHub, and reading them some commits at a time (ADR 131).
 */
public final class BuildsUpdate {

    private BuildsUpdate() {}

    public static Next update(Model model, BuildsMsg msg) {
        return switch (msg) {
            case BuildsMsg.Toggled(BranchName branch) ->
                model.repoView().builds().exists(view -> view.branch().equals(branch))
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
            case BuildsMsg.Stepped(int by) -> Next.of(shownChanged(model, (view, builds) -> view.stepped(builds, by)));
            case BuildsMsg.PickRequested() ->
                model.repoView()
                        .builds()
                        .map(view -> shownBuilds(model, view))
                        .filter(builds -> !builds.isEmpty())
                        .map(builds -> {
                            List<Pipeline> all = BuildsView.pipelines(builds);
                            return Update.ask(
                                    model,
                                    new BuildsPopup(all.map(pipeline -> new BuildsPopup.Choice(
                                            pipeline,
                                            pipeline.label(all),
                                            builds.find(pipeline::ran)
                                                    .map(Build::outcome)
                                                    .getOrElse(Build.Outcome.PASSED),
                                            builds.count(pipeline::ran)))));
                        })
                        .getOrElse(() -> Next.of(model));
            case BuildsMsg.Picked(Pipeline pipeline) ->
                Next.of(shownChanged(
                        model.withoutPopup(BuildsPopup.class), (view, builds) -> view.withSelected(pipeline)));
            case BuildsMsg.WorkflowsRead(var workflows) ->
                Next.of(model.repoView()
                        .builds()
                        .map(view -> shown(model, Option.of(view.withWorkflows(workflows))))
                        .getOrElse(model));
        };
    }

    /**
     * {@code model} with what {@code read} makes of the builds of the commits of {@code branch} from {@code after} on,
     * when those are still awaited; reading on from where they end while more are to be read.
     */
    private static Next awaited(
            Model model, RemoteBranch branch, Option<String> after, UnaryOperator<BuildsView> read) {
        return model.repoView()
                .builds()
                .filter(view -> view.awaits(branch, after))
                .map(read)
                .map(view -> {
                    // The file of the workflow of the last build that is done, once there is one, says which of its
                    // jobs needs which (ADR 131).
                    List<FileAt> unread = view.unread();
                    BuildsView asked = view.withWorkflows(
                            HashMap.ofEntries(unread.map(file -> io.vavr.Tuple.of(file, Option.<Workflow>none()))));
                    List<Cmd> cmds = List.<Cmd>empty()
                            .appendAll(Option.when(view.reading(), () -> new LoadBuilds(branch, view.after())))
                            .appendAll(unread.map(ReadWorkflows::new));
                    return new Next(shown(model, Option.of(asked)), cmds);
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code view} keeping the workflow chosen for the builds shown before, of another branch: a branch with builds
     * of the same workflow shows those (ADR 131).
     */
    private static BuildsView kept(Model model, BuildsView view) {
        return model.repoView()
                .builds()
                .flatMap(BuildsView::selected)
                .map(view::withSelected)
                .getOrElse(view);
    }

    /**
     * Of the builds of {@code view}, those that show: with only the commits not on the base listed ({@code b}), those
     * of the commits not on it, as the log of the branch (ADR 20, 131).
     */
    public static List<Build> shownBuilds(Model model, BuildsView view) {
        return BuildsView.shown(
                view.builds().getOrElse(List.empty()),
                AheadUpdate.branch(model, new BranchRef.Local(view.branch())),
                model.repoView().onlyAhead());
    }

    /**
     * {@code model} with the builds view as {@code change} makes it of it and the builds that show.
     */
    private static Model shownChanged(Model model, BiFunction<BuildsView, List<Build>, BuildsView> change) {
        return model.repoView()
                .builds()
                .map(view -> shown(model, Option.of(change.apply(view, shownBuilds(model, view)))))
                .getOrElse(model);
    }

    private static Model shown(Model model, Option<BuildsView> builds) {
        return model.withRepoView(model.repoView().withBuilds(builds));
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
                .filter(remote -> model.gitHub().mayBeOnGitHub(remote));
    }
}

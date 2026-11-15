package net.vanfleteren.jazygit.feature.github.builds;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.BuildsView.FileAt;
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
                                        shown(model, Option.of(BuildsView.reading(branch, remote))),
                                        new LoadBuilds(remote, Option.none())))
                                .getOrElse(() -> Next.of(shown(model, Option.of(BuildsView.offGitHub(branch)))));
            case BuildsMsg.Read(RemoteBranch branch, Option<String> after, var page) ->
                awaited(model, branch, after, view -> view.read(page));
            case BuildsMsg.Failed(RemoteBranch branch, Option<String> after, Failure failure) ->
                awaited(model, branch, after, view -> view.failed(failure));
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

package net.vanfleteren.jazygit.feature.github.builds;

import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.view.BuildsView.FileAt;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;
import net.vanfleteren.jazygit.state.view.Workflow;

/**
 * Showing the builds of a local branch on GitHub (ADR 131).
 */
public sealed interface BuildsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BuildsUpdate.update(model, this);
    }

    /**
     * The user asked for the builds of {@code branch}, or, while they show, for its log again.
     */
    record Toggled(BranchName branch) implements BuildsMsg {}

    /**
     * The user asked, in the builds, for the log of the branch again.
     */
    record Closed() implements BuildsMsg {}

    /**
     * The commits of {@code branch} on GitHub from {@code after} on were read, with their builds; none when it is
     * not on GitHub.
     */
    record Read(RemoteBranch branch, Option<String> after, Option<BuildsPage> page) implements BuildsMsg {}

    record Failed(RemoteBranch branch, Option<String> after, Failure failure) implements BuildsMsg {}

    /**
     * The files of workflows were read at the commit of a build, each as its workflow, or none when it could not be.
     */
    record WorkflowsRead(Map<FileAt, Option<Workflow>> workflows) implements BuildsMsg {}

    /**
     * The user asked for the builds of the workflow {@code by} tabs after the one that shows, before it when negative.
     */
    record Stepped(int by) implements BuildsMsg {}

    /**
     * The user asked to choose the workflow whose builds show from all of them.
     */
    record PickRequested() implements BuildsMsg {}

    /**
     * The user chose {@code pipeline} in the popup.
     */
    record Picked(Pipeline pipeline) implements BuildsMsg {}
}

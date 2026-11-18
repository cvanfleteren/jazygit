package net.vanfleteren.jazygit.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;

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
     * The user asked for the builds of the workflow {@code by} tabs after the one that shows, before it when negative.
     */
    record Stepped(int by) implements BuildsMsg {}

    /**
     * The user asked for the runs of the workflows on every branch in place of the builds of the branch, or back.
     */
    record AllToggled() implements BuildsMsg {}

    /**
     * The user asked for older builds than those read: of more commits of the branch, or, on every branch, more runs
     * of the workflow of the tab (ADR 132).
     */
    record MoreRequested() implements BuildsMsg {}

    /**
     * The user asked to choose how many builds show.
     */
    record WindowRequested() implements BuildsMsg {}

    /**
     * The user chose to show the newest {@code builds} builds; none for all those read.
     */
    record WindowPicked(Option<Integer> builds) implements BuildsMsg {}

    /**
     * The user asked to pick the build whose jobs show on one time line (ADR 133).
     */
    record BuildPickRequested() implements BuildsMsg {}

    /**
     * The user picked the build of the run {@code run}; none for the last build that is done (ADR 133).
     */
    record BuildPicked(Option<Long> run) implements BuildsMsg {}

    /**
     * The user asked for the build {@code by} rows after the one on the time line, an older one when positive, to
     * show its jobs there (ADR 133).
     */
    record BuildStepped(int by) implements BuildsMsg {}

    /**
     * The user asked to choose the workflow whose builds show from all of them.
     */
    record PickRequested() implements BuildsMsg {}

    /**
     * The user chose {@code pipeline} in the popup.
     */
    record Picked(Pipeline pipeline) implements BuildsMsg {}
}

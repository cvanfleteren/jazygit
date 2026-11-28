package net.vanfleteren.jazygit.features.branches.branch.push;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.popup.TextPopup;

/**
 * Asking where to push {@code branch}: it has no upstream yet, or {@code push.default} does not push it to the one
 * it has.
 *
 * @param upstream the upstream as far as it was typed; it starts out as the branch of the same name, on the default
 *                 remote or on the remote of the upstream it has
 * @param refused  why {@code push.default} did not push it, if that is why it is asked
 */
public record SetUpstreamPopup(BranchName branch, String upstream, Option<Refusal> refused) implements TextPopup {

    /**
     * Asking where to push {@code branch}, which follows nothing, starting out with the branch of its own name on the
     * remote it is pushed to: the one set for it, else {@code remote.pushDefault}, else the base remote (ADR 215).
     */
    public static SetUpstreamPopup of(Branch branch) {
        return new SetUpstreamPopup(
                branch.name(), new RemoteBranch(branch.pushTarget().remote(), branch.name()).text(), Option.none());
    }

    /**
     * Asking where to push {@code branch}, which {@code push.default} did not push, as {@code refused} says.
     */
    public static SetUpstreamPopup refused(BranchName branch, Refusal refused) {
        return new SetUpstreamPopup(
                branch, new RemoteBranch(refused.upstream().remote(), branch).text(), Option.of(refused));
    }

    @Override
    public String text() {
        return upstream;
    }

    @Override
    public SetUpstreamPopup withText(String text) {
        return new SetUpstreamPopup(branch, text, refused);
    }

    /**
     * {@code push.default} did not push the branch to {@code upstream}, the branch it follows.
     *
     * @param pushDefault the value of {@code push.default}, {@code simple} when it is not set
     */
    public record Refusal(String pushDefault, RemoteBranch upstream) {}
}

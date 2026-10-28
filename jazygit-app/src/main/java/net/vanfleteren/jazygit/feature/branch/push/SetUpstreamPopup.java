package net.vanfleteren.jazygit.feature.branch.push;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking where to push {@code branch}: it has no upstream yet, or {@code push.default} does not push it to the one
 * it has.
 *
 * @param upstream the upstream as far as it was typed; it starts out as the branch of the same name, on the default
 *                 remote or on the remote of the upstream it has
 * @param refused  why {@code push.default} did not push it, if that is why it is asked
 */
public record SetUpstreamPopup(BranchName branch, String upstream, Option<Refusal> refused) implements TextPopup {

    public SetUpstreamPopup(BranchName branch) {
        this(branch, new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, branch).text(), Option.none());
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

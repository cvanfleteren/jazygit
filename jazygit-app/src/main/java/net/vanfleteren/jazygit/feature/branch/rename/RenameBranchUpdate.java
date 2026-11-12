package net.vanfleteren.jazygit.feature.branch.rename;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchError;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.LocallyChosen;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.Requested;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.WithUpstreamChosen;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Renaming a local branch, and its upstream on its remote too when the user chooses so (ADR 90).
 */
public final class RenameBranchUpdate {

    private RenameBranchUpdate() {}

    public static Next update(Model model, RenameBranchMsg msg) {
        return switch (msg) {
            case Requested(BranchName branch) ->
                Update.ask(model, model.idleBranch(branch).map(known -> new RenameBranchPopup(branch)));
            case Confirmed() -> confirmed(model);
            case LocallyChosen() ->
                Update.confirm(
                        model,
                        RenameUpstreamPopup.class,
                        popup -> Option.of(new RenameBranchCmd(popup.branch(), popup.newName())));
            case WithUpstreamChosen() -> withUpstreamChosen(model);
        };
    }

    /**
     * Renames the branch and its upstream, only while that is still the one the dialog named. When it is not, as the
     * branches were read again meanwhile, nothing is renamed and the dialog closes, saying so.
     */
    private static Next withUpstreamChosen(Model model) {
        return model.popup(RenameUpstreamPopup.class)
                .filter(popup -> !renamableUpstream(model, popup.branch()).contains(popup.upstream()))
                .map(popup -> Next.of(model.withoutPopup(RenameUpstreamPopup.class)
                        .fail(new BranchError.UpstreamChanged(
                                popup.branch(), popup.upstream().shortName()))))
                .getOrElse(() -> Update.confirm(
                        model,
                        RenameUpstreamPopup.class,
                        popup -> Option.of(
                                new RenameBranchCmd(popup.branch(), popup.newName(), Option.of(popup.upstream())))));
    }

    private static Next confirmed(Model model) {
        // The name it already has leaves it as it is.
        boolean unchanged = model.popup(RenameBranchPopup.class)
                .exists(popup -> BranchName.parse(popup.name()).contains(popup.branch()));
        // A branch with an upstream that can be renamed with it is asked whether to rename that too.
        Option<RenameUpstreamPopup> withUpstream = model.popup(RenameBranchPopup.class)
                .flatMap(popup -> BranchName.parse(popup.name())
                        .flatMap(newName -> renamableUpstream(model, popup.branch())
                                .map(upstream -> new RenameUpstreamPopup(popup.branch(), newName, upstream))));
        // A name that is no branch name, like a blank one, keeps the dialog open.
        return unchanged
                ? Next.of(model.withoutPopup(RenameBranchPopup.class))
                : withUpstream
                        .map(popup -> Update.ask(model.withoutPopup(RenameBranchPopup.class), popup))
                        .getOrElse(() -> Update.confirm(
                                model,
                                RenameBranchPopup.class,
                                popup -> BranchName.parse(popup.name())
                                        .map(newName -> new RenameBranchCmd(popup.branch(), newName))));
    }

    /**
     * The upstream of {@code branch} that can be renamed with it: one of the same name, on a remote, that is there;
     * not that of the default branch, which would break every clone.
     */
    private static Option<RemoteBranch> renamableUpstream(Model model, BranchName branch) {
        return model.repo()
                .branch(branch)
                .filter(Branch::remoteDeletable)
                .flatMap(Branch::remoteUpstream)
                .filter(upstream -> upstream.branch().equals(branch));
    }
}

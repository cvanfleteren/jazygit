package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutUpdate;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Creating a branch.
 */
public final class NewBranchUpdate {

    private NewBranchUpdate() {}

    public static Next update(Model model, NewBranchMsg msg) {
        return switch (msg) {
            case Requested(BranchName base) ->
                named(model, model.idleBranch(base).map(known -> new NewBranchPopup(base)));
            case NewBranchMsg.RequestedAtCommit(var sha) -> atCommit(model, sha);
            case NewBranchMsg.RequestedAtRemote(var remote) ->
                named(
                        model,
                        model.repo()
                                .remoteBranch(remote)
                                .map(known -> new NewBranchPopup(remote.tracking(), Option.of(remote.branch()))));
            case NewBranchMsg.NameTemplateRead(var popup, var template) ->
                Update.ask(model, popup.withText(template.getOrElse("")));
            // Checking it out may ask where, in a linked worktree (ADR 68).
            case NewBranchMsg.Confirmed() ->
                model.popup(NewBranchPopup.class)
                        .flatMap(NewBranchPopup::request)
                        .map(request -> CheckoutUpdate.place(model.withoutPopup(NewBranchPopup.class), request))
                        .getOrElse(() -> Next.of(model));
        };
    }

    private static Next atCommit(Model model, Sha sha) {
        return named(
                model,
                model.commit(sha).map(commit -> new NewBranchPopup(new Revision.Commit(commit.sha()), Option.none())));
    }

    /**
     * Asks for the name of a new branch with {@code popup}, if there is one, once what the name starts out as is
     * read (ADR 121); none when the branch cannot be made, and then nothing happens.
     */
    private static Next named(Model model, Option<NewBranchPopup> popup) {
        return popup.map(asking -> Next.of(model, new ReadNameTemplate(asking))).getOrElse(() -> Next.of(model));
    }
}

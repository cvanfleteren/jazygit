package net.vanfleteren.jazygit.feature.cherrypick;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Cherry-picking a commit: it is copied in the log of one branch, and picked onto another once that is checked
 * out. The copy stays until it is picked or dropped, also when picking it fails, so it can be tried again.
 */
public final class CherryPickUpdate {

    private CherryPickUpdate() {}

    public static Next update(Model model, CherryPickMsg msg) {
        return switch (msg) {
            case CherryPickMsg.Copied(Sha sha) -> copied(model, sha);
            case CherryPickMsg.Dropped() ->
                Next.of(model.mapRepoView(repoView -> repoView.withCherryPick(Option.none())));
            case CherryPickMsg.Requested() -> requested(model);
            case CherryPickMsg.Cancelled() -> Next.of(model.withoutPopup(CherryPickPopup.class));
            case CherryPickMsg.Confirmed() ->
                Update.confirm(
                        model,
                        CherryPickPopup.class,
                        popup -> Option.of(new CherryPickCmd(popup.commit().sha())));
            case CherryPickMsg.Picked(CherryPickCmd cmd, List<ExecutedCommand> commands) ->
                OperationUpdate.succeeded(picked(model, cmd.sha()), cmd, commands);
        };
    }

    /**
     * A merge cannot be copied: which of its parents to pick it against is not asked.
     */
    private static Next copied(Model model, Sha sha) {
        return Next.of(model.commit(sha)
                .filter(commit -> !commit.isMerge())
                .map(commit -> model.mapRepoView(repoView -> repoView.withCherryPick(Option.of(commit))))
                .getOrElse(model));
    }

    /**
     * Without a copied commit, nothing happens; a commit the checked out branch already has is not picked again.
     */
    private static Next requested(Model model) {
        return model.repoView()
                .cherryPick()
                .map(commit -> model.repo().commit(commit.sha()).isDefined()
                        ? Next.of(model.fail(new AppError.AlreadyPicked(commit.sha())))
                        : Update.ask(model, popup(model, commit)))
                .getOrElse(() -> Next.of(model));
    }

    private static CherryPickPopup popup(Model model, Commit commit) {
        return new CherryPickPopup(commit, model.repo().currentBranch().map(Branch::name));
    }

    /**
     * The copy is done with once it is picked; one copied meanwhile stays.
     */
    private static Model picked(Model model, Sha sha) {
        return model.repoView().cherryPick().exists(commit -> commit.sha().equals(sha))
                ? model.mapRepoView(repoView -> repoView.withCherryPick(Option.none()))
                : model;
    }
}

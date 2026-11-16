package net.vanfleteren.jazygit.ui.files;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.state.view.BranchesTab;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * The files the files pane lists for the pane that has the focus (ADR 141): those of the commit or the stash its list
 * highlights, which follow the highlight, or those of the working tree. The highlight is the one whose diff the
 * content panel shows, which the model knows. While the files pane, or an area of the content panel, has the focus,
 * what it lists stays.
 */
final class FollowedFiles {

    private FollowedFiles() {}

    /**
     * The message that makes the files pane of {@code model} list what the pane in use, {@code inUse}, follows, when it
     * lists something else. The pane in use is the one with the focus, or whose content has it: the focus, not the
     * model, which learns where it is a frame later, as {@code F} moves it into the files it opens.
     */
    static Option<Msg> change(Model model, Option<PaneId> inUse) {
        if (model.screen() != Screen.REPOSITORY) {
            return Option.none();
        }
        return inUse.filter(pane -> !pane.equals(FilesPanel.PANE)).flatMap(pane -> changeFor(model, pane));
    }

    private static Option<Msg> changeFor(Model model, PaneId from) {
        Option<CommitFilesView> listed = model.repoView().files().commitFiles();
        Option<Changes> wanted = followed(model, from);
        boolean same = wanted.isEmpty()
                ? listed.isEmpty()
                : listed.exists(view -> view.followed() && view.of().sameAs(wanted.get()));
        return same ? Option.none() : Option.of(new SelectionMsg.FilesFollowed(wanted, from, FilesPanel.PANE));
    }

    /**
     * What the list of the pane {@code from} highlights, whose files are listed: a commit of the commits, of a
     * divergence or of the commits of a branch, or a stash. None for any other pane, and for the branches themselves.
     */
    static Option<Changes> followed(Model model, PaneId from) {
        if (from.equals(Scopes.COMMITS.pane())) {
            return commit(model.repoView().preview().commitDetail());
        }
        if (from.equals(Scopes.STASH.pane())) {
            return model.repoView().preview().stashDetail().map(Preview::key).map(Changes.OfStash::new);
        }
        if (from.equals(Scopes.BRANCHES.pane())) {
            var branches = model.repoView().branches();
            return branches.divergence()
                    .filter(view -> branches.tab() == BranchesTab.LOCAL)
                    .map(view -> commit(view.detail()))
                    .orElse(() -> branches.branchCommits().map(view -> commit(view.detail())))
                    .getOrElse(Option::none);
        }
        return Option.none();
    }

    private static Option<Changes> commit(Option<? extends Preview<Sha, ?>> detail) {
        return detail.map(Preview::key).map(Changes.OfCommit::new);
    }
}

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
import net.vanfleteren.jazygit.state.view.FilesView;
import net.vanfleteren.jazygit.state.view.Followed;
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
     * lists something else; {@code branches} is what the branches follow while they list the branches themselves. The
     * pane in use is the one with the focus, or whose content has it: the focus, not the model, which learns where it
     * is a frame later, as {@code F} moves it into the files it opens.
     */
    static Option<Msg> change(Model model, Option<PaneId> inUse, Option<Followed> branches) {
        if (model.screen() != Screen.REPOSITORY) {
            return Option.none();
        }
        return inUse.filter(pane -> !pane.equals(FilesPanel.PANE))
                .flatMap(pane -> followed(model, pane, branches)
                        .filter(wanted -> !lists(model.repoView().files(), wanted, pane))
                        .map(wanted -> new SelectionMsg.FilesFollowed(wanted, pane, FilesPanel.PANE)));
    }

    /**
     * Whether {@code files} list what is {@code wanted} for the pane {@code from}: the same files, as they follow the
     * highlight of that pane. Those {@code F} asked for follow it again, and those another list highlights the same
     * commit of follow this one: {@code Esc} goes back to it.
     */
    private static boolean lists(FilesView files, Followed wanted, PaneId from) {
        return wanted instanceof Followed.Files(Changes of)
                ? files.commitFiles()
                        .exists(view -> view.followed()
                                && view.pane().equals(from)
                                && view.of().sameAs(of))
                : files.followed().equals(wanted);
    }

    /**
     * What the list of the pane {@code from} highlights follows: a commit of the commits, of a divergence or of the
     * commits of a branch, a stash, or what {@code branches} says the branches follow. Any other pane follows the
     * working tree; none while that is not known yet.
     */
    static Option<Followed> followed(Model model, PaneId from, Option<Followed> branches) {
        if (from.equals(Scopes.COMMITS.pane())) {
            return Option.of(commit(model.repoView().preview().commitDetail()));
        }
        if (from.equals(Scopes.STASH.pane())) {
            return Option.of(files(
                    model.repoView().preview().stashDetail().map(Preview::key).map(Changes.OfStash::new)));
        }
        if (from.equals(Scopes.BRANCHES.pane())) {
            var view = model.repoView().branches();
            return view.divergence()
                    .filter(divergence -> view.tab() == BranchesTab.LOCAL)
                    .map(divergence -> commit(divergence.detail()))
                    .orElse(() -> view.branchCommits().map(commits -> commit(commits.detail())))
                    .map(Option::of)
                    .getOrElse(branches);
        }
        return Option.of(new Followed.WorkingTree());
    }

    private static Followed commit(Option<? extends Preview<Sha, ?>> detail) {
        return files(detail.map(Preview::key).map(Changes.OfCommit::new));
    }

    private static Followed files(Option<? extends Changes> of) {
        return of.<Followed>map(Followed.Files::new).getOrElse(Followed.WorkingTree::new);
    }
}

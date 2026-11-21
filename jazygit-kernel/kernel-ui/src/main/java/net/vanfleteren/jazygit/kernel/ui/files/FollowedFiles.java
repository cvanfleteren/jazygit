package net.vanfleteren.jazygit.kernel.ui.files;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.state.view.FilesView;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The files the files pane lists for the pane that has the focus (ADR 141): those of the commit or the stash its list
 * highlights, which follow the highlight, or those of the working tree. The highlight is the one whose diff the
 * content panel shows, which the model knows. While the files pane, or an area of the content panel, has the focus,
 * what it lists stays. Locked to a list, by Enter in it, it follows that list only (ADR 149).
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
        if (!model.screen().equals(Screen.REPOSITORY)) {
            return Option.none();
        }
        return model.repoView()
                .files()
                .locked()
                .map(lock -> locked(model, lock, branches))
                .getOrElse(() -> following(model, inUse, branches));
    }

    /**
     * The message that makes the files pane list what the list it is locked to by {@code lock} follows, once its
     * highlight moved since the pane last listed for it: whichever pane has the focus, and whatever the pane lists
     * until then, as the files of a pull request {@code F} opened (ADR 149). The frame after the lock tells what the
     * list follows then; a list no longer shown lets go of the lock.
     */
    private static Option<Msg> locked(Model model, FilesLock lock, Option<Followed> branches) {
        if (!lock.holds(model.repoView(), lock.pane().equals(Scopes.BRANCHES.pane()))) {
            return Option.of(new SelectionMsg.FilesUnlocked());
        }
        return followed(model, lock.pane(), branches)
                .filter(wanted -> !lock.at().contains(wanted))
                .map(wanted -> lock.at().isEmpty()
                        ? new SelectionMsg.FilesLockedAt(wanted)
                        : new SelectionMsg.FilesFollowed(wanted, lock.pane(), FilesPanel.PANE));
    }

    private static Option<Msg> following(Model model, Option<PaneId> inUse, Option<Followed> branches) {
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

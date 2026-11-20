package net.vanfleteren.jazygit.features.changes.commit.squash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Squashing a commit into the one below it.
 */
public final class SquashUpdate {

    private SquashUpdate() {}

    public static Next update(Model model, SquashMsg msg) {
        return switch (msg) {
            case SquashMsg.Requested(Sha sha) -> requested(model, sha);
        };
    }

    /**
     * A commit with exactly one parent can be squashed into it: a root commit has nothing to squash into, and
     * a merge has no single commit below it.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha)
                .flatMap(commit -> onlyParent(commit).map(into -> new SquashPopup(commit, into)))
                .transform(popup -> PopupUpdate.ask(model, popup));
    }

    /**
     * The parent of {@code commit}, if it has exactly one: a root commit has nothing to squash into, and a
     * merge has no single commit below it.
     */
    public static Option<Sha> onlyParent(Commit commit) {
        return commit.parents().headOption().filter(parent -> commit.parents().size() == 1);
    }
}

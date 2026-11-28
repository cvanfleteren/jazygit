package net.vanfleteren.jazygit.features.changes.commit.squash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheck;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
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
            case SquashMsg.Requested requested -> requested(model, requested);
            case SquashMsg.Checked(var sha, var warning) -> ask(model, sha, warning);
        };
    }

    /**
     * A commit with exactly one parent can be squashed into it: a root commit has nothing to squash into, and
     * a merge has no single commit below it. When it is pushed already, the confirmation warns of that.
     */
    private static Next requested(Model model, SquashMsg.Requested requested) {
        Sha sha = requested.sha();
        return model.commit(sha)
                .flatMap(SquashUpdate::onlyParent)
                .map(into -> PushedCheck.first(model, sha, requested, () -> ask(model, sha, Option.none())))
                .getOrElse(() -> Next.of(model));
    }

    private static Next ask(Model model, Sha sha, Option<PushedWarning> warning) {
        return model.commit(sha)
                .flatMap(commit -> onlyParent(commit).map(into -> new SquashPopup(commit, into, warning)))
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

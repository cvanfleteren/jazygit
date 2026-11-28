package net.vanfleteren.jazygit.features.changes.commit.fixup;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheck;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.features.changes.commit.squash.SquashCmd;
import net.vanfleteren.jazygit.features.changes.commit.squash.SquashUpdate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Fixing up a commit into the one below it: squashing it, keeping only one of the two messages.
 */
public final class FixupUpdate {

    private FixupUpdate() {}

    public static Next update(Model model, FixupMsg msg) {
        return switch (msg) {
            case FixupMsg.Requested requested -> requested(model, requested);
            case FixupMsg.Checked(var sha, var warning) -> ask(model, sha, warning);
            case FixupMsg.PushedConfirmed() ->
                model.popup(FixupPushedPopup.class)
                        .map(popup -> PopupUpdate.ask(model.withoutPopup(FixupPushedPopup.class), popup.fixup()))
                        .getOrElse(() -> Next.of(model));
            case FixupMsg.Chosen(SquashMode mode) ->
                PopupUpdate.confirm(
                        model,
                        FixupPopup.class,
                        popup -> Option.of(new SquashCmd(popup.commit().sha(), mode)));
        };
    }

    /**
     * Only a commit that can be squashed can be fixed up: one with exactly one parent. When it is pushed already, a
     * warning comes first.
     */
    private static Next requested(Model model, FixupMsg.Requested requested) {
        Sha sha = requested.sha();
        return model.commit(sha)
                .flatMap(SquashUpdate::onlyParent)
                .map(into -> PushedCheck.first(model, sha, requested, () -> ask(model, sha, Option.none())))
                .getOrElse(() -> Next.of(model));
    }

    private static Next ask(Model model, Sha sha, Option<PushedWarning> warning) {
        return model.commit(sha)
                .flatMap(commit -> SquashUpdate.onlyParent(commit)
                        .map(into -> warning.<Popup>map(pushed -> new FixupPushedPopup(commit, into, pushed))
                                .getOrElse(() -> new FixupPopup(commit, into))))
                .transform(popup -> PopupUpdate.ask(model, popup));
    }
}

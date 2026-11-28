package net.vanfleteren.jazygit.features.changes.commit.reword;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheck;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.features.changes.commit.reword.RewordMsg.Checked;
import net.vanfleteren.jazygit.features.changes.commit.reword.RewordMsg.PushedConfirmed;
import net.vanfleteren.jazygit.features.changes.commit.reword.RewordMsg.Requested;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Rewording a commit.
 */
public final class RewordUpdate {

    private RewordUpdate() {}

    public static Next update(Model model, RewordMsg msg) {
        return switch (msg) {
            case Requested requested -> requested(model, requested);
            case Checked(var sha, var warning) -> ask(model, sha, warning);
            case PushedConfirmed() ->
                model.popup(RewordPushedPopup.class)
                        .map(popup -> PopupUpdate.ask(
                                model.withoutPopup(RewordPushedPopup.class), new RewordPopup(popup.commit())))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Any commit can be reworded; the dialog starts out with its message. When it is pushed already, a warning comes
     * first.
     */
    private static Next requested(Model model, Requested requested) {
        Sha sha = requested.sha();
        return model.commit(sha)
                .map(commit -> PushedCheck.first(model, sha, requested, () -> ask(model, sha, Option.none())))
                .getOrElse(() -> Next.of(model));
    }

    private static Next ask(Model model, Sha sha, Option<PushedWarning> warning) {
        return model.commit(sha)
                .map(commit -> warning.<Popup>map(pushed -> new RewordPushedPopup(commit, pushed))
                        .getOrElse(() -> new RewordPopup(commit)))
                .transform(popup -> PopupUpdate.ask(model, popup));
    }
}

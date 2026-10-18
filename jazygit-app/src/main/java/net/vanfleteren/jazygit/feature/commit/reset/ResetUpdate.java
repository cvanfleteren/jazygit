package net.vanfleteren.jazygit.feature.commit.reset;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Resetting the checked out branch to a commit or its upstream. A hard reset that would discard uncommitted changes is
 * confirmed first.
 */
public final class ResetUpdate {

    private ResetUpdate() {}

    public static Next update(Model model, ResetMsg msg) {
        return switch (msg) {
            case ResetMsg.Requested(Sha sha) ->
                Update.ask(
                        model,
                        model.repo().commit(sha).map(commit -> new ResetPopup(new ResetTarget.ToCommit(commit.sha()))));
            case ResetMsg.Cancelled() -> Next.of(model.withoutPopup(ResetPopup.class));
            case ResetMsg.Chosen(ResetMode mode) -> chosen(model, mode);
            case ResetMsg.HardConfirmed() ->
                Update.confirm(
                        model, HardResetPopup.class, popup -> Option.of(new ResetCmd(popup.target(), ResetMode.HARD)));
            case ResetMsg.HardCancelled() -> Next.of(model.withoutPopup(HardResetPopup.class));
        };
    }

    /**
     * A hard reset that would discard changes asks first; any other reset starts.
     */
    private static Next chosen(Model model, ResetMode mode) {
        Option<Integer> discarded = discarded(model);
        boolean confirm = mode == ResetMode.HARD && !discarded.contains(0);
        return confirm
                ? model.popup(ResetPopup.class)
                        .map(popup -> Update.ask(
                                model.withoutPopup(ResetPopup.class), new HardResetPopup(popup.target(), discarded)))
                        .getOrElse(() -> Next.of(model))
                : Update.confirm(model, ResetPopup.class, popup -> Option.of(new ResetCmd(popup.target(), mode)));
    }

    /**
     * How many files a hard reset discards changes of: all but the untracked ones, which it leaves alone. None
     * when the status is not known.
     */
    private static Option<Integer> discarded(Model model) {
        return model.repo()
                .status()
                .toOption()
                .map(RepoStatus::files)
                .map(files -> files.count(file -> file.type() != ChangeType.UNTRACKED));
    }
}

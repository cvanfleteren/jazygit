package net.vanfleteren.jazygit.feature.commit.fixup;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.squash.SquashCmd;
import net.vanfleteren.jazygit.feature.commit.squash.SquashUpdate;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Fixing up a commit into the one below it: squashing it, keeping only one of the two messages.
 */
public final class FixupUpdate {

    private FixupUpdate() {}

    public static Next update(Model model, FixupMsg msg) {
        return switch (msg) {
            case FixupMsg.Requested(Sha sha) -> requested(model, sha);
            case FixupMsg.Chosen(SquashMode mode) ->
                Update.confirm(
                        model,
                        FixupPopup.class,
                        popup -> Option.of(new SquashCmd(popup.commit().sha(), mode)));
        };
    }

    /**
     * Only a commit that can be squashed can be fixed up: one with exactly one parent.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha)
                .flatMap(commit -> SquashUpdate.onlyParent(commit).map(into -> new FixupPopup(commit, into)))
                .transform(popup -> Update.ask(model, popup));
    }
}

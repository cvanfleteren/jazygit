package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.feature.commit.amend.AmendAction;
import net.vanfleteren.jazygit.feature.commit.create.CreateCommitAction;
import net.vanfleteren.jazygit.feature.commit.drop.DropAction;
import net.vanfleteren.jazygit.feature.commit.fixup.FixupAction;
import net.vanfleteren.jazygit.feature.commit.reset.ResetAction;
import net.vanfleteren.jazygit.feature.commit.revert.RevertAction;
import net.vanfleteren.jazygit.feature.commit.reword.RewordAction;
import net.vanfleteren.jazygit.feature.commit.search.CommitSearchAction;
import net.vanfleteren.jazygit.feature.commit.squash.SquashAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The commit operations: committing, amending (the last or any commit), rewording, squashing, fixing up, dropping,
 * reverting, resetting, and searching them. Adding one is adding its subpackage, and listing its action here.
 */
public final class CommitFeature {

    /**
     * The operations, in the order their keys are listed in a panel.
     */
    private static final List<FeatureAction> ACTIONS = List.of(
            CreateCommitAction.ACTION,
            AmendAction.ACTION,
            RewordAction.ACTION,
            SquashAction.ACTION,
            FixupAction.ACTION,
            DropAction.ACTION,
            RevertAction.ACTION,
            ResetAction.ACTION,
            CommitSearchAction.ACTION);

    private CommitFeature() {}

    public static Keymap register(Keymap keys) {
        return FeatureAction.register(ACTIONS, keys);
    }

    /**
     * The popups of the commit operations, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return FeatureAction.popups(ACTIONS, dispatch);
    }
}

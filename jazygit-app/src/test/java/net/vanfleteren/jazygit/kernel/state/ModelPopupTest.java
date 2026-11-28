package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.delete.DeleteBranchPopup;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendPopup;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendTarget;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import org.junit.jupiter.api.Test;

/**
 * At most one popup is open, and closing is specific to the kind of popup.
 */
class ModelPopupTest {

    private static final DeleteBranchPopup FEATURE_POPUP = new DeleteBranchPopup(
            new BranchName("feature"),
            new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, new BranchName("feature")),
            Option.none());

    private final Model closed = TestModels.loaded();

    @Test
    void openingAPopupReplacesTheOneThatWasOpen() {
        Model model =
                closed.openPopup(new AmendPopup(new AmendTarget.LastCommit())).openPopup(FEATURE_POPUP);

        assertEquals(Option.of(FEATURE_POPUP), model.popup());
        assertEquals(Option.none(), model.popup(AmendPopup.class));
    }

    @Test
    void closingOnlyClosesAPopupOfThatKind() {
        Model model = closed.openPopup(FEATURE_POPUP);

        assertEquals(model, model.withoutPopup(AmendPopup.class));
        assertEquals(Option.none(), model.withoutPopup(DeleteBranchPopup.class).popup());
    }

    @Test
    void theFocusOutsideThePopupsIsKeptThroughAPopup() {
        assertEquals(Option.none(), closed.repoView().focusedPane());

        Model focused = Update.update(closed, new ViewMsg.Focused(new PaneId("branches")))
                .model();

        assertEquals(Option.of(new PaneId("branches")), focused.repoView().focusedPane());
        assertEquals(
                Option.of(new PaneId("branches")),
                focused.openPopup(new AmendPopup(new AmendTarget.LastCommit()))
                        .withoutPopup(AmendPopup.class)
                        .repoView()
                        .focusedPane());
    }
}

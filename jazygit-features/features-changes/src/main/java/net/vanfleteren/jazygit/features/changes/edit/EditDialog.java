package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Cell;

/**
 * The menu of the editors a file can be opened in (ADR 145), as that of a worktree is (ADR 110): each with the letter
 * that chooses it and its name. Escape goes back to the popup under it, if any (ADR 206).
 */
public final class EditDialog {

    public static final PickDialog.Spec<EditPopup, EditorProgram> MENU = new PickDialog.Spec<>(
            "edit-file",
            EditPopup.class,
            popup -> popup.title()
                    .map(ErrorText::of)
                    .getOrElse(() -> Messages.get(
                            "changes.dialog.edit.title", popup.file().name())),
            popup -> popup.editors()
                    .map(lettered -> new PickDialog.Row<>(lettered._2, List.of(Cell.of(lettered._2.name())))
                            .withKey(lettered._1)),
            EditMsg.Chosen::new,
            EditMsg.Cancelled::new,
            Option.none());

    private EditDialog() {}
}

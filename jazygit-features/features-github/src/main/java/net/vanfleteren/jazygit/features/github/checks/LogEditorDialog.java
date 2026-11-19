package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Cell;

/**
 * The menu of the editors the log of a check can be opened in (ADR 160), as that of a file is (ADR 145): each with
 * the letter that chooses it and its name. Escape goes back to the checks.
 */
public final class LogEditorDialog {

    public static final PickDialog.Spec<LogEditorPopup, EditorProgram> MENU = new PickDialog.Spec<>(
            "check-log-editor",
            LogEditorPopup.class,
            popup -> Messages.get("github.dialog.logEditor.title", popup.name()),
            popup -> popup.editors()
                    .map(lettered -> new PickDialog.Row<>(lettered._2, List.of(Cell.of(lettered._2.name())))
                            .withKey(lettered._1)),
            ChecksMsg.LogEditorChosen::new,
            ChecksMsg.LogEditorCancelled::new,
            Option.none());

    private LogEditorDialog() {}
}

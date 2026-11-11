package net.vanfleteren.jazygit.feature.worktree.open;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog.Cell;

/**
 * The menu of the editors a worktree can be opened in (ADR 110): each with the letter that chooses it and its name.
 * Not its command, which is noise in a menu.
 */
public final class OpenInEditorDialog {

    public static final PickDialog.Spec<OpenInEditorPopup, EditorProgram> MENU = new PickDialog.Spec<>(
            "open-in-editor",
            OpenInEditorPopup.class,
            popup -> Messages.get("dialog.openInEditor.title", name(popup.worktree())),
            popup -> popup.editors()
                    .map(lettered -> new PickDialog.Row<>(lettered._2, List.of(Cell.of(lettered._2.name())))
                            .withKey(lettered._1)),
            OpenInEditorMsg.Chosen::new,
            Option.none());

    private OpenInEditorDialog() {}

    /**
     * The name of the directory of the worktree at {@code location}, as the worktrees call it.
     */
    private static String name(String location) {
        return Option.of(Path.of(location).getFileName()).map(Path::toString).getOrElse(location);
    }
}

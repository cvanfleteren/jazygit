package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which of the editors the user set to open files opens the log of a check (ADR 160, 195), over the checks
 * popup it goes back to, as the menu of a file does (ADR 145).
 *
 * @param checks   the checks popup it goes back to, scrolled as {@code scrolled}; none when that was closed before the
 *                 log was read
 * @param scrolled how far the checks popup was scrolled
 * @param name     the name of the check whose log it is
 * @param file     the file the log is kept in
 * @param line     the line it opens at, counted from 1
 * @param editors  each editor that opens files with the letter that chooses it, in the order of the settings file
 */
public record LogEditorPopup(
        Option<ForgejoChecksPopup> checks,
        ListCursor scrolled,
        String name,
        String file,
        int line,
        List<Tuple2<Character, EditorProgram>> editors)
        implements Popup {

    /**
     * This, over {@code checks} as they are read meanwhile.
     */
    public LogEditorPopup over(ForgejoChecksPopup checks) {
        return new LogEditorPopup(Option.of(checks), scrolled, name, file, line, editors);
    }

    /**
     * {@code model} back to the checks popup, as far as it was scrolled; or without a popup when there is none.
     */
    public Model back(Model model) {
        return checks.map(popup -> model.openPopup(popup).withListCursor(scrolled))
                .getOrElse(() -> model.withoutPopup(LogEditorPopup.class));
    }
}

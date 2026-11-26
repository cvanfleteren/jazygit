package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.popup.Covering;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which of the editors the user set to open files opens {@code path} (ADR 145), over the popup that was open,
 * which it goes back to (ADR 206).
 *
 * @param path        the file, relative to the working tree, or absolute
 * @param line        the line it opens at, counted from 1
 * @param editors     each editor that opens files with the letter that chooses it, in the order of the settings file
 * @param title       what the menu is titled, when it is not named by the file
 * @param under       the popup it goes back to, if one was open
 * @param underCursor where the user was in the list of that popup
 */
public record EditPopup(
        String path,
        int line,
        List<Tuple2<Character, EditorProgram>> editors,
        Option<Phrase> title,
        Option<Popup> under,
        ListCursor underCursor)
        implements Covering {

    /**
     * Asking which of {@code editors} opens {@code path} at {@code line}, over no popup, titled by the file.
     */
    public EditPopup(String path, int line, List<Tuple2<Character, EditorProgram>> editors) {
        this(path, line, editors, Option.none(), Option.none(), ListCursor.START);
    }

    @Override
    public EditPopup over(Popup popup) {
        return new EditPopup(path, line, editors, title, Option.of(popup), underCursor);
    }

    /**
     * {@code model} back to the popup under this one, where the user was in it; or without a popup when there is none.
     */
    public Model back(Model model) {
        return under.map(popup -> model.openPopup(popup).withListCursor(underCursor))
                .getOrElse(() -> model.withoutPopup(EditPopup.class));
    }
}

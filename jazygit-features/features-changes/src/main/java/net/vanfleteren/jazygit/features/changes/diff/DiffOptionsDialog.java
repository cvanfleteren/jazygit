package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.DiffContext;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.preferences.DiffFilters;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Cell;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Row;

/**
 * The popup of the diff options: the style the diffs are drawn in and the lines of context, which the arrows to the
 * sides step through and Enter steps forward, the switches of the changed words and of whitespace, which Space or Enter
 * switches, and the diff filters, which Space switches all off or on again and Enter opens the popup of. Each change
 * shows at once; the popup stays open.
 */
public final class DiffOptionsDialog {

    public static final PickDialog.Spec<DiffOptionsPopup, Msg> SPEC = new PickDialog.Spec<>(
                    "diff-options",
                    DiffOptionsPopup.class,
                    popup -> Messages.get("changes.dialog.diffOptions.title"),
                    DiffOptionsDialog::rows,
                    msg -> msg)
            .pickedBySpace()
            .hintedBy("changes.dialog.diffOptions.hint");

    /**
     * The row of the diff filters, which the popup of the filters goes back to.
     */
    static final int FILTERS_ROW = 4;

    private static final List<DiffStyle> STYLES = List.of(DiffStyle.values());

    private DiffOptionsDialog() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        // Undimmed: each change shows at once in the diff behind it.
        return List.of(
                PopupSlot.returningToFocus(new PickDialog<>(SPEC, dispatch)).undimmed());
    }

    /**
     * The rows of {@code popup}: the style, the context, the changed words, whitespace and the filters.
     */
    static List<Row<Msg>> rows(DiffOptionsPopup popup) {
        DiffStyle style = popup.style();
        DiffOptionsMsg nextStyle = new DiffOptionsMsg.StyleChosen(step(style, 1));
        Row<Msg> styleRow = new Row<Msg>(
                        nextStyle,
                        List.of(
                                Cell.of(Messages.get("changes.dialog.diffOptions.style")),
                                Cell.of(stepped(Messages.get("changes.dialog.diffOptions.style." + style.id()))),
                                Cell.styled(Messages.get("changes.dialog.diffOptions.saved"), Theme.Classes.DIM)))
                .withSteps(new DiffOptionsMsg.StyleChosen(step(style, -1)), nextStyle);
        Row<Msg> contextRow = new Row<Msg>(
                        new DiffOptionsMsg.ContextWidened(),
                        List.of(
                                Cell.of(Messages.get("changes.dialog.diffOptions.context")),
                                Cell.of(stepped(context(popup.context()))),
                                Cell.styled(
                                        Messages.get("changes.dialog.diffOptions.context.note"), Theme.Classes.DIM)))
                .withSteps(new DiffOptionsMsg.ContextNarrowed(), new DiffOptionsMsg.ContextWidened());
        Row<Msg> wordsRow = new Row<>(
                new DiffOptionsMsg.WordsToggled(),
                style.rich()
                        ? List.of(
                                Cell.of(Messages.get("changes.dialog.diffOptions.words")),
                                Cell.of(DiffFiltersDialog.box(popup.words())),
                                Cell.styled(Messages.get("changes.dialog.diffOptions.saved"), Theme.Classes.DIM))
                        : List.of(
                                Cell.styled(Messages.get("changes.dialog.diffOptions.words"), Theme.Classes.DISABLED),
                                Cell.styled(DiffFiltersDialog.box(popup.words()), Theme.Classes.DISABLED),
                                Cell.styled(
                                        Messages.get("changes.dialog.diffOptions.words.plain"), Theme.Classes.DIM)));
        Row<Msg> whitespaceRow = new Row<>(
                new DiffOptionsMsg.WhitespaceToggled(),
                List.of(
                        Cell.of(Messages.get("changes.dialog.diffOptions.whitespace")),
                        Cell.of(DiffFiltersDialog.box(popup.ignoreWhitespace())),
                        Cell.styled(Messages.get("changes.dialog.diffOptions.session"), Theme.Classes.DIM)));
        return List.of(styleRow, contextRow, wordsRow, whitespaceRow, filtersRow(popup.filters()));
    }

    private static Row<Msg> filtersRow(DiffFilters filters) {
        String state = filters.off()
                ? Messages.get("changes.dialog.diffOptions.filters.off")
                : Messages.get(
                        "changes.dialog.diffOptions.filters.on",
                        filters.enabledFilters().size(),
                        filters.all().size());
        return new Row<Msg>(
                        new DiffFiltersMsg.Requested(),
                        List.of(
                                Cell.of(Messages.get("changes.dialog.diffOptions.filters")),
                                Cell.of(DiffFiltersDialog.box(!filters.off()) + " " + state),
                                Cell.styled(
                                        Messages.get("changes.dialog.diffOptions.filters.note"), Theme.Classes.DIM)))
                .withSpace(new DiffFiltersMsg.AllToggled());
    }

    /**
     * The style {@code by} steps from {@code style}, past the last going round to the first.
     */
    private static DiffStyle step(DiffStyle style, int by) {
        return STYLES.get(Math.floorMod(STYLES.indexOf(style) + by, STYLES.size()));
    }

    /**
     * The value of a row the arrows to the sides step through, showing they do.
     */
    private static String stepped(String value) {
        return "‹ " + value + " ›";
    }

    private static String context(DiffContext context) {
        return context.wholeFile()
                ? Messages.get("changes.dialog.diffOptions.context.wholeFile")
                : Messages.get("changes.dialog.diffOptions.context.lines", context.lines());
    }
}

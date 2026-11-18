package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.DiffFilter;
import net.vanfleteren.jazygit.settings.FilterDefinition;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.preferences.DiffFilters;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog.Cell;

/**
 * The popup of the diff filters (ADR 144), opened from the one of the diff options, which Escape goes back to: each
 * filter, enabled or not, the built-in ones by language before those by path, then the user's. Space or Enter switches
 * the highlighted row, at once; the popup stays open. A filter that cannot be used says why, and is not enabled. The
 * switch that turns them all off for this session is in the popup of the options.
 */
public final class DiffFiltersDialog {

    public static final PickDialog.Spec<DiffFiltersPopup, DiffFiltersMsg> SPEC = new PickDialog.Spec<>(
                    "diff-filters",
                    DiffFiltersPopup.class,
                    popup -> Messages.get("changes.dialog.diffFilters.title"),
                    popup -> rows(popup.filters()),
                    msg -> msg)
            .pickedBySpace()
            .cancelledBy(DiffFiltersMsg.Closed::new)
            .hintedBy("changes.dialog.diffFilters.hint");
    /**
     * The columns of what a filter applies to shown at most.
     */
    private static final int SCOPE = 50;

    private DiffFiltersDialog() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningToFocus(new PickDialog<>(SPEC, dispatch)));
    }

    /**
     * The rows of {@code filters}, one for each filter.
     */
    static List<PickDialog.Row<DiffFiltersMsg>> rows(DiffFilters filters) {
        List<DiffFilters.Choice> ordered = filters.all()
                .filter(choice -> choice.definition().builtIn()
                        && !choice.definition().languages().isEmpty())
                .appendAll(filters.all()
                        .filter(choice -> choice.definition().builtIn()
                                && choice.definition().languages().isEmpty()))
                .appendAll(filters.all().filter(choice -> !choice.definition().builtIn()));
        return ordered.map(DiffFiltersDialog::row);
    }

    private static PickDialog.Row<DiffFiltersMsg> row(DiffFilters.Choice choice) {
        FilterDefinition definition = choice.definition();
        String scope = definition.languages().isEmpty()
                ? String.join(", ", definition.paths())
                : String.join(", ", definition.languages());
        Cell said = choice.filter()
                .fold(
                        problem -> Cell.styled(problem(problem), Theme.Classes.ERROR),
                        filter -> Cell.styled(
                                definition.builtIn() ? scope : Messages.get("changes.dialog.diffFilters.yours", scope),
                                Theme.Classes.DIM));
        return new PickDialog.Row<>(
                new DiffFiltersMsg.Toggled(definition.id()),
                List.of(
                        Cell.of(box(choice.enabled() && choice.filter().isRight())),
                        choice.filter().isRight()
                                ? Cell.of(choice.name())
                                : Cell.styled(choice.name(), Theme.Classes.DISABLED),
                        said.capped(SCOPE)));
    }

    static String box(boolean on) {
        return on ? "[x]" : "[ ]";
    }

    private static String problem(DiffFilter.Problem problem) {
        return switch (problem) {
            case DiffFilter.Problem.NoScope() -> Messages.get("changes.dialog.diffFilters.noScope");
            case DiffFilter.Problem.UnknownLanguage(String name) ->
                Messages.get("changes.dialog.diffFilters.unknownLanguage", name);
            case DiffFilter.Problem.BadPattern(String pattern, String reason) ->
                Messages.get("changes.dialog.diffFilters.badPattern", pattern, reason);
        };
    }
}

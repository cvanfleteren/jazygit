package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.popup.ListCursor;
import net.vanfleteren.jazygit.state.preferences.SaveSettings;

/**
 * Choosing the diff filters (ADR 144). Which are enabled is kept in the settings, and saved at each change; whether
 * all are switched off holds for this session only. Either shows at once: the filters hide hunks as the diff is drawn,
 * so nothing is read again.
 */
public final class DiffFiltersUpdate {

    private DiffFiltersUpdate() {}

    public static Next update(Model model, DiffFiltersMsg msg) {
        return switch (msg) {
            // Opened from the popup of the diff options, which it replaces.
            case DiffFiltersMsg.Requested()
            when model.popup().isDefined()
                    && model.popup(DiffOptionsPopup.class).isEmpty() -> Next.of(model);
            case DiffFiltersMsg.Requested() ->
                Next.of(model.openPopup(new DiffFiltersPopup(model.preferences().diffFilters())));
            case DiffFiltersMsg.Closed()
            when model.popup(DiffFiltersPopup.class).isEmpty() -> Next.of(model);
            case DiffFiltersMsg.Closed() ->
                Next.of(model.openPopup(DiffOptionsPopup.of(model.preferences()))
                        .withListCursor(new ListCursor(Option.of(DiffOptionsDialog.FILTERS_ROW), Option.none())));
            case DiffFiltersMsg.Toggled(String id) -> toggled(model, id);
            case DiffFiltersMsg.AllToggled() -> {
                Model switched =
                        model.mapPreferences(preferences -> preferences.withFiltersOff(!preferences.filtersOff()));
                yield Next.of(shown(switched));
            }
        };
    }

    /**
     * Enables the filter {@code id}, after those enabled before it, or disables it; saved at once. One that cannot be
     * used is not enabled.
     */
    private static Next toggled(Model model, String id) {
        boolean usable = model.preferences()
                .diffFilters()
                .all()
                .exists(choice ->
                        choice.definition().id().equals(id) && choice.filter().isRight());
        Settings previous = model.preferences().settings();
        List<String> enabled = previous.diffFilters();
        if (!usable && !enabled.contains(id)) {
            return Next.of(model);
        }
        Settings chosen = previous.withDiffFilters(enabled.contains(id) ? enabled.remove(id) : enabled.append(id));
        return Next.of(
                shown(model.mapPreferences(preferences -> preferences.withSettings(chosen))),
                new SaveSettings(previous, chosen));
    }

    /**
     * {@code model} with the popup of the filters, or the one of the options, showing them as they are now, if it is
     * open.
     */
    private static Model shown(Model model) {
        return DiffOptionsUpdate.shown(model.popup(DiffFiltersPopup.class)
                .map(open ->
                        model.openPopup(new DiffFiltersPopup(model.preferences().diffFilters())))
                .getOrElse(model));
    }
}

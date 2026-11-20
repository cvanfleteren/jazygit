package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;

/**
 * Editing a list of the settings from the settings panel (ADR 164): its entries are added, edited, deleted and moved,
 * and each change is saved through {@link SettingChange#set}, as any other setting is. An entry is edited in a form,
 * which saves it only once nothing wrong with it blocks that.
 */
public final class ListChange {

    private ListChange() {}

    public static Next update(Model model, ListMsg msg) {
        return switch (msg) {
            case ListMsg.Opened(ListSetting<?> list, char row) -> opened(model, list, row);
            case ListMsg.Closed() -> closed(model);
            case ListMsg.AddRequested() -> onList(model, popup -> form(model, popup, popup.list(), Option.none()));
            case ListMsg.EditRequested(int index) ->
                onList(model, popup -> form(model, popup, popup.list(), Option.some(index)));
            case ListMsg.Deleted(int index) -> onList(model, popup -> deleted(model, popup.list(), index));
            case ListMsg.Moved(int index, int by) -> onList(model, popup -> moved(model, popup.list(), index, by));
            case ListMsg.FieldPicked(int field) -> onForm(model, form -> fieldPicked(model, form, field));
            case ListMsg.FieldEntered() -> fieldEntered(model);
            case ListMsg.FieldCancelled() ->
                Next.of(model.popup(FieldPopup.class)
                        .map(popup -> model.openPopup(popup.form()).withListCursor(ListCursor.START.at(popup.field())))
                        .getOrElse(model));
            case ListMsg.FormSaved() -> onForm(model, form -> formSaved(model, form));
            case ListMsg.FormCancelled() -> onForm(model, form -> listAt(model, form.back(), form.index()));
        };
    }

    /**
     * Opens {@code list} from the settings panel, which goes back to the row with the letter {@code row}.
     */
    private static Next opened(Model model, ListSetting<?> list, char row) {
        return Next.of(model.popup(SettingsPanelPopup.class)
                .map(panel -> model.openPopup(new ListPopup(list, panel.withOpensAt(Option.some(row)))))
                .getOrElse(model));
    }

    /**
     * The settings panel again, showing the settings as they are now.
     */
    private static Next closed(Model model) {
        return Next.of(model.popup(ListPopup.class)
                .map(popup -> model.openPopup(
                        popup.panel().withSettings(model.preferences().settings())))
                .getOrElse(model));
    }

    private static Next onList(Model model, Function<ListPopup, Next> then) {
        return model.popup(ListPopup.class).map(then).getOrElse(() -> Next.of(model));
    }

    private static Next onForm(Model model, Function<FormPopup<?>, Next> then) {
        return model.popup(FormPopup.class)
                .<FormPopup<?>>map(form -> form)
                .map(then)
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The form of a new entry, or of the one at {@code index} when the app could read it.
     */
    private static <E> Next form(Model model, ListPopup popup, ListSetting<E> list, Option<Integer> index) {
        Entries<E> entries = model.preferences().settings().get(list.key());
        Option<E> draft = index.fold(
                () -> Option.some(list.form().blank()), at -> entries.at(at).flatMap(Entries.Entry::value));
        return Next.of(draft.map(entry -> model.openPopup(FormPopup.of(list, popup.panel(), index, entry)))
                .getOrElse(model));
    }

    private static <E> Next deleted(Model model, ListSetting<E> list, int index) {
        Entries<E> entries = model.preferences().settings().get(list.key());
        return entries.at(index).isEmpty()
                ? Next.of(model)
                : saved(
                        model,
                        list,
                        entries.removed(index),
                        Math.max(0, Math.min(index, entries.all().size() - 2)));
    }

    private static <E> Next moved(Model model, ListSetting<E> list, int index, int by) {
        Entries<E> entries = model.preferences().settings().get(list.key());
        Entries<E> moved = entries.moved(index, by);
        return moved.equals(entries) ? Next.of(model) : saved(model, list, moved, index + by);
    }

    /**
     * Keeps {@code entries} for {@code list}, with the list open on the entry at {@code cursor}.
     */
    private static <E> Next saved(Model model, ListSetting<E> list, Entries<E> entries, int cursor) {
        return SettingChange.set(model, list.key(), entries)
                .map(changed -> changed.popup(ListPopup.class)
                        .map(popup -> changed.openPopup(new ListPopup(
                                popup.list(),
                                popup.panel().withSettings(changed.preferences().settings()))))
                        .getOrElse(changed)
                        .withListCursor(ListCursor.START.at(cursor)));
    }

    /**
     * Turns a field that is on or off the other way; asks for the value of any other.
     */
    private static <E> Next fieldPicked(Model model, FormPopup<E> form, int index) {
        if (index < 0 || index >= form.list().form().fields().size()) {
            return Next.of(model);
        }
        return Next.of(
                switch (form.list().form().fields().get(index)) {
                    case Field.Toggle<E> toggle ->
                        model.openPopup(form.drafting(toggle.set()
                                        .apply(form.draft(), !toggle.get().test(form.draft()))))
                                .withListCursor(ListCursor.START.at(index));
                    case Field.Text<E> text ->
                        model.openPopup(new FieldPopup(form, index, text.get().apply(form.draft())));
                    case Field.Letter<E> letter ->
                        model.openPopup(new FieldPopup(
                                form,
                                index,
                                letter.get()
                                        .apply(form.draft())
                                        .map(String::valueOf)
                                        .getOrElse("")));
                });
    }

    /**
     * The form, with the value typed for its field.
     */
    private static Next fieldEntered(Model model) {
        return model.popup(FieldPopup.class)
                .map(popup -> Next.of(model.openPopup(entered(popup.form(), popup.field(), popup.text()))
                        .withListCursor(ListCursor.START.at(popup.field()))))
                .getOrElse(() -> Next.of(model));
    }

    private static <E> FormPopup<E> entered(FormPopup<E> form, int index, String text) {
        E draft = form.draft();
        return form.drafting(
                switch (form.list().form().fields().get(index)) {
                    case Field.Text<E> field -> field.set().apply(draft, text);
                    case Field.Letter<E> field ->
                        field.set()
                                .apply(
                                        draft,
                                        Option.of(text.strip())
                                                .filter(typed -> !typed.isEmpty())
                                                .map(typed -> typed.charAt(0)));
                    case Field.Toggle<E> field -> draft;
                });
    }

    /**
     * Saves the entry of {@code form}, back to the list on it, unless something wrong with it blocks that.
     */
    private static <E> Next formSaved(Model model, FormPopup<E> form) {
        Entries<E> entries = model.preferences().settings().get(form.list().key());
        if (!form.list().form().savable(form.draft(), form.others(entries))) {
            return Next.of(model);
        }
        Entries<E> next = form.index()
                .map(index -> entries.replaced(index, form.draft()))
                .getOrElse(() -> entries.added(form.draft()));
        Model listed = model.openPopup(form.back());
        return saved(
                listed, form.list(), next, form.index().getOrElse(next.all().size() - 1));
    }

    /**
     * {@code popup} open, on the entry at {@code index}, if any.
     */
    private static Next listAt(Model model, ListPopup popup, Option<Integer> index) {
        Model listed = model.openPopup(popup);
        return Next.of(
                index.map(at -> listed.withListCursor(ListCursor.START.at(at))).getOrElse(listed));
    }
}

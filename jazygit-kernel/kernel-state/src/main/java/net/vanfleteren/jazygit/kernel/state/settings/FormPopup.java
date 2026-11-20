package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * The form editing an entry of a list of the settings, as far as the user changed it (ADR 164). Nothing is saved until
 * the form is.
 *
 * @param list    the list the entry is in
 * @param panel   the settings panel the list was opened from
 * @param index   where the entry is in the list; none for a new one, which saving adds at the end
 * @param draft   the entry as the user changed it so far
 * @param changed whether the user changed anything yet
 * @param <E>     what an entry is
 */
public record FormPopup<E>(
        ListSetting<E> list, SettingsPanelPopup panel, Option<Integer> index, E draft, boolean changed)
        implements Popup {

    /**
     * The form of {@code draft}, as it is in the list at {@code index}, or new, before the user changed it.
     */
    public static <E> FormPopup<E> of(ListSetting<E> list, SettingsPanelPopup panel, Option<Integer> index, E draft) {
        return new FormPopup<>(list, panel, index, draft, false);
    }

    @Override
    public boolean holdsInput() {
        return changed;
    }

    /**
     * This form, going back to {@code panel} through the list.
     */
    public FormPopup<E> withPanel(SettingsPanelPopup panel) {
        return new FormPopup<>(list, panel, index, draft, changed);
    }

    /**
     * This form with the entry changed to {@code draft}.
     */
    public FormPopup<E> drafting(E draft) {
        return new FormPopup<>(list, panel, index, draft, true);
    }

    /**
     * The other entries of the list, in {@code entries}, that the entry is checked against: those before it and
     * those after it; all before a new one, which goes at the end.
     */
    public Others<E> others(Entries<E> entries) {
        int at = index.getOrElse(entries.all().size());
        List<Tuple2<Entries.Entry<E>, Integer>> numbered = entries.all().zipWithIndex();
        return new Others<>(
                numbered.filter(entry -> entry._2 < at).flatMap(entry -> entry._1.value()),
                numbered.filter(entry -> entry._2 > at).flatMap(entry -> entry._1.value()));
    }

    /**
     * What is wrong with the entry, given {@code entries}, the list as it is.
     */
    public List<FieldProblem> problems(Entries<E> entries) {
        return list.form().problems(draft, others(entries));
    }

    /**
     * The list back from the form.
     */
    public ListPopup back() {
        return new ListPopup(list, panel);
    }
}

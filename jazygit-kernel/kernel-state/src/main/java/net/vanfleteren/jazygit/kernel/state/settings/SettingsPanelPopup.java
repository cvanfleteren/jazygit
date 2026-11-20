package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import lombok.With;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * The settings panel, while the user looks at it: the settings it shows, and what each of its sections keeps for
 * itself, as what it read when the panel opened (ADR 163). The sections come from the features, so the panel names
 * none of them.
 *
 * @param settings the settings shown
 * @param sections what each section keeps, by the name of its {@link SectionState}; one not kept is at its initial
 *                 value
 * @param opensAt  the key of the setting highlighted when the panel opens, like the one a dialog was opened from that
 *                 cancelling goes back from; else the first
 */
@With
public record SettingsPanelPopup(Settings settings, Map<String, Object> sections, Option<Character> opensAt)
        implements Popup {

    /**
     * The panel showing {@code settings}, with each section at its initial state, opening on the first setting.
     */
    public SettingsPanelPopup(Settings settings) {
        this(settings, HashMap.empty(), Option.none());
    }

    /**
     * What the section of {@code state} keeps: its initial value while it keeps nothing.
     */
    @SuppressWarnings("unchecked") // Only with() keeps a value under a state's name, one of that state's type.
    public <S> S section(SectionState<S> state) {
        return sections.get(state.name()).map(value -> (S) value).getOrElse(state.initial());
    }

    /**
     * This panel, with {@code value} kept for the section of {@code state}; nothing kept when that is its initial
     * value, so two panels that show the same are equal.
     */
    public <S> SettingsPanelPopup with(SectionState<S> state, S value) {
        return withSections(
                value.equals(state.initial()) ? sections.remove(state.name()) : sections.put(state.name(), value));
    }

    /**
     * {@code model} with {@code change} made to the settings panel that is open, or that the list, the form or the
     * field open over it go back to (ADR 164), so what reaches the panel meanwhile, as the settings file read as it
     * opened, is there when they draw from it and when the user goes back to it; as it is when no panel is.
     */
    public static Model changed(Model model, UnaryOperator<SettingsPanelPopup> change) {
        return model.popup(SettingsPanelPopup.class)
                .<Popup>map(change)
                .orElse(() -> model.popup(ListPopup.class).map(list -> list.withPanel(change.apply(list.panel()))))
                .orElse(() -> model.popup(FormPopup.class)
                        .<FormPopup<?>>map(form -> form)
                        .map(form -> withPanel(form, change)))
                .orElse(() ->
                        model.popup(FieldPopup.class).map(field -> field.withForm(withPanel(field.form(), change))))
                .map(model::openPopup)
                .getOrElse(model);
    }

    private static <E> FormPopup<E> withPanel(FormPopup<E> form, UnaryOperator<SettingsPanelPopup> change) {
        return form.withPanel(change.apply(form.panel()));
    }
}

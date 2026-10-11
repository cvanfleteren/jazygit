package net.vanfleteren.jazygit.keys;

import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.state.Msg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The single source of truth for keyboard shortcuts: every key is declared once, with its description, and
 * the panels both react to the keys and the help popup lists them from here. A feature declares the keys it
 * adds to a panel ({@link Scope}) or to the whole app ({@link Global}) in its own {@code *Keys} class; only
 * {@link #standard()} lists those classes.
 */
public final class Keymap {

    private static final String HELP_KEY = "?";

    /**
     * A panel that has keys of its own.
     *
     * @param id       the id of the panel
     * @param titleKey the message key of the panel's title
     * @param <S>      what is selected in the panel, which its keys act on
     */
    public record Scope<S>(String id, String titleKey) {
    }

    /**
     * A key in a panel.
     *
     * @param key            the text of the key event, {@code " "} for space
     * @param descriptionKey the message key of what the key does
     * @param action         the message to send for the key, given the selection if there is one
     * @param <S>            what is selected in the panel
     */
    public record Binding<S>(String key, String descriptionKey, Function<Optional<S>, Optional<Msg>> action) {

        /**
         * A key that acts on the selection, and does nothing when nothing is selected.
         */
        public static <S> Binding<S> onSelected(String key, String descriptionKey, Function<S, Msg> msg) {
            return new Binding<>(key, descriptionKey, selection -> selection.map(msg));
        }

        /**
         * A key that does the same whatever is selected.
         */
        public static <S> Binding<S> always(String key, String descriptionKey, Supplier<Msg> msg) {
            return new Binding<>(key, descriptionKey, selection -> Optional.of(msg.get()));
        }

        /**
         * How the key is shown to the user.
         */
        public String label() {
            return " ".equals(key) ? "space" : key;
        }
    }

    /**
     * A key that works wherever the focus is.
     *
     * @param label          how the key is shown to the user
     * @param descriptionKey the message key of what the key does
     * @param matches        whether an event is this key; never for a key that the framework handles
     * @param action         what it does
     */
    public record Global(String label, String descriptionKey, Predicate<KeyEvent> matches, Runnable action) {

        /**
         * A key that is only listed, because the framework handles it.
         */
        public static Global documented(String label, String descriptionKey) {
            return new Global(label, descriptionKey, event -> false, () -> { });
        }
    }

    private record Section(Scope<?> scope, List<Binding<?>> bindings) {
    }

    /**
     * Collects the keys of the features.
     */
    public static final class Builder {

        private final Map<String, Section> sections = new LinkedHashMap<>();
        private final List<Global> globals = new ArrayList<>();

        private Builder() {
        }

        @SafeVarargs
        public final <S> Builder add(Scope<S> scope, Binding<S>... bindings) {
            sections.computeIfAbsent(scope.id(), id -> new Section(scope, new ArrayList<>()))
                    .bindings().addAll(List.of(bindings));
            return this;
        }

        public Builder global(Global global) {
            globals.add(global);
            return this;
        }

        public Keymap build() {
            Map<String, Section> copy = new LinkedHashMap<>();
            sections.forEach((id, section) -> copy.put(id, new Section(section.scope(), List.copyOf(section.bindings()))));
            return new Keymap(Map.copyOf(copy), List.copyOf(globals));
        }
    }

    private final Map<String, Section> sections;
    private final List<Global> globals;

    private Keymap(Map<String, Section> sections, List<Global> globals) {
        this.sections = sections;
        this.globals = globals;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * What the keys of {@code scope} do in the event {@code event}: help for {@code ?}, otherwise the message
     * of the key, if the scope has it.
     *
     * @param selection what is selected in the panel, if anything
     */
    public <S> Optional<Msg> handle(Scope<S> scope, KeyEvent event, Optional<S> selection) {
        String text = event.string();
        if (HELP_KEY.equals(text)) {
            return Optional.of(new HelpMsg.Requested(scope.id()));
        }
        return Optional.ofNullable(sections.get(scope.id()))
                .stream()
                .flatMap(section -> section.bindings().stream())
                .filter(binding -> binding.key().equals(text))
                .findFirst()
                .flatMap(binding -> {
                    @SuppressWarnings("unchecked")
                    Binding<S> typed = (Binding<S>) binding;
                    return typed.action().apply(selection);
                });
    }

    /**
     * The message key of the title of the panel {@code scopeId}, if it has keys.
     */
    public Optional<String> titleKey(String scopeId) {
        return Optional.ofNullable(sections.get(scopeId)).map(section -> section.scope().titleKey());
    }

    /**
     * The keys of the panel {@code scopeId}, in the order the features declared them.
     */
    public List<Binding<?>> bindings(String scopeId) {
        return Optional.ofNullable(sections.get(scopeId)).map(Section::bindings).orElse(List.of());
    }

    public List<Global> globals() {
        return globals;
    }

    /**
     * The keys of the app, as declared by its features.
     */
    public static Keymap standard() {
        return Standard.KEYMAP;
    }
}

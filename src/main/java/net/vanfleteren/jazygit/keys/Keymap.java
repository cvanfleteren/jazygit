package net.vanfleteren.jazygit.keys;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.state.Msg;

import java.util.LinkedHashMap;
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
     * @param msg            the message to send for it
     */
    public record Global(String label, String descriptionKey, Predicate<KeyEvent> matches, Supplier<Msg> msg) {

        /**
         * A key that is only listed, because the framework handles it.
         */
        public static Global documented(String label, String descriptionKey) {
            return new Global(label, descriptionKey, event -> false, () -> {
                throw new IllegalStateException("Handled by the framework: " + label);
            });
        }
    }

    private record Section(Scope<?> scope, List<Binding<?>> bindings) {
    }

    /**
     * Collects the keys of the features.
     */
    public static final class Builder {

        private final Map<String, Section> sections = new LinkedHashMap<>();
        private List<Global> globals = List.empty();

        private Builder() {
        }

        @SafeVarargs
        public final <S> Builder add(Scope<S> scope, Binding<S>... bindings) {
            sections.compute(scope.id(), (id, existing) -> new Section(scope,
                    (existing == null ? List.<Binding<?>>empty() : existing.bindings()).appendAll(List.of(bindings))));
            return this;
        }

        public Builder global(Global global) {
            globals = globals.append(global);
            return this;
        }

        public Keymap build() {
            return new Keymap(Map.copyOf(sections), globals);
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
                .flatMap(section -> section.bindings().find(binding -> binding.key().equals(text)).toJavaOptional())
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

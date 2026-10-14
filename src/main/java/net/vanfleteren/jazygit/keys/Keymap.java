package net.vanfleteren.jazygit.keys;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;

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

    /**
     * The key that shows the keys of the panel that has the focus, and closes that help again.
     */
    public static final String HELP_KEY = "?";

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
    public record Binding<S>(String key, String descriptionKey, Function<Option<S>, Option<Msg>> action) {

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
            return new Binding<>(key, descriptionKey, selection -> Option.of(msg.get()));
        }

        /**
         * How the key is shown to the user.
         */
        public String label() {
            return " ".equals(key) ? Messages.get("key.space") : key;
        }
    }

    /**
     * A key that works wherever the focus is.
     */
    public sealed interface Global {

        /**
         * How the key is shown to the user.
         */
        String label();

        /**
         * The message key of what the key does.
         */
        String descriptionKey();

        /**
         * The message to send for {@code event}, if it is this key.
         */
        Option<Msg> handle(KeyEvent event);

        /**
         * A key that sends {@code msg}.
         *
         * @param matches whether an event is this key
         */
        record Dispatching(String label, String descriptionKey, Predicate<KeyEvent> matches, Supplier<Msg> msg)
                implements Global {

            @Override
            public Option<Msg> handle(KeyEvent event) {
                return matches.test(event) ? Option.of(msg.get()) : Option.none();
            }
        }

        /**
         * A key that is only listed, because the framework handles it.
         */
        record Documented(String label, String descriptionKey) implements Global {

            @Override
            public Option<Msg> handle(KeyEvent event) {
                return Option.none();
            }
        }
    }

    private record Section(Scope<?> scope, List<Binding<?>> bindings) {
    }

    private final Map<String, Section> sections;
    private final List<Global> globals;

    private Keymap(Map<String, Section> sections, List<Global> globals) {
        this.sections = sections;
        this.globals = globals;
    }

    /**
     * A keymap without keys, for the features to add theirs to.
     */
    public static Keymap empty() {
        return new Keymap(LinkedHashMap.empty(), List.empty());
    }

    /**
     * This keymap with {@code bindings} added to the keys of {@code scope}.
     */
    @SafeVarargs
    public final <S> Keymap add(Scope<S> scope, Binding<S>... bindings) {
        List<Binding<?>> all = bindings(scope.id()).appendAll(List.of(bindings));
        // A key bound twice would only do what it was bound to first, silently; the help key does nothing else.
        all.map(Binding::key).append(HELP_KEY).groupBy(key -> key).filterValues(keys -> keys.size() > 1).keySet()
                .headOption()
                .forEach(key -> {
                    throw new IllegalArgumentException("The key \"" + key + "\" is bound twice in " + scope.id());
                });
        return new Keymap(sections.put(scope.id(), new Section(scope, all)), globals);
    }

    /**
     * This keymap with the key {@code global} added.
     */
    public Keymap global(Global global) {
        return new Keymap(sections, globals.append(global));
    }

    /**
     * What the keys of {@code scope} do in the event {@code event}: help for {@code ?}, otherwise the message
     * of the key, if the scope has it.
     *
     * @param selection what is selected in the panel, if anything
     */
    public <S> Option<Msg> handle(Scope<S> scope, KeyEvent event, Option<S> selection) {
        String text = event.string();
        if (HELP_KEY.equals(text)) {
            return Option.of(new HelpMsg.Requested(scope.id()));
        }
        return sections.get(scope.id())
                .flatMap(section -> section.bindings().find(binding -> binding.key().equals(text)))
                .flatMap(binding -> {
                    @SuppressWarnings("unchecked")
                    Binding<S> typed = (Binding<S>) binding;
                    return typed.action().apply(selection);
                });
    }

    /**
     * The message key of the title of the panel {@code scopeId}, if it has keys.
     */
    public Option<String> titleKey(String scopeId) {
        return sections.get(scopeId).map(section -> section.scope().titleKey());
    }

    /**
     * The keys of the panel {@code scopeId}, in the order the features declared them.
     */
    public List<Binding<?>> bindings(String scopeId) {
        return sections.get(scopeId).map(Section::bindings).getOrElse(List.of());
    }

    public List<Global> globals() {
        return globals;
    }

    /**
     * The message of the global key {@code event} is, if it is one.
     */
    public Option<Msg> handleGlobal(KeyEvent event) {
        return globals.iterator().flatMap(global -> global.handle(event)).headOption();
    }

    /**
     * The keys of the app, as declared by its features.
     */
    public static Keymap standard() {
        return Standard.KEYMAP;
    }
}

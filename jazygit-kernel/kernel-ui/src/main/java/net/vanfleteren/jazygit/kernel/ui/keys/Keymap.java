package net.vanfleteren.jazygit.kernel.ui.keys;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.lib.base.Require;

/**
 * The single source of truth for keyboard shortcuts: every key is declared once, with its description, and
 * the panels both react to the keys and the help popup lists them from here. A feature declares the keys it
 * adds to a panel ({@link Scope}) in its own package, through its {@code Feature}; {@link #standard()} is the keymap
 * the app makes of those of every feature, and of the keys that work wherever the focus is ({@link Global}).
 */
public final class Keymap {

    /**
     * The key that shows the keys of the panel that has the focus, and closes that help again.
     */
    public static final String HELP_KEY = "?";

    /**
     * How a binding names the Escape key.
     */
    public static final String ESCAPE = "esc";

    /**
     * How a binding names the Enter key.
     */
    public static final String ENTER = "enter";

    private final Map<String, Section> sections;
    private final List<Global> globals;
    private final Option<BiFunction<String, List<Option<Msg>>, Msg>> help;
    private final Map<String, List<Scope<?>>> guests;

    private Keymap(
            Map<String, Section> sections,
            List<Global> globals,
            Option<BiFunction<String, List<Option<Msg>>, Msg>> help,
            Map<String, List<Scope<?>>> guests) {
        this.sections = sections;
        this.globals = globals;
        this.help = help;
        this.guests = guests;
    }

    /**
     * A keymap without keys, for the features to add theirs to, in which {@link #HELP_KEY} does nothing.
     */
    public static Keymap empty() {
        return new Keymap(LinkedHashMap.empty(), List.empty(), Option.none(), LinkedHashMap.empty());
    }

    /**
     * A keymap without keys, in which {@link #HELP_KEY} sends what {@code help} makes of the id of the panel and of
     * what each of its keys would send. The keymap names no feature, the help included (ADR 136).
     */
    public static Keymap withHelp(BiFunction<String, List<Option<Msg>>, Msg> help) {
        return new Keymap(LinkedHashMap.empty(), List.empty(), Option.of(help), LinkedHashMap.empty());
    }

    /**
     * The key {@code event} is, as {@link Binding#key() bindings} name it: the character, {@link #ESCAPE} or
     * {@link #ENTER}, prefixed with {@code ctrl+} and {@code alt+} when those are held. Shift is left out, as it is
     * already in the character. The keypad's Enter is Enter, as it confirms where the bindings of TamboUI are asked.
     */
    static String name(KeyEvent event) {
        return (event.hasCtrl() ? "ctrl+" : "")
                + (event.hasAlt() ? "alt+" : "")
                + (event.code() == KeyCode.ESCAPE
                        ? ESCAPE
                        : event.code() == KeyCode.ENTER || event.code() == KeyCode.KP_ENTER ? ENTER : event.string());
    }

    /**
     * The keys of the app, as declared by its features: the keymap of the {@link Source} on the classpath, which the
     * app gives. Without the app, as in the tests of the views, it is {@link #empty()}.
     */
    public static Keymap standard() {
        return Found.KEYMAP;
    }

    /**
     * This keymap with {@code bindings} added to the keys of {@code scope}.
     */
    @SafeVarargs
    public final <S> Keymap add(Scope<S> scope, Binding<S>... bindings) {
        List<Binding<?>> all = own(scope.id()).appendAll(List.of(bindings));
        Keymap added = new Keymap(sections.put(scope.id(), new Section(scope, all)), globals, help, guests);
        // The scope's own keys, and those of every scope that hosts it, as its keys are theirs as well (ADR 161).
        added.requireOnce(scope.id());
        guests.filterValues(hosted -> hosted.exists(guest -> guest.id().equals(scope.id())))
                .keySet()
                .forEach(added::requireOnce);
        return added;
    }

    /**
     * This keymap with the keys of {@code guest} among those of {@code host} as well, after its own: the keys of a
     * subject, as a failed build, that features of another group add to a view that shows it, without seeing the
     * view's own keys (ADR 161). A key of the guest that the host or another of its guests has is refused.
     */
    public Keymap host(Scope<?> host, Scope<?> guest) {
        Keymap hosting = new Keymap(
                sections,
                globals,
                help,
                guests.put(
                        host.id(), guests.get(host.id()).getOrElse(List.empty()).append(guest)));
        hosting.requireOnce(host.id());
        return hosting;
    }

    /**
     * Fails when a key of {@code scopeId}, with its guests, is bound twice: it would only do what it was bound to
     * first, silently; the help key does nothing else.
     */
    private void requireOnce(String scopeId) {
        Option<String> twice = bindings(scopeId)
                .map(Binding::key)
                .append(HELP_KEY)
                .groupBy(key -> key)
                .filterValues(keys -> keys.size() > 1)
                .keySet()
                .headOption();
        Require.that(twice.isEmpty(), () -> "The key \"" + twice.get() + "\" is bound twice in " + scopeId);
    }

    /**
     * This keymap with the key {@code global} added.
     */
    public Keymap global(Global global) {
        return new Keymap(sections, globals.append(global), help, guests);
    }

    /**
     * What the keys of {@code scope} do in the event {@code event}: help for {@code ?}, if this keymap has help,
     * otherwise the message of the key, if the scope has it. A key let go does nothing. The keys of the scopes it
     * hosts do nothing here, as they act on what {@code hosted} gives them.
     *
     * @param selection what is selected in the panel, if anything
     * @param hosted    what the scopes it hosts act on, each of the scope it is for (ADR 161)
     */
    public <S> Option<Msg> handle(Scope<S> scope, KeyEvent event, Option<S> selection, Hosted<?>... hosted) {
        if (event.isRelease()) {
            return Option.none();
        }
        List<Hosted<?>> subjects = List.of(hosted);
        if (HELP_KEY.equals(name(event))) {
            return help.map(requested -> requested.apply(scope.id(), actions(scope, selection, subjects)));
        }
        return sections.get(scope.id())
                .flatMap(section -> section.bindings().find(binding -> binding.matches(event)))
                .map(binding -> {
                    @SuppressWarnings("unchecked")
                    Binding<S> typed = (Binding<S>) binding;
                    return typed.action().apply(selection);
                })
                .getOrElse(() -> guestsOf(scope.id())
                        .iterator()
                        .flatMap(guest -> subject(guest, subjects).handled(this, event))
                        .headOption());
    }

    /**
     * What each key of {@code scope} would send for {@code selection}, in the order of {@link #bindings}: none
     * for a key that does nothing then, as a key of a scope it hosts, which acts on what a view gives it.
     */
    public <S> List<Option<Msg>> actions(Scope<S> scope, Option<S> selection) {
        return actions(scope, selection, List.empty());
    }

    private <S> List<Option<Msg>> actions(Scope<S> scope, Option<S> selection, List<Hosted<?>> hosted) {
        return own(scope.id())
                .map(binding -> {
                    @SuppressWarnings("unchecked")
                    Binding<S> typed = (Binding<S>) binding;
                    return typed.action().apply(selection);
                })
                .appendAll(guestsOf(scope.id())
                        .flatMap(guest -> subject(guest, hosted).actions(this)));
    }

    /**
     * The scopes {@code scopeId} hosts, in the order they were hosted.
     */
    private List<Scope<?>> guestsOf(String scopeId) {
        return guests.get(scopeId).getOrElse(List.empty());
    }

    /**
     * What {@code hosted} gives the scope {@code guest} to act on; nothing when it gives it nothing.
     */
    private static Hosted<?> subject(Scope<?> guest, List<Hosted<?>> hosted) {
        return hosted.find(subject -> subject.scope().id().equals(guest.id())).getOrElse(() -> Hosted.nothing(guest));
    }

    /**
     * The message key of the title of the panel {@code scopeId}, if it has keys.
     */
    public Option<String> titleKey(String scopeId) {
        return sections.get(scopeId).map(section -> section.scope().titleKey());
    }

    /**
     * The keys of the panel {@code scopeId}, in the order the features declared them, then those of the scopes it
     * hosts (ADR 161).
     */
    public List<Binding<?>> bindings(String scopeId) {
        return own(scopeId).appendAll(guestsOf(scopeId).flatMap(guest -> own(guest.id())));
    }

    /**
     * The keys of {@code scopeId} itself, without those of the scopes it hosts.
     */
    private List<Binding<?>> own(String scopeId) {
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
         * The message the key sends, as the help runs it.
         */
        Msg action();

        /**
         * A key that sends {@code msg}.
         *
         * @param matches whether an event is this key
         */
        record Dispatching(String label, String descriptionKey, Predicate<KeyEvent> matches, Supplier<Msg> msg)
                implements Global {

            @Override
            public Option<Msg> handle(KeyEvent event) {
                return matches.test(event) ? Option.of(action()) : Option.none();
            }

            @Override
            public Msg action() {
                return msg.get();
            }
        }
    }

    /**
     * A panel that has keys of its own.
     *
     * @param id       the id of the panel
     * @param titleKey the message key of the panel's title
     * @param <S>      what is selected in the panel, which its keys act on
     */
    @SuppressWarnings("UnusedTypeParameter")
    public record Scope<S>(String id, String titleKey) {

        /**
         * The id of the pane the panel is, as the model keeps it.
         */
        public PaneId pane() {
            return new PaneId(id);
        }
    }

    /**
     * A key in a panel.
     *
     * @param key            the key as {@link #name} gives it: the character, {@code " "} for space, prefixed
     *                       with {@code ctrl+} and {@code alt+} when those must be held, as in {@code ctrl+x}
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
         * Whether {@code event} is this key, with the same Ctrl and Alt held. A key let go is none, as with TamboUI's
         * own bindings.
         */
        public boolean matches(KeyEvent event) {
            return !event.isRelease() && key.equals(name(event));
        }

        /**
         * How the key is shown to the user.
         */
        public String label() {
            return " ".equals(key) ? Messages.get("key.space") : key;
        }
    }

    /**
     * What gives the {@link #standard()} keymap: the app, which knows every feature, as a service
     * ({@code META-INF/services}), so that the views ask for the keymap without naming the app (ADR 136).
     */
    public interface Source {

        /**
         * The keymap of the app.
         */
        Keymap keymap();
    }

    private static final class Found {
        // Looked up once, when the standard keymap is first asked for.
        private static final Keymap KEYMAP =
                ServiceLoader.load(Source.class).findFirst().map(Source::keymap).orElseGet(Keymap::empty);
    }

    /**
     * What a view gives the keys of a scope it hosts to act on (ADR 161).
     *
     * @param scope   the scope hosted
     * @param subject what its keys act on, if anything
     */
    public record Hosted<T>(Scope<T> scope, Option<T> subject) {

        private static <T> Hosted<T> nothing(Scope<T> scope) {
            return new Hosted<>(scope, Option.none());
        }

        private Option<Msg> handled(Keymap keymap, KeyEvent event) {
            return keymap.own(scope.id())
                    .find(binding -> binding.matches(event))
                    .flatMap(binding -> {
                        @SuppressWarnings("unchecked")
                        Binding<T> typed = (Binding<T>) binding;
                        return typed.action().apply(subject);
                    });
        }

        private List<Option<Msg>> actions(Keymap keymap) {
            return keymap.own(scope.id()).map(binding -> {
                @SuppressWarnings("unchecked")
                Binding<T> typed = (Binding<T>) binding;
                return typed.action().apply(subject);
            });
        }
    }

    private record Section(Scope<?> scope, List<Binding<?>> bindings) {}
}

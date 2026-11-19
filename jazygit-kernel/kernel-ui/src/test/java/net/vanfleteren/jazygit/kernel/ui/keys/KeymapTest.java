package net.vanfleteren.jazygit.kernel.ui.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import org.junit.jupiter.api.Test;

/**
 * The keymap as the views see it, without the app: {@code ?} asks for help only when the keymap was given a way to,
 * and the standard keymap is empty, as no {@link Keymap.Source} is on the classpath (ADR 136).
 */
class KeymapTest {

    private static final Scope<String> SCOPE = new Scope<>("probe", "panel.probe.title");

    private static final Probe ALWAYS = new Probe("always");

    private static final Keymap.Binding<String> ON_SELECTED =
            Binding.onSelected("x", "probe.x", selected -> new Probe("selected " + selected));

    @Test
    void theHelpKeyDoesNothingInAKeymapWithoutHelp() {
        Keymap keymap = Keymap.empty().add(SCOPE, Binding.always("a", "probe.a", () -> ALWAYS));

        assertEquals(Option.none(), keymap.handle(SCOPE, KeyEvent.ofChar('?'), Option.of("one")));
    }

    @Test
    void theHelpKeySendsWhatTheHelpMakesOfTheScopeAndItsActionsInOrder() {
        Keymap keymap = Keymap.withHelp((id, actions) -> new Help(id, actions))
                .add(SCOPE, Binding.always("a", "probe.a", () -> ALWAYS), ON_SELECTED);

        assertEquals(
                Option.of(new Help("probe", List.of(Option.of(ALWAYS), Option.of(new Probe("selected one"))))),
                keymap.handle(SCOPE, KeyEvent.ofChar('?'), Option.of("one")));
        // A key that does nothing without a selection is still listed, as doing nothing.
        assertEquals(
                Option.of(new Help("probe", List.of(Option.of(ALWAYS), Option.none()))),
                keymap.handle(SCOPE, KeyEvent.ofChar('?'), Option.none()));
    }

    @Test
    void theOtherKeysWorkTheSameWithOrWithoutHelp() {
        Keymap withHelp =
                Keymap.withHelp((id, actions) -> new Help(id, actions)).add(SCOPE, ON_SELECTED);
        Keymap withoutHelp = Keymap.empty().add(SCOPE, ON_SELECTED);

        for (Keymap keymap : List.of(withHelp, withoutHelp)) {
            assertEquals(
                    Option.of(new Probe("selected one")), keymap.handle(SCOPE, KeyEvent.ofChar('x'), Option.of("one")));
            assertEquals(Option.none(), keymap.handle(SCOPE, KeyEvent.ofChar('x'), Option.none()));
        }
    }

    @Test
    void theStandardKeymapIsEmptyWithoutTheApp() {
        Keymap standard = Keymap.standard();

        assertTrue(standard.globals().isEmpty());
        assertTrue(standard.bindings(Scopes.FILES.id()).isEmpty());
        assertEquals(Option.none(), standard.handle(Scopes.FILES, KeyEvent.ofChar('?'), Option.none()));
    }

    private record Probe(String name) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }

    private record Help(String scopeId, List<Option<Msg>> actions) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}

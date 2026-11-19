package net.vanfleteren.jazygit.kernel.ui.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.vavr.control.Option;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void substitutesParameters() {
        assertEquals("2h 5m", Messages.forLocale(Locale.ENGLISH).format("checks.hours", "2", "5"));
    }

    @Test
    void missingKeyIsMarkedInsteadOfThrowing() {
        assertEquals("!nope!", Messages.forLocale(Locale.ENGLISH).format("nope"));
    }

    @Test
    void usesTranslationOfTheLocaleAndFallsBackToEnglish() {
        Messages xx = Messages.forLocale(Locale.of("xx"));
        assertEquals("Stash-xx", xx.format("panel.stash.title"));
        assertEquals("Branches", xx.format("panel.branches.title"));
    }

    /**
     * A key that starts with the root of a module is looked up in that module's bundle (ADR 136): here the bundle of
     * the tests, {@code probe}.
     */
    @Test
    void aKeyIsLookedUpInTheBundleOfTheModuleItNames() {
        assertEquals("Hello, Ada", Messages.forLocale(Locale.ENGLISH).format("probe.greeting", "Ada"));
        assertEquals("!probe.missing!", Messages.forLocale(Locale.ENGLISH).format("probe.missing"));
    }

    /**
     * A module's bundle is in its group's package (ADR 159), and its keys start with its name without the group.
     */
    @Test
    void aKeyIsLookedUpInTheBundleOfItsModuleInTheKernel() {
        assertEquals(
                "Hello from the kernel, Ada", Messages.forLocale(Locale.ENGLISH).format("probekernel.greeting", "Ada"));
    }

    @Test
    void aKeyIsLookedUpInTheBundleOfItsModuleInTheFeatures() {
        assertEquals(
                "Hello from the features, Ada",
                Messages.forLocale(Locale.ENGLISH).format("probefeature.greeting", "Ada"));
    }

    /**
     * When two groups have a bundle for one name, neither hides the other's keys; for a key both have, the kernel's
     * wins, as the kernel comes first.
     */
    @Test
    void aNameWithABundleInTwoGroupsKeepsTheKeysOfBoth() {
        Messages messages = Messages.forLocale(Locale.ENGLISH);
        assertEquals("Only the kernel has this", messages.format("probeboth.kernel"));
        assertEquals("Only the features have this", messages.format("probeboth.feature"));
        assertEquals("From the kernel", messages.format("probeboth.shared"));
    }

    /**
     * A key of the views that starts as the name of a module does is still the views' own, when that module has no
     * such key.
     */
    @Test
    void aKeyThatNoModuleHasIsTheViews() {
        assertEquals("gone", Messages.forLocale(Locale.ENGLISH).format("branches.gone"));
        assertEquals("!unknown.key!", Messages.forLocale(Locale.ENGLISH).format("unknown.key"));
        assertEquals(Option.none(), Messages.find("unknown.key"));
    }

    @Test
    void everyKeyHasAText() {
        ResourceBundle base = ResourceBundle.getBundle("net.vanfleteren.jazygit.kernel.ui.messages", Locale.ENGLISH);
        base.keySet().forEach(key -> assertFalse(base.getString(key).isBlank(), key));
    }
}

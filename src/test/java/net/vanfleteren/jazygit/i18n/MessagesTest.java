package net.vanfleteren.jazygit.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

class MessagesTest {

    @Test
    void substitutesParameters() {
        assertEquals("Checkout of main failed: boom",
                Messages.forLocale(Locale.ENGLISH).format("error.checkout.failed", "main", "boom"));
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

    @Test
    void everyKeyHasAText() {
        ResourceBundle base = ResourceBundle.getBundle("net.vanfleteren.jazygit.messages", Locale.ENGLISH);
        base.keySet().forEach(key -> assertFalse(base.getString(key).isBlank(), key));
    }
}

package net.vanfleteren.jazygit.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The user-visible text, looked up by key in the {@code messages} bundles. The bundle is chosen by
 * the locale; a key without a translation falls back to the English base bundle.
 */
public final class Messages {

    private static final String BUNDLE = "net.vanfleteren.jazygit.messages";

    private final ResourceBundle bundle;
    private final Locale locale;

    private Messages(Locale locale) {
        this.locale = locale;
        this.bundle = ResourceBundle.getBundle(BUNDLE, locale);
    }

    /**
     * The messages for the locale of the user, as the JVM derived it from the environment.
     */
    public static Messages forDefaultLocale() {
        return new Messages(Locale.getDefault());
    }

    public static Messages forLocale(Locale locale) {
        return new Messages(locale);
    }

    private static volatile Messages current;

    /**
     * Looks up {@code key} in the messages for the default locale.
     *
     * @param args the values for the {@code {0}}, {@code {1}}... placeholders; numbers should be
     *             passed as strings to avoid locale-specific grouping
     */
    public static String get(String key, Object... args) {
        Messages messages = current;
        if (messages == null || !messages.locale.equals(Locale.getDefault())) {
            messages = forDefaultLocale();
            current = messages;
        }
        return messages.format(key, args);
    }

    public String format(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            return new MessageFormat(pattern, locale).format(args);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }
}

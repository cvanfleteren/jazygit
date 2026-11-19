package net.vanfleteren.jazygit.kernel.ui.i18n;

import io.vavr.Function1;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The user-visible text, looked up by key in the {@code messages} bundles. Each module of the application has its own
 * bundle, {@code net/vanfleteren/jazygit/<group>/<root>/messages.properties}, and its keys start with its root, as
 * {@code github.dialog.rerunChecks.title} (ADR 136, 159): a key is looked up in the bundle its first part names, in
 * the first group that has one, and otherwise in the bundle of the views, which holds the keys of no module. So the
 * views find the text of a feature without naming it. The bundle is chosen by the locale; a key without a translation
 * falls back to the English base bundle.
 */
public final class Messages {

    private static final String PACKAGE = "net.vanfleteren.jazygit.";
    // The groups whose modules have text, then the app, whose package is in no group (ADR 159).
    private static final List<String> GROUPS = List.of("kernel.", "features.", "");
    private static final String BUNDLE = "messages";
    private static final String OWN = "kernel.ui";
    // The locale of the user does not change while the app runs.
    private static final Messages DEFAULT = forDefaultLocale();
    private final Locale locale;
    private final ResourceBundle own;
    // The bundle of each module a key named, looked up once: none for a part that names no module with a bundle.
    private final Function1<String, Option<ResourceBundle>> bundles;

    private Messages(Locale locale) {
        this.locale = locale;
        this.own = ResourceBundle.getBundle(PACKAGE + OWN + "." + BUNDLE, locale);
        this.bundles = Function1.<String, Option<ResourceBundle>>of(root -> GROUPS.iterator()
                        .flatMap(group -> Try.of(
                                        () -> ResourceBundle.getBundle(PACKAGE + group + root + "." + BUNDLE, locale))
                                .toOption())
                        .headOption())
                .memoized();
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

    /**
     * Looks up {@code key} in the messages for the locale of the user.
     *
     * @param args the values for the {@code {0}}, {@code {1}}... placeholders; numbers should be
     *             passed as strings to avoid locale-specific grouping
     */
    public static String get(String key, Object... args) {
        return DEFAULT.format(key, args);
    }

    /**
     * The message {@code key} in the messages for the locale of the user, if there is one; for texts that are
     * optional.
     */
    public static Option<String> find(String key) {
        return DEFAULT.pattern(key).map(pattern -> DEFAULT.format(key));
    }

    /**
     * The message {@code key} with {@code args} filled in; {@code !key!} when there is no such message, or
     * when its pattern is malformed.
     */
    public String format(String key, Object... args) {
        return pattern(key)
                .flatMap(pattern -> Try.of(() -> new MessageFormat(pattern, locale).format(args))
                        .toOption())
                .getOrElse(() -> "!" + key + "!");
    }

    /**
     * The pattern of {@code key}: in the bundle of the module its first part names, or else in the bundle of the views.
     */
    private Option<String> pattern(String key) {
        int dot = key.indexOf('.');
        Option<ResourceBundle> module =
                dot > 0 && !key.substring(0, dot).equals(OWN) ? bundles.apply(key.substring(0, dot)) : Option.none();
        return module.filter(bundle -> bundle.containsKey(key))
                .orElse(() -> Option.when(own.containsKey(key), own))
                .map(bundle -> bundle.getString(key));
    }
}

package net.vanfleteren.jazygit.core.gh.account;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.core.gh.model.ChosenBrowser.Source;
import net.vanfleteren.jazygit.core.process.CommandLine;

/**
 * The browser gh opens links in, which jazygit starts itself for a link gh would cut short (ADR 85).
 */
final class Browser {

    // How long a browser is waited for: one still running by then is taken to have opened the link.
    static final Duration SETTLE = Duration.ofSeconds(2);

    // What opens a link in the browser the desktop is set to, on Linux.
    static final String XDG_OPEN = "xdg-open";

    // What opens a link in the browser the desktop is set to, on macOS and on Windows; not cmd's start, which takes an
    // & in the link for the end of the command.
    static final String MAC_OPEN = "open";
    static final String WINDOWS_OPEN = "rundll32 url.dll,FileProtocolHandler";

    private Browser() {}

    /**
     * What opens a link in the browser the desktop of {@code os} is set to, as {@code os.name} names it: on Linux
     * {@code xdg-open}, on macOS {@code open}, on Windows the URL handler of the shell; none on another system. gh
     * does so itself for what it opens; jazygit, for a link that gh cannot open (ADR 96).
     */
    static Option<String> desktop(String os) {
        String name = os.toLowerCase(Locale.ROOT);
        return name.contains("linux")
                ? Option.of(XDG_OPEN)
                : name.contains("mac")
                        ? Option.of(MAC_OPEN)
                        : name.contains("windows") ? Option.of(WINDOWS_OPEN) : Option.none();
    }

    /**
     * The browser gh would start, in gh's order: {@code $GH_BROWSER}, gh's {@code browser} setting,
     * {@code $BROWSER}, and on Linux {@code xdg-open}; none on another system without one set, where gh is left to
     * open what it can. Blank is not set.
     *
     * @param environment the variables of the environment, by name
     * @param configured  gh's {@code browser} setting, read only when {@code $GH_BROWSER} is not set
     * @param os          the name of the system, as {@code os.name} says it
     */
    static Option<ChosenBrowser> chosen(
            Function<String, Option<String>> environment, Supplier<Option<String>> configured, String os) {
        return set(environment.apply("GH_BROWSER"))
                .map(browser -> new ChosenBrowser(browser, Source.GH_BROWSER))
                .orElse(() -> set(configured.get()).map(browser -> new ChosenBrowser(browser, Source.GH_SETTING)))
                .orElse(() ->
                        set(environment.apply("BROWSER")).map(browser -> new ChosenBrowser(browser, Source.BROWSER)))
                .orElse(() -> Option.when(
                        os.toLowerCase(Locale.ROOT).contains("linux"), new ChosenBrowser(XDG_OPEN, Source.DESKTOP)));
    }

    /**
     * How {@code browser}, as it is set, is run on {@code url}: its words, as a shell splits them, and the link.
     */
    static List<String> commandLine(String browser, String url) {
        return words(browser).append(url);
    }

    /**
     * {@code line} split into words as a shell does, as gh splits its browser: see {@link CommandLine#words}.
     */
    static List<String> words(String line) {
        return CommandLine.words(line);
    }

    static Option<String> set(Option<String> value) {
        return value.map(String::strip).filter(set -> !set.isEmpty());
    }
}

package net.vanfleteren.jazygit.features.forgejo.browse;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import net.vanfleteren.jazygit.core.process.CommandLine;
import net.vanfleteren.jazygit.core.process.Exec;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * What opens a link of Forgejo's in the browser (ADR 176).
 */
@FunctionalInterface
public interface Browser {

    /**
     * No browser: nothing opens.
     */
    Browser NONE = url -> Either.left(new ForgejoFailure.NoBrowser());

    /**
     * Opens {@code url}: the command that ran, or why it did not.
     */
    Either<Failure, ExecutedCommand> open(String url);

    /**
     * The browser {@code $BROWSER} names, run in {@code directory}, or else what opens a link in the browser the
     * desktop of {@code os} is set to: {@code xdg-open} on Linux, {@code open} on macOS, the URL handler of the shell
     * on Windows; none on another system without {@code $BROWSER}.
     *
     * @param set the value of {@code $BROWSER}, if it is set
     * @param os  the name of the system, as {@code os.name} says it
     */
    static Browser of(Path directory, Option<String> set, String os) {
        return chosen(set, os)
                .<Browser>map(browser -> url -> launch(directory, browser, url))
                .getOrElse(NONE);
    }

    /**
     * The browser run: {@code $BROWSER} when it is set and not blank, else the desktop's.
     */
    static Option<String> chosen(Option<String> set, String os) {
        String name = os.toLowerCase(Locale.ROOT);
        return set.map(String::strip)
                .filter(browser -> !browser.isEmpty())
                .orElse(() -> name.contains("linux")
                        ? Option.of("xdg-open")
                        : name.contains("mac")
                                ? Option.of("open")
                                : name.contains("windows")
                                        ? Option.of("rundll32 url.dll,FileProtocolHandler")
                                        : Option.none());
    }

    private static Either<Failure, ExecutedCommand> launch(Path directory, String browser, String url) {
        List<String> commandLine = CommandLine.words(browser).append(url);
        // One still running after two seconds is taken to have opened the link, as gh's are (ADR 85).
        return Exec.launch(directory, commandLine, Duration.ofSeconds(2))
                .mapLeft(failure -> new ForgejoFailure.BrowserFailed(browser));
    }
}

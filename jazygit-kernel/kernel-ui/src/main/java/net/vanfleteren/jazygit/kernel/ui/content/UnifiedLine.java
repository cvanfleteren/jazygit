package net.vanfleteren.jazygit.kernel.ui.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * A line of a unified diff as text, which the content panel and the content of the features draw alike.
 */
public final class UnifiedLine {

    /**
     * How the header lines of a diff start, before its first hunk.
     */
    private static final List<String> HEADER_PREFIXES = List.of("+++", "---", "diff ", "index ");

    private UnifiedLine() {}

    /**
     * A line of a unified diff, colored by what it is: added, removed, a hunk header or a file header.
     */
    public static Line of(String line, Theme.SpanStyles styles) {
        return cssClass(line)
                .map(cssClass -> Line.from(Span.styled(line, styles.of(cssClass))))
                .getOrElse(() -> Line.from(Span.raw(line)));
    }

    private static Option<String> cssClass(String line) {
        return switch (line) {
            case String l when HEADER_PREFIXES.exists(l::startsWith) -> Option.of(Theme.Classes.DIFF_HEADER);
            case String l when l.startsWith("+") -> Option.of(Theme.Classes.DIFF_ADDED);
            case String l when l.startsWith("-") -> Option.of(Theme.Classes.DIFF_REMOVED);
            case String l when l.startsWith("@@") -> Option.of(Theme.Classes.DIFF_HUNK);
            default -> Option.none();
        };
    }
}

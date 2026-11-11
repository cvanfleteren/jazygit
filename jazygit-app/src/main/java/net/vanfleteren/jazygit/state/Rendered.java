package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Locale;

/**
 * A kind of text file a version of can be shown rendered (ADR 103), told by the extension of its path: the one table
 * of which files have a renderer, and which.
 */
public enum Rendered {
    /**
     * Markdown. Not {@code .mdx}, which holds JSX.
     */
    MARKDOWN(HashSet.of("md", "markdown", "mdown", "mkd"));

    private final Set<String> extensions;

    Rendered(Set<String> extensions) {
        this.extensions = extensions;
    }

    /**
     * How the file at {@code path} is rendered, if it can be.
     */
    public static Option<Rendered> of(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return Option.when(dot > 0, () -> name.substring(dot + 1).toLowerCase(Locale.ROOT))
                .flatMap(extension -> List.of(values()).find(kind -> kind.extensions.contains(extension)));
    }
}

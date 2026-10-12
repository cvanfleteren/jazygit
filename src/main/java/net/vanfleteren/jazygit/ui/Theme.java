package net.vanfleteren.jazygit.ui;

import dev.tamboui.css.Styleable;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.style.Style;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * The CSS styling of the UI: the {@link StyleEngine} holding the stylesheets, and the class names
 * the elements use to be matched by them.
 */
public final class Theme {

    private static final String RESOURCE_DIR = "/net/vanfleteren/jazygit/themes/";
    public static final String DARK = "dark";
    public static final String LIGHT = "light";
    public static final String DARCULA = "darcula";
    /** The themes, in the order they are offered. */
    public static final List<String> NAMES = List.of(DARK, LIGHT, DARCULA);

    /** CSS class names referenced from the stylesheets. */
    public static final class Classes {
        public static final String FOCUSED = "focused";
        public static final String BOLD = "bold";
        public static final String DIM = "dim";
        public static final String ERROR = "error";
        public static final String DISABLED = "disabled";
        public static final String STAGED = "staged";
        public static final String UNSTAGED = "unstaged";
        public static final String SPINNER = "spinner";
        public static final String AHEAD = "ahead";
        public static final String BEHIND = "behind";
        public static final String DIFF_HEADER = "diff-header";
        public static final String DIFF_ADDED = "diff-added";
        public static final String DIFF_REMOVED = "diff-removed";
        public static final String DIFF_HUNK = "diff-hunk";
        public static final String CURRENT_BRANCH = "current-branch";
        public static final String SHA = "sha";

        private Classes() {
        }
    }

    // Spans are not elements, so their styles are resolved by hand; null until the app starts.
    private static volatile StyleEngine active;

    private Theme() {
    }

    public static StyleEngine createEngine() {
        StyleEngine engine = StyleEngine.create();
        try {
            for (String name : NAMES) {
                engine.loadStylesheet(name, RESOURCE_DIR + name + ".tcss");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load themes", e);
        }
        engine.setActiveStylesheet(DARK);
        active = engine;
        return engine;
    }

    /** Whether {@code event} is the shortcut that asks which theme to use. */
    public static boolean isThemeKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('t');
    }

    /**
     * Makes {@code name} the active theme, if it is not yet; the next render picks it up. An unknown name
     * leaves the theme as it is.
     */
    public static void activate(String name) {
        StyleEngine engine = active;
        if (engine == null || !NAMES.contains(name) || engine.getActiveStylesheet().filter(name::equals).isPresent()) {
            return;
        }
        engine.setActiveStylesheet(name);
    }

    /**
     * The style of a span with the given CSS class, for text that is not an element of its own.
     * Empty when no theme is active.
     */
    public static Style spanStyle(String cssClass) {
        StyleEngine engine = active;
        return engine == null
                ? Style.EMPTY
                : engine.resolve(new SpanNode(Set.of(cssClass))).toStyle();
    }

    private record SpanNode(Set<String> cssClasses) implements Styleable {
        @Override
        public String styleType() {
            return "Span";
        }

        @Override
        public java.util.Optional<String> cssId() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Styleable> cssParent() {
            return java.util.Optional.empty();
        }
    }
}

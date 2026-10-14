package net.vanfleteren.jazygit.ui;

import dev.tamboui.css.Styleable;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.style.Style;
import io.vavr.collection.List;

import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

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
    /** The dark theme with blue and vermillion for red and green, for red/green colour blindness. */
    public static final String DARK_COLORBLIND = "dark-colorblind";
    /** The themes, in the order they are offered. */
    public static final List<String> NAMES = List.of(DARK, LIGHT, DARCULA, DARK_COLORBLIND);

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
        public static final String GONE = "gone";
        public static final String PULL_REQUEST = "pull-request";
        public static final String CHECKS_PASSED = "checks-passed";
        public static final String CHECKS_FAILED = "checks-failed";
        public static final String CHECKS_RUNNING = "checks-running";
        public static final String REVIEW_APPROVED = "review-approved";
        public static final String REVIEW_CHANGES = "review-changes";
        public static final String DIFF_HEADER = "diff-header";
        public static final String DIFF_ADDED = "diff-added";
        public static final String DIFF_REMOVED = "diff-removed";
        public static final String DIFF_HUNK = "diff-hunk";
        public static final String CURRENT_BRANCH = "current-branch";
        public static final String SHA = "sha";
        public static final String TAG = "tag";
        /** The highlighted row of the left pane that is in use. */
        public static final String SELECTION = "selection";
        /** The highlighted row of the other left panes, which the built-in themes hide. */
        public static final String INACTIVE_SELECTION = "inactive-selection";

        private Classes() {
        }
    }

    private Theme() {
    }

    /**
     * A stylesheet of a theme could not be read.
     */
    public record LoadFailed(String theme, Throwable cause) {
    }

    /**
     * The style engine with all themes loaded, the dark one active.
     */
    public static Either<LoadFailed, StyleEngine> createEngine() {
        StyleEngine engine = StyleEngine.create();
        return NAMES.foldLeft(Either.<LoadFailed, StyleEngine>right(engine), (loaded, name) -> loaded.flatMap(e ->
                        Try.run(() -> e.loadStylesheet(name, RESOURCE_DIR + name + ".tcss"))
                                .toEither()
                                .<LoadFailed>mapLeft(cause -> new LoadFailed(name, cause))
                                .map(done -> e)))
                .peek(e -> e.setActiveStylesheet(DARK));
    }

    /**
     * Makes {@code name} the active theme, if it is not yet; the next render picks it up. An unknown name
     * leaves the theme as it is.
     */
    public static void activate(StyleEngine engine, String name) {
        if (NAMES.contains(name) && !Option.ofOptional(engine.getActiveStylesheet()).contains(name)) {
            engine.setActiveStylesheet(name);
        }
    }

    /**
     * The styles of spans by their CSS class, as {@code engine} resolves them, for text that is not an
     * element of its own.
     */
    public static SpanStyles spanStyles(StyleEngine engine) {
        return cssClass -> engine.resolve(new Node("Span", Set.of(cssClass))).toStyle();
    }

    /**
     * How {@code engine} highlights the selected row: as the {@link Classes#SELECTION} rule says in the pane in
     * use, reversed when the theme has none, and as the {@link Classes#INACTIVE_SELECTION} rule says in the
     * others, not at all when the theme has none.
     */
    public static Highlights highlights(StyleEngine engine) {
        return active -> active
                ? Option.of(ownStyle(engine, Classes.SELECTION)).filter(style -> !style.equals(Style.EMPTY))
                        .getOrElse(Highlights.DEFAULT.of(true))
                : ownStyle(engine, Classes.INACTIVE_SELECTION);
    }

    /**
     * What the rules for {@code cssClass} declare themselves, without what every element gets, like the colours
     * of {@code *}: patched over a row, those would hide the colours of its pieces.
     */
    private static Style ownStyle(StyleEngine engine, String cssClass) {
        Style base = engine.resolve(new Node("Highlight", Set.of())).toStyle();
        Style styled = engine.resolve(new Node("Highlight", Set.of(cssClass))).toStyle();
        Style withFg = Option.ofOptional(styled.fg())
                .filter(fg -> !base.fg().equals(java.util.Optional.of(fg)))
                .map(Style.EMPTY::fg)
                .getOrElse(Style.EMPTY);
        Style withBg = Option.ofOptional(styled.bg())
                .filter(bg -> !base.bg().equals(java.util.Optional.of(bg)))
                .map(withFg::bg)
                .getOrElse(withFg);
        return List.ofAll(styled.addModifiers()).removeAll(base.addModifiers())
                .foldLeft(withBg, Style::addModifier);
    }

    /**
     * How the selected row of a left pane is highlighted, in the pane in use and in the others.
     */
    @FunctionalInterface
    public interface Highlights {

        /**
         * The highlight before a theme is known: reversed in the pane in use, none in the others.
         */
        Highlights DEFAULT = active -> active ? Style.EMPTY.reversed() : Style.EMPTY;

        Style of(boolean active);
    }

    /**
     * How text is styled that is not an element of its own: the style of a span with a CSS class.
     */
    @FunctionalInterface
    public interface SpanStyles {

        /**
         * No styling, e.g. before a theme is known.
         */
        SpanStyles NONE = cssClass -> Style.EMPTY;

        Style of(String cssClass);
    }

    private record Node(String styleType, Set<String> cssClasses) implements Styleable {

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

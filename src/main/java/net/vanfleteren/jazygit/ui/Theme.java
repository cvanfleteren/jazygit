package net.vanfleteren.jazygit.ui;

import dev.tamboui.css.Styleable;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.Set;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * The CSS styling of the UI: the {@link StyleEngine} holding the stylesheets, and the class names
 * the elements use to be matched by them.
 */
public final class Theme {

    private static final String RESOURCE_DIR = "/net/vanfleteren/jazygit/themes/";

    private Theme() {}

    /**
     * The style engine with all themes loaded, the dark one active.
     */
    public static Either<LoadFailed, StyleEngine> createEngine() {
        StyleEngine engine = StyleEngine.create();
        return List.of(ThemeName.values())
                .map(ThemeName::id)
                .foldLeft(
                        Either.<LoadFailed, StyleEngine>right(engine),
                        (loaded, name) ->
                                loaded.flatMap(e -> Try.run(() -> e.loadStylesheet(name, RESOURCE_DIR + name + ".tcss"))
                                        .toEither()
                                        .mapLeft(cause -> new LoadFailed(name, cause))
                                        .map(done -> e)))
                .peek(e -> e.setActiveStylesheet(ThemeName.DARK.id()));
    }

    /**
     * Makes {@code theme} the active theme, if it is not yet; the next render picks it up.
     */
    public static void activate(StyleEngine engine, ThemeName theme) {
        if (!Option.ofOptional(engine.getActiveStylesheet()).contains(theme.id())) {
            engine.setActiveStylesheet(theme.id());
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
                ? Option.of(ownStyle(engine, Classes.SELECTION))
                        .filter(style -> !style.equals(Style.EMPTY))
                        .getOrElse(Highlights.DEFAULT.of(true))
                : ownStyle(engine, Classes.INACTIVE_SELECTION);
    }

    /**
     * How {@code engine} dims what is behind an open popup: plain text, in the colour of {@code *} or the
     * terminal's own, takes the colour of the {@link Classes#BACKDROP} rule.
     */
    public static Backdrop backdrop(StyleEngine engine) {
        return new Backdrop(Option.ofOptional(baseStyle(engine).fg()), ownStyle(engine, Classes.BACKDROP));
    }

    /**
     * How {@code engine} colours plain text on a highlighted row: as the {@link Classes#SELECTED_TEXT} rule says,
     * on rows with the background of the {@link Classes#SELECTION} rule.
     */
    public static SelectedText selectedText(StyleEngine engine) {
        return new SelectedText(
                Option.ofOptional(ownStyle(engine, Classes.SELECTION).bg()),
                Option.ofOptional(baseStyle(engine).fg()),
                Option.ofOptional(ownStyle(engine, Classes.SELECTED_TEXT).fg()));
    }

    private static Style baseStyle(StyleEngine engine) {
        return engine.resolve(new Node("Highlight", Set.of())).toStyle();
    }

    /**
     * What the rules for {@code cssClass} declare themselves, without what every element gets, like the colours
     * of {@code *}: patched over a row, those would hide the colours of its pieces.
     */
    private static Style ownStyle(StyleEngine engine, String cssClass) {
        Style base = baseStyle(engine);
        Style styled = engine.resolve(new Node("Highlight", Set.of(cssClass))).toStyle();
        Style withFg = Option.ofOptional(styled.fg())
                .filter(fg -> !base.fg().equals(java.util.Optional.of(fg)))
                .map(Style.EMPTY::fg)
                .getOrElse(Style.EMPTY);
        Style withBg = Option.ofOptional(styled.bg())
                .filter(bg -> !base.bg().equals(java.util.Optional.of(bg)))
                .map(withFg::bg)
                .getOrElse(withFg);
        return List.ofAll(styled.addModifiers()).removeAll(base.addModifiers()).foldLeft(withBg, Style::addModifier);
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

    /**
     * CSS class names referenced from the stylesheets.
     */
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
        public static final String COPIED = "copied";
        public static final String IN_PROGRESS = "in-progress";
        public static final String REPO_BRANCH = "repo-branch";
        public static final String REPO_LOCATION = "repo-location";
        /**
         * The highlighted row of the left pane that is in use.
         */
        public static final String SELECTION = "selection";
        /**
         * The highlighted row of the other left panes, which the built-in themes hide.
         */
        public static final String INACTIVE_SELECTION = "inactive-selection";
        /**
         * The colour of plain text on a highlighted row, when a theme has one.
         */
        public static final String SELECTED_TEXT = "selected-text";
        /**
         * The colour of plain text behind an open popup.
         */
        public static final String BACKDROP = "backdrop";

        private Classes() {}
    }

    /**
     * A stylesheet of a theme could not be read.
     */
    public record LoadFailed(String theme, Throwable cause) {}

    /**
     * How what is behind an open popup is dimmed: every cell gets the terminal's dim attribute, and plain text
     * gets {@code plain} on top, as terminals do not always dim their default colour.
     *
     * @param plainFg the colour of plain text, none when it is the terminal's default
     */
    public record Backdrop(Option<Color> plainFg, Style plain) {

        /**
         * The terminal's dim attribute alone, e.g. before a theme is known.
         */
        public static final Backdrop DIM_ONLY = new Backdrop(Option.none(), Style.EMPTY);

        private static final Style DIM = Style.EMPTY.dim();

        /**
         * {@code style} dimmed.
         */
        public Style dim(Style style) {
            boolean isPlain = Option.ofOptional(style.fg()).equals(plainFg);
            return (isPlain ? style.patch(plain) : style).patch(DIM);
        }
    }

    /**
     * How plain text on a highlighted row is coloured. The highlight itself is laid over every cell of the row, so
     * it only sets the background, and a colour of its own would hide the colours of the pieces of the row; plain
     * text takes {@code fg} on top, the pieces keep theirs.
     *
     * @param rowBg   the background of a highlighted row, none when the theme highlights rows otherwise
     * @param plainFg the colour of plain text, none when it is the terminal's default
     * @param fg      the colour plain text takes on a highlighted row, none to keep it
     */
    public record SelectedText(Option<Color> rowBg, Option<Color> plainFg, Option<Color> fg) {

        /**
         * Plain text keeps its colour, e.g. before a theme is known.
         */
        public static final SelectedText UNCHANGED = new SelectedText(Option.none(), Option.none(), Option.none());

        /**
         * {@code style} as it is on a highlighted row, when it is a cell of one.
         */
        public Style of(Style style) {
            boolean highlighted =
                    rowBg.isDefined() && Option.ofOptional(style.bg()).equals(rowBg);
            boolean isPlain = Option.ofOptional(style.fg()).equals(plainFg);
            return highlighted && isPlain ? fg.map(style::fg).getOrElse(style) : style;
        }
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

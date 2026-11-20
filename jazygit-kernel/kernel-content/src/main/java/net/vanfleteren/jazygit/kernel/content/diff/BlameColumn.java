package net.vanfleteren.jazygit.kernel.content.diff;

import dev.tamboui.style.Style;
import dev.tamboui.text.Span;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.vanfleteren.jazygit.core.git.model.Blame;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.diff.Blamed;
import net.vanfleteren.jazygit.kernel.ui.commits.CommitRows;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * The column before the numbers of the lines of a version shown blamed (ADR 147): for each line a bar of the age of the
 * commit it is blamed on, and, on the first line of a run from one commit, its short sha, the initials of its author
 * and its author date, {@code ▌ 9c4d2e8 AM 2026-09-14}. In an area too narrow for that beside {@link #CODE} columns of
 * code, the bar and the sha only. While the blame is read, a blank column as wide.
 *
 * @param blamed the blame drawn
 * @param narrow whether the column is the bar and the sha only
 * @param shades the shade of each commit blamed on, once it is read
 * @param styles the styles of the theme
 */
record BlameColumn(Blamed blamed, boolean narrow, Map<Sha, Integer> shades, Theme.SpanStyles styles) {

    /**
     * How many columns the code of a blamed version keeps beside the whole column, else it is shortened to fit.
     */
    static final int CODE = 60;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int SHA_WIDTH = 7;
    private static final int INITIALS_WIDTH = 2;
    // The sha, a space, the initials, a space and the date.
    private static final int LABEL_WIDTH = SHA_WIDTH + 1 + INITIALS_WIDTH + 1 + 10;

    /**
     * The column of {@code blamed}, if a version is shown blamed, in an area {@code area} columns wide whose gutter is
     * {@code gutter} columns wide beside it.
     */
    static Option<BlameColumn> of(Option<Blamed> blamed, int area, int gutter, Theme.SpanStyles styles) {
        return blamed.map(drawn -> new BlameColumn(
                drawn,
                area - gutter - width(false) < CODE,
                drawn.blame().toOption().map(Blame::shades).getOrElse(HashMap.empty()),
                styles));
    }

    /**
     * How many columns the column takes: the bar, a space, the label and a space.
     */
    int width() {
        return width(narrow);
    }

    private static int width(boolean narrow) {
        return 2 + label(narrow) + 1;
    }

    private static int label(boolean narrow) {
        return narrow ? SHA_WIDTH : LABEL_WIDTH;
    }

    /**
     * What the title says of the column: that it is shortened to fit, when it is.
     */
    Option<String> note() {
        return Option.when(narrow, () -> Messages.get("content.blame.narrow"));
    }

    /**
     * The column beside the line numbered {@code number} in the version, if it has a number: blank while the blame
     * is read, or for a line it does not have.
     */
    List<Span> beside(Option<Integer> number) {
        Option<Blame> read = blamed.blame().toOption();
        return number.flatMap(at -> read.flatMap(blame -> blame.at(at).map(line -> beside(blame, at, line))))
                .getOrElse(() -> List.of(Span.raw(" ".repeat(width()))));
    }

    private List<Span> beside(Blame blame, int number, Blame.Line line) {
        Blame.Commit commit = line.commit();
        int label = label(narrow);
        List<Span> labelled = !blame.startsRun(number)
                ? List.of(Span.raw(" ".repeat(label)))
                : !commit.committed()
                        ? dim(Messages.get(narrow ? "content.blame.uncommittedShort" : "content.blame.uncommitted"))
                        : blamed.own().contains(commit.sha())
                                ? dim(Messages.get(narrow ? "content.blame.ownShort" : "content.blame.own"))
                                : committed(commit);
        return List.of(bar(commit), Span.raw(" ")).appendAll(labelled).append(Span.raw(" "));
    }

    /**
     * The bar of {@code commit}, in its shade: none for lines not committed yet, or in a theme without the shade.
     */
    private Span bar(Blame.Commit commit) {
        Option<Style> shade = shades.get(commit.sha())
                .map(at -> styles.of(Theme.Classes.BLAME_AGE + at))
                .filter(style -> style.fg().isPresent());
        return shade.map(style -> Span.styled("▌", style)).getOrElse(() -> Span.raw(" "));
    }

    private List<Span> committed(Blame.Commit commit) {
        String shortSha = commit.sha().shortSha();
        Span sha = Span.styled(
                shortSha + " ".repeat(Math.max(0, SHA_WIDTH - shortSha.length())), styles.of(Theme.Classes.SHA));
        if (narrow) {
            return List.of(sha);
        }
        String initials = CommitRows.initials(commit.author());
        String rest = " "
                + initials
                + " ".repeat(Math.max(0, INITIALS_WIDTH - RichDiff.width(initials)))
                + " "
                + DATE.format(commit.authored().atZone(ZoneId.systemDefault()));
        return List.of(sha, Span.styled(rest, styles.of(Theme.Classes.DIFF_LINE_NUMBER)));
    }

    private List<Span> dim(String text) {
        int label = label(narrow);
        String fitted = text.length() > label ? text.substring(0, label) : text;
        return List.of(
                Span.styled(fitted, styles.of(Theme.Classes.DIM)), Span.raw(" ".repeat(label - fitted.length())));
    }

    /**
     * How many characters {@code spans} hold, which a search counts its line from (ADR 126).
     */
    static int length(List<Span> spans) {
        return spans.map(span -> span.content().length()).sum().intValue();
    }
}

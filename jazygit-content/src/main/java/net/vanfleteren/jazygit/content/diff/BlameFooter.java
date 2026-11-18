package net.vanfleteren.jazygit.content.diff;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.TimeText;

/**
 * The row under an area whose version is shown blamed (ADR 147): the commit of the line of the cursor, its sha, the
 * name of its author, how long ago it was written and its summary, as the column has no room for them.
 */
public final class BlameFooter {

    private static final long DAY = Duration.ofDays(1).toSeconds();
    private static final int MONTHS_FROM_DAYS = 60;
    private static final int YEARS_FROM_MONTHS = 24;

    private BlameFooter() {}

    /**
     * What the row under the area showing {@code version} says, with the cursor on {@code cursor}, at {@code now}: none
     * when the version shown is not blamed, and there is no such row. While the blame is read it says so; on the bar of
     * the file, what is no line, it is blank, as when the blame failed, which the title says.
     */
    public static Option<String> text(Version version, Option<Anchor> cursor, Instant now) {
        return version.blame().map(blame -> switch (blame) {
            case Loadable.Loading<Blame> reading -> Messages.get("content.blame.reading");
            case Loadable.Failed<Blame> failed -> "";
            case Loadable.Loaded<Blame>(Blame read) ->
                cursor.flatMap(version::blamedLine)
                        .map(line -> said(line.commit(), now))
                        .getOrElse("");
        });
    }

    private static String said(Blame.Commit commit, Instant now) {
        return commit.committed()
                ? Messages.get(
                        "content.blame.commit",
                        commit.sha().value().substring(0, 7),
                        commit.author(),
                        ago(Duration.between(commit.authored(), now)),
                        commit.summary())
                : Messages.get("content.blame.notCommitted");
    }

    /**
     * How long ago a commit written {@code since} was: as the panes say it under two months, then in months, and from
     * two years in years.
     */
    static String ago(Duration since) {
        long days = Math.max(0, since.toSeconds()) / DAY;
        long months = days * 12 / 365;
        return days < MONTHS_FROM_DAYS
                ? TimeText.ago(since)
                : months < YEARS_FROM_MONTHS
                        ? Messages.get("content.blame.monthsAgo", months)
                        : Messages.get("content.blame.yearsAgo", months / 12);
    }
}

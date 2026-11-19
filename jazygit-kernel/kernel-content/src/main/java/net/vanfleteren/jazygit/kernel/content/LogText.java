package net.vanfleteren.jazygit.kernel.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.collection.List;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.core.git.model.CommitGraph;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.ui.commits.AheadView;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * The text of the log of a branch, as {@code git log} lists it, with a graph in its gutter. Only functions.
 */
public final class LogText {

    private static final DateTimeFormatter LOG_DATE_TIME =
            DateTimeFormatter.ofPattern(Messages.get("content.dateTimePattern"));

    private LogText() {}

    /**
     * The branch log, each commit as {@link #logLines} lists it: the {@code commit <sha>} part of each commit's first line is
     * yellow. The commits on the base are dimmed instead.
     */
    public static Text logText(List<Commit> commits, ZoneId zone, AheadView ahead, Theme.SpanStyles styles) {
        DateTimeFormatter dateTime = dateTime(zone);
        return ContentText.styledText(drawn(commits).flatMap(node -> {
            Sha sha = node.node().commit().sha();
            List<String> lines = logLines(node.node(), node.gutter(), dateTime);
            return ahead.dimmed(sha)
                    ? lines.map(line -> Line.from(ContentText.styled(line, Theme.Classes.DIM, styles)))
                    : lines.map(line -> line.startsWith("* ")
                            ? Line.from(
                                    Span.raw("* "),
                                    ContentText.styled(line.substring(2), Theme.Classes.CURRENT_BRANCH, styles))
                            : Line.from(Span.raw(line)));
        }));
    }

    /**
     * The commits as a {@code git log}-style listing, with dates in {@code zone}. Like a graph, the first
     * line of each commit starts with {@code "* "} and its other lines with {@code "| "}. The message is
     * indented.
     */
    public static List<String> logLines(List<Commit> commits, ZoneId zone) {
        DateTimeFormatter dateTime = dateTime(zone);
        return drawn(commits).flatMap(node -> logLines(node.node(), node.gutter(), dateTime));
    }

    /**
     * The lines of the commit of {@code node} in {@link #logLines(List, ZoneId)}, drawn with {@code gutter}.
     */
    private static List<String> logLines(CommitGraph.Node node, LogGraph.Gutter gutter, DateTimeFormatter dateTime) {
        Commit c = node.commit();
        List<String> lines = List.of(
                        Messages.get("content.commitSha", c.shortSha()),
                        Messages.get("content.author", c.authorName(), c.authorEmail()),
                        dateTime.format(c.authorTime()),
                        "")
                .appendAll(ContentText.messageLines(c))
                .append("");
        return gutter.draw(
                lines,
                Messages.get("content.merge", c.parents().map(Sha::shortSha).mkString(" ")));
    }

    /**
     * The nodes of the graph of {@code commits}, each with the gutter its lines are drawn with.
     */
    private static List<GraphRow> drawn(List<Commit> commits) {
        List<CommitGraph.Node> nodes = CommitGraph.layout(commits);
        return nodes.zipWith(LogGraph.gutters(nodes), GraphRow::new);
    }

    /**
     * How the log shows when a commit was made, in {@code zone}.
     */
    private static DateTimeFormatter dateTime(ZoneId zone) {
        return LOG_DATE_TIME.withZone(zone);
    }

    /**
     * A node of the graph of the log, with the gutter its lines are drawn with.
     */
    private record GraphRow(CommitGraph.Node node, LogGraph.Gutter gutter) {}
}

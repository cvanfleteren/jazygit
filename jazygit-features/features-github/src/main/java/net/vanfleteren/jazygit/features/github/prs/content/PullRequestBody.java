package net.vanfleteren.jazygit.features.github.prs.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.collection.List;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.kernel.ui.content.Body;
import net.vanfleteren.jazygit.kernel.ui.content.DrawContext;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.content.UnifiedLine;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * A pull request, and what it says and changes.
 */
public record PullRequestBody(PullRequestSummary pullRequest, PullRequestPreview preview) implements Body {

    private static final String MESSAGE_INDENT = "    ";

    @Override
    public Drawn draw(DrawContext context) {
        return Drawn.plain(
                List.ofAll(text(pullRequest, preview, context.styles()).lines()));
    }

    /**
     * Like a commit: a header with the pull request, who opened it, the branches it merges and when it changed,
     * then its indented title and description, a {@code ---} line, and what it changes.
     */
    public static Text text(PullRequestSummary pr, PullRequestPreview preview, Theme.SpanStyles styles) {
        List<String> body = List.ofAll(preview.body().lines());
        List<Line> lines = List.of(
                        Span.styled(
                                Messages.get(
                                        "github.content.pullRequest.header",
                                        pr.repository(),
                                        String.valueOf(pr.number().value())),
                                styles.of(Theme.Classes.SHA)),
                        Span.raw(Messages.get("github.content.pullRequest.author", pr.author())),
                        Span.raw(Messages.get(
                                pr.fromFork()
                                        ? "github.content.pullRequest.forkBranches"
                                        : "github.content.pullRequest.branches",
                                pr.headBranch().name(),
                                preview.base().name())),
                        Span.raw(Messages.get(
                                "github.content.pullRequest.updated",
                                DateTimeFormatter.ofPattern(
                                                Messages.get("github.content.pullRequest.datePattern"), Locale.ENGLISH)
                                        .format(pr.updatedAt().atZone(ZoneId.systemDefault())))),
                        Span.raw(""),
                        Span.styled(MESSAGE_INDENT + pr.title(), styles.of(Theme.Classes.BOLD)))
                .map(Line::from)
                .appendAll((body.isEmpty()
                                ? List.<String>empty()
                                : body.map(line -> line.isBlank() ? "" : MESSAGE_INDENT + line)
                                        .prepend(""))
                        .map(line -> Line.from(Span.raw(line))))
                .append(Line.from(Span.raw("---")))
                .appendAll(List.ofAll((preview.truncated()
                                        ? preview.diff() + Messages.get("github.content.pullRequest.truncated") + "\n"
                                        : preview.diff())
                                .lines())
                        .map(line -> UnifiedLine.of(line, styles)));
        return Text.from(lines.toJavaList());
    }
}

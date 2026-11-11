package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewer;
import net.vanfleteren.jazygit.gh.model.PullRequestEvent;
import net.vanfleteren.jazygit.gh.model.PullRequestEvent.Kind;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;

/**
 * PROTOTYPE (branch prototype/pr-panel): the preview of a pull request, by tab: an overview of its state, reviewers,
 * labels and checks above its description; its checks one by one; or its diff. Activity and commits are not in the
 * prototype.
 */
final class PrPreviewText {

    private static final String INDENT = "  ";

    private PrPreviewText() {}

    static Text of(PullRequestSummary pr, PullRequestPreview preview, int tab, Theme.SpanStyles styles) {
        List<Line> lines = switch (tab) {
            case 0 -> overview(pr, preview, styles);
            case 2 -> checks(preview, styles);
            case 1 -> activity(preview, styles);
            case 4 -> files(preview, styles);
            default -> List.of(Line.from(Span.styled("Not in the prototype.", styles.of(Theme.Classes.DIM))));
        };
        return Text.from(lines.toJavaList());
    }

    private static List<Line> overview(PullRequestSummary pr, PullRequestPreview preview, Theme.SpanStyles styles) {
        List<Line> head = List.of(
                Line.from(Span.raw(pr.title()).bold()),
                Line.from(Span.styled(pr.repository() + "#" + pr.number(), styles.of(Theme.Classes.SHA))));
        List<Line> details = preview.details()
                .map(read -> detailLines(pr, preview, read, styles))
                .getOrElse(() -> List.of(Line.from(Span.styled("(no details read)", styles.of(Theme.Classes.DIM)))));
        List<Line> body = List.of(preview.body().split("\n", -1))
                .map(line -> Line.from(Span.raw(line.isBlank() ? "" : INDENT + line)));
        return head.appendAll(details)
                .append(Line.from(Span.styled("─".repeat(60), styles.of(Theme.Classes.DIM))))
                .appendAll(body);
    }

    private static List<Line> detailLines(
            PullRequestSummary pr, PullRequestPreview preview, PullRequestDetails read, Theme.SpanStyles styles) {
        Instant now = Instant.now();
        var status = read.status();
        var branches = read.branches();
        var size = read.size();
        var conversation = read.conversation();
        var threads = conversation.reviewThreads();

        List<Span> state = List.of(
                        Span.styled(
                                status.draft()
                                        ? "◌ Draft"
                                        : "● " + capital(status.state().name()),
                                styles.of(status.draft() ? Theme.Classes.DIM : Theme.Classes.CHECKS_PASSED)),
                        Span.raw("  "),
                        switch (status.mergeable()) {
                            case MERGEABLE -> Span.styled("mergeable", styles.of(Theme.Classes.CHECKS_PASSED));
                            case CONFLICTING -> Span.styled("conflicts", styles.of(Theme.Classes.CHECKS_FAILED));
                            case UNKNOWN -> Span.styled("mergeable?", styles.of(Theme.Classes.DIM));
                        },
                        Span.raw("  " + branches.head().name() + " → "
                                + branches.base().name()))
                .appendAll(branches.behindBase()
                        .filter(behind -> behind > 0)
                        .map(behind ->
                                Span.styled(" (" + behind + " behind)", styles.of(Theme.Classes.CHECKS_RUNNING))));

        List<Span> who = List.of(
                Span.raw(INDENT + read.author()),
                Span.styled(
                        " · opened "
                                + TimeText.age(Duration.between(read.activity().createdAt(), now)) + " ago",
                        styles.of(Theme.Classes.DIM)),
                Span.styled(
                        " · pushed "
                                + TimeText.age(Duration.between(read.activity().pushedAt(), now)) + " ago",
                        styles.of(Theme.Classes.DIM)));

        List<Span> sizes = List.of(
                Span.raw(INDENT),
                Span.styled("+" + size.additions(), styles.of(Theme.Classes.DIFF_ADDED)),
                Span.raw(" "),
                Span.styled("−" + size.deletions(), styles.of(Theme.Classes.DIFF_REMOVED)),
                Span.raw(" · " + size.changedFiles() + " files · " + size.commits() + " commits · 💬 "
                        + conversation.comments() + " · threads " + threads.unresolved() + "/" + threads.threads()
                        + " open"));

        List<Span> reviewers = conversation.reviewers().isEmpty()
                ? List.of(Span.styled(INDENT + "Reviewers: none", styles.of(Theme.Classes.DIM)))
                : conversation
                        .reviewers()
                        .flatMap(reviewer -> reviewer(reviewer, styles))
                        .prepend(Span.raw(INDENT + "Reviewers:"));

        Option<Line> labels = Option.when(
                !conversation.labels().isEmpty(),
                () -> Line.from(conversation
                        .labels()
                        .map(label -> Span.styled(" " + label + " ", styles.of(Theme.Classes.TAG)))
                        .intersperse(Span.raw(" "))
                        .prepend(Span.raw(INDENT + "Labels: "))
                        .toJavaList()));

        List<Span> checks = read.checks().isEmpty()
                ? List.of(Span.styled(INDENT + "Checks: none", styles.of(Theme.Classes.DIM)))
                : checkSummary(read.checks(), styles).prepend(Span.raw(INDENT + "Checks:"));

        return List.of(state.prepend(Span.raw(INDENT)), who, sizes, reviewers)
                .map(spans -> Line.from(spans.toJavaList()))
                .appendAll(labels)
                .append(Line.from(checks.toJavaList()));
    }

    private static List<Span> reviewer(Reviewer reviewer, Theme.SpanStyles styles) {
        return switch (reviewer.state()) {
            case APPROVED ->
                List.of(Span.styled(" " + reviewer.login() + " ✓", styles.of(Theme.Classes.REVIEW_APPROVED)));
            case CHANGES_REQUESTED ->
                List.of(Span.styled(" " + reviewer.login() + " ✗ changes", styles.of(Theme.Classes.REVIEW_CHANGES)));
            case COMMENTED -> List.of(Span.raw(" " + reviewer.login() + " 💬"));
            case REQUESTED ->
                List.of(Span.styled(" " + reviewer.login() + " ○ requested", styles.of(Theme.Classes.DIM)));
        };
    }

    /**
     * The failing and running checks by name, the passing ones counted.
     */
    private static List<Span> checkSummary(List<Check> checks, Theme.SpanStyles styles) {
        int passing = checks.count(check -> check.state() == Checks.PASSING);
        return checks.filter(check -> check.state() == Checks.FAILING || check.state() == Checks.PENDING)
                .map(check -> check(check, styles))
                .appendAll(Option.when(
                        passing > 0,
                        () -> Span.styled(" ✓ " + passing + " passed", styles.of(Theme.Classes.CHECKS_PASSED))));
    }

    private static Span check(Check check, Theme.SpanStyles styles) {
        return switch (check.state()) {
            case FAILING -> Span.styled(" ✗ " + check.name(), styles.of(Theme.Classes.CHECKS_FAILED));
            case PENDING -> Span.styled(" ◐ " + check.name(), styles.of(Theme.Classes.CHECKS_RUNNING));
            case PASSING -> Span.styled(" ✓ " + check.name(), styles.of(Theme.Classes.CHECKS_PASSED));
            case NONE -> Span.styled(" – " + check.name(), styles.of(Theme.Classes.DIM));
        };
    }

    private static List<Line> checks(PullRequestPreview preview, Theme.SpanStyles styles) {
        return preview.details()
                .map(read -> read.checks()
                        .sortBy(check -> check.state().ordinal() == Checks.FAILING.ordinal() ? 0 : 1)
                        .map(check -> Line.from(check(check, styles))))
                .filter(lines -> !lines.isEmpty())
                .getOrElse(() -> List.of(Line.from(Span.styled("No checks", styles.of(Theme.Classes.DIM)))));
    }

    // How many lines of what was written show, before the rest is counted.
    private static final int BODY_LINES = 8;
    private static final String GUTTER = "        │ ";

    /**
     * What happened, newest first; the commits one author pushed one after the other as one entry.
     */
    private static List<Line> activity(PullRequestPreview preview, Theme.SpanStyles styles) {
        Instant now = Instant.now();
        return preview.timeline()
                .map(events -> grouped(events)
                        .reverse()
                        .flatMap(group -> entry(group, now, styles).append(Line.from(Span.raw("")))))
                .filter(lines -> !lines.isEmpty())
                .getOrElse(() -> List.of(Line.from(Span.styled(
                        preview.timeline().isDefined() ? "Nothing happened yet" : "(activity not read)",
                        styles.of(Theme.Classes.DIM)))));
    }

    /**
     * The events, oldest first, with the commits one author made one after the other together.
     */
    private static List<List<PullRequestEvent>> grouped(List<PullRequestEvent> events) {
        return events.foldLeft(List.<List<PullRequestEvent>>empty(), (groups, event) -> groups.lastOption()
                        .filter(last -> mergeable(last.head()) && mergeable(event)
                                && last.head().kind() == event.kind()
                                && last.head().actor().equals(event.actor()))
                        .map(last -> groups.init().append(last.append(event)))
                        .getOrElse(() -> groups.append(List.of(event))));
    }

    /**
     * Whether {@code event} goes together with the ones of its kind by the same author right before it: commits, and
     * reviews that only remark on lines.
     */
    private static boolean mergeable(PullRequestEvent event) {
        return event.kind() == Kind.COMMITTED || (event.kind() == Kind.REVIEWED && event.body().isBlank());
    }

    private static List<Line> entry(List<PullRequestEvent> group, Instant now, Theme.SpanStyles styles) {
        PullRequestEvent event = group.last();
        Span what = what(group, styles);
        Line head = Line.from(
                Span.styled("%5s ".formatted(TimeText.age(Duration.between(event.at(), now))), styles.of(Theme.Classes.DIM)),
                Span.raw(event.actor().isEmpty() ? "someone" : event.actor()).bold(),
                Span.raw(" "),
                what);
        List<Line> commits = event.kind() == Kind.COMMITTED
                ? group.reverse().map(commit -> gutter(Span.styled(commit.subject(), styles.of(Theme.Classes.DIM)), styles))
                : List.empty();
        List<Line> remarks = group.flatMap(PullRequestEvent::remarks)
                .flatMap(remark -> body(remark.body(), styles, 3)
                        .prepend(gutter(
                                Span.styled(
                                        "├ " + remark.path() + remark.line().map(line -> ":" + line).getOrElse(""),
                                        styles.of(Theme.Classes.SHA)),
                                styles)));
        return body(event.body(), styles, BODY_LINES).prependAll(commits).appendAll(remarks).prepend(head);
    }

    private static Span what(List<PullRequestEvent> group, Theme.SpanStyles styles) {
        PullRequestEvent event = group.last();
        return switch (event.kind()) {
            case COMMENTED -> Span.styled("💬 commented", styles.of(Theme.Classes.DIM));
            case APPROVED -> Span.styled("✓ approved", styles.of(Theme.Classes.REVIEW_APPROVED));
            case CHANGES_REQUESTED -> Span.styled("✗ requested changes", styles.of(Theme.Classes.REVIEW_CHANGES));
            case REVIEWED -> Span.styled(
                    "◇ reviewed" + remarkCount(group.flatMap(PullRequestEvent::remarks).size()),
                    styles.of(Theme.Classes.DIM));
            case COMMITTED -> Span.styled(
                    "↑ pushed " + group.size() + (group.size() == 1 ? " commit" : " commits"),
                    styles.of(Theme.Classes.PULL_REQUEST));
            case FORCE_PUSHED -> Span.styled("⇡ force-pushed", styles.of(Theme.Classes.CHECKS_RUNNING));
            case REVIEW_REQUESTED -> Span.styled(
                    "○ asked " + event.subject() + " to review", styles.of(Theme.Classes.DIM));
            case READY_FOR_REVIEW -> Span.styled("● marked ready for review", styles.of(Theme.Classes.CHECKS_PASSED));
            case CONVERTED_TO_DRAFT -> Span.styled("◌ converted to draft", styles.of(Theme.Classes.DIM));
            case LABELED -> Span.styled("labeled [" + event.subject() + "]", styles.of(Theme.Classes.TAG));
            case MERGED -> Span.styled("⛙ merged", styles.of(Theme.Classes.CHECKS_PASSED));
            case CLOSED -> Span.styled("✕ closed", styles.of(Theme.Classes.CHECKS_FAILED));
            case REOPENED -> Span.styled("● reopened", styles.of(Theme.Classes.CHECKS_PASSED));
        };
    }

    private static String remarkCount(int remarks) {
        return remarks == 0 ? "" : remarks == 1 ? ", 1 remark" : ", " + remarks + " remarks";
    }

    /**
     * The first {@code most} lines of {@code text} that are not blank, in the gutter, and how many more there are.
     */
    private static List<Line> body(String text, Theme.SpanStyles styles, int most) {
        List<String> lines = List.of(text.strip().split("\\R", -1)).filter(line -> !line.isBlank());
        return lines.take(most)
                .map(line -> gutter(Span.raw(line), styles))
                .appendAll(Option.when(
                        lines.size() > most,
                        () -> gutter(Span.styled("… " + (lines.size() - most) + " more lines", styles.of(Theme.Classes.DIM)), styles)));
    }

    private static Line gutter(Span span, Theme.SpanStyles styles) {
        return Line.from(Span.styled(GUTTER, styles.of(Theme.Classes.DIM)), span);
    }

    private static List<Line> files(PullRequestPreview preview, Theme.SpanStyles styles) {
        return List.of(preview.diff().split("\n", -1)).map(line -> ContentPanel.diffLine(line, styles));
    }

    private static String capital(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}

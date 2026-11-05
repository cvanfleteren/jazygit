package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Iterator;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * What the preview of a pull request of another repository shows: its base branch and its body, read with
 * {@code gh pr view --json}, and its diff (ADR 42).
 */
final class PreviewQuery {

    // The most of a diff of a pull request that is shown, in lines and in characters, as for the diffs of git (ADR 79).
    static final int MAX_DIFF_LINES = 50_000;
    static final int MAX_DIFF_CHARS = 4 * 1024 * 1024;

    // Its base branch and its body.
    static final Decoder<Tuple2<String, String>> DECODER =
            map2(field("baseRefName", string()), field("body", string()), Tuple::of);

    private PreviewQuery() {}

    static GhCommand command(String url) {
        return gh("pr", "view", url, "--json", "baseRefName,body");
    }

    static GhCommand diffCommand(String url) {
        return gh("pr", "diff", url, "--color", "never");
    }

    /**
     * The preview of a pull request into {@code base}, with its body and its diff, cut off after
     * {@link #MAX_DIFF_LINES} lines, or before the line that would make it longer than {@link #MAX_DIFF_CHARS}
     * characters: its text up to there as gh wrote it.
     */
    static PullRequestPreview preview(String base, String body, String diff) {
        return cut(diff)
                .map(end -> new PullRequestPreview(base, body.stripTrailing(), diff.substring(0, end), true))
                .getOrElse(() -> new PullRequestPreview(base, body.stripTrailing(), diff, false));
    }

    /**
     * Where {@code diff} is cut off, as git's diffs are: after the last whole line that keeps it within
     * {@link #MAX_DIFF_LINES} lines and {@link #MAX_DIFF_CHARS} characters. Only {@code \n} ends a line. None when it
     * is within both whole.
     */
    private static Option<Integer> cut(String diff) {
        long lines = diff.chars().filter(c -> c == '\n').count() + (diff.isEmpty() || diff.endsWith("\n") ? 0 : 1);
        if (lines <= MAX_DIFF_LINES && diff.length() <= MAX_DIFF_CHARS) {
            return Option.none();
        }
        return Option.of(Iterator.iterate(diff.indexOf('\n'), at -> diff.indexOf('\n', at + 1))
                .takeWhile(at -> at >= 0)
                .map(at -> at + 1)
                .take(MAX_DIFF_LINES)
                .takeWhile(end -> end <= MAX_DIFF_CHARS)
                .foldLeft(0, (last, end) -> end));
    }
}

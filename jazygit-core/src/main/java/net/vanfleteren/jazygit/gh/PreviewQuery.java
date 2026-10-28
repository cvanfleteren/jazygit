package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * What the preview of a pull request of another repository shows: its base branch and its body, read with
 * {@code gh pr view --json}, and its diff (ADR 42).
 */
final class PreviewQuery {

    // The most lines of a diff of a pull request that are shown, as for the diffs of git.
    static final int MAX_DIFF_LINES = 5000;

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
     * {@link #MAX_DIFF_LINES} lines.
     */
    static PullRequestPreview preview(String base, String body, String diff) {
        List<String> lines = List.of(diff.split("\n", -1));
        return lines.size() <= MAX_DIFF_LINES
                ? new PullRequestPreview(base, body.stripTrailing(), diff, false)
                : new PullRequestPreview(
                        base, body.stripTrailing(), lines.take(MAX_DIFF_LINES).mkString("", "\n", "\n"), true);
    }
}

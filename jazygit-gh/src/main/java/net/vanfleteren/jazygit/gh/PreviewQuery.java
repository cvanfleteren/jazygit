package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.json.Decoder;
import net.vanfleteren.jazygit.process.Limit;
import net.vanfleteren.jazygit.process.Limited;

/**
 * What the preview of a pull request of another repository shows: its base branch and its body, read with
 * {@code gh pr view --json}, and its diff (ADR 42).
 */
final class PreviewQuery {

    // The most of a diff of a pull request that is read and shown, as of the diffs of git (ADR 79).
    static final Limit DIFF_LIMIT = new Limit(50_000, 4 * 1024 * 1024);

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
     * The preview of a pull request into {@code base}, with its body and the {@code diff} read of it.
     */
    static PullRequestPreview preview(String base, String body, Limited diff) {
        return new PullRequestPreview(base, body.stripTrailing(), diff.text(), diff.cut());
    }
}

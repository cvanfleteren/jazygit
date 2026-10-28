package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.fail;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.optionalField;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * A merge in the background through REST's {@code merge-async} (ADR 24), and how it is doing: its status, its id
 * while it is pending, and why when it failed (ADR 42).
 */
final class MergeQuery {

    static final Decoder<MergeStatus> DECODER = field("status", string()).flatMap(status -> switch (status) {
        case "pending" ->
            at(
                            "details.uuid",
                            string().flatMap(id -> id.isEmpty() ? fail("expected the id of the merge") : succeed(id)))
                    .map(MergeStatus.Pending::new);
        case "merged" -> succeed(new MergeStatus.Merged());
        case "enqueued" -> succeed(new MergeStatus.Enqueued());
        // A merge that failed is told as failed, whatever GitHub gives or leaves out of why.
        case "failed" ->
            optionalField("details", nullable(optionalField("message", nullable(string()))))
                    .map(details -> new MergeStatus.Failed(details.flatMap(d -> d)
                            .flatMap(message -> message)
                            .flatMap(message -> message)
                            .getOrElse("")));
        default -> fail("expected pending, merged, enqueued or failed, got \"" + status + "\"");
    });

    private MergeQuery() {}

    /**
     * The command that has GitHub merge pull request {@code number} in the background, only while its branch is at
     * {@code head}, if given. gh fills in the repository of the working tree for {owner} and {repo}.
     */
    static GhCommand mergeCommand(int number, Option<GhCommit> head, MergeMethod method) {
        return gh(List.of(
                        "api",
                        "--method",
                        "PUT",
                        "repos/{owner}/{repo}/pulls/" + number + "/merge-async",
                        "-f",
                        "merge_method=" + mergeMethod(method))
                .appendAll(head.toList().flatMap(commit -> List.of("-f", "sha=" + commit.oid())))
                .toJavaArray(String[]::new));
    }

    /**
     * The command that asks GitHub how the merge {@code id} of pull request {@code number} is doing.
     */
    static GhCommand statusCommand(int number, String id) {
        return gh("api", "repos/{owner}/{repo}/pulls/" + number + "/merge-async/" + id);
    }

    private static String mergeMethod(MergeMethod method) {
        return switch (method) {
            case MERGE -> "merge";
            case SQUASH -> "squash";
            case REBASE -> "rebase";
        };
    }
}

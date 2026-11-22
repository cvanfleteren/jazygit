package net.vanfleteren.jazygit.core.forgejo;

import static net.vanfleteren.jazygit.lib.json.Decoder.at;
import static net.vanfleteren.jazygit.lib.json.Decoder.bool;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.instant;
import static net.vanfleteren.jazygit.lib.json.Decoder.integer;
import static net.vanfleteren.jazygit.lib.json.Decoder.list;
import static net.vanfleteren.jazygit.lib.json.Decoder.longValue;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.map3;
import static net.vanfleteren.jazygit.lib.json.Decoder.map4;
import static net.vanfleteren.jazygit.lib.json.Decoder.map6;
import static net.vanfleteren.jazygit.lib.json.Decoder.map7;
import static net.vanfleteren.jazygit.lib.json.Decoder.optionalField;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Activity;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Branches;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Conversation;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Size;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.State;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails.Status;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * How Forgejo's API gives its pull requests and their reviews (ADR 176), and how a repository lets them be merged
 * (ADR 180), as {@code /api/v1} answers them; what it leaves out or nulls is empty, false or 0.
 */
final class PullRequestJson {

    private static final Decoder<String> LOGIN =
            optionalField("login", string()).map(login -> login.getOrElse(""));

    private static final Decoder<String> USER = optionalField("user", LOGIN).map(login -> login.getOrElse(""));

    /**
     * A pull request as the list of the open ones gives it.
     */
    private static final Decoder<ForgejoPullRequest> SUMMARY = map7(
            field("number", longValue()),
            text("title"),
            at("head", text("ref")),
            field("head", optionalField("repo", optionalField("full_name", string())))
                    .map(repo -> repo.flatMap(name -> name)),
            at("base", text("ref")),
            flag("draft"),
            text("html_url"),
            ForgejoPullRequest::new);

    private static final Decoder<Status> STATUS = map3(
            map2(
                    text("state"),
                    flag("merged"),
                    (state, merged) ->
                            merged ? State.MERGED : state.equalsIgnoreCase("open") ? State.OPEN : State.CLOSED),
            flag("draft"),
            flag("mergeable"),
            Status::new);

    private static final Decoder<Branches> BRANCHES = map3(
            at("head", text("ref")),
            at("base", text("ref")),
            at("head", text("sha")).map(Sha::new),
            Branches::new);

    private static final Decoder<Activity> ACTIVITY =
            map2(field("created_at", instant()), field("updated_at", instant()), Activity::new);

    private static final Decoder<Size> SIZE =
            map3(count("additions"), count("deletions"), count("changed_files"), Size::new);

    private static final Decoder<Conversation> CONVERSATION = map3(
            count("comments"),
            names("labels", optionalField("name", string()).map(name -> name.getOrElse(""))),
            names("requested_reviewers", LOGIN),
            Conversation::new);

    /**
     * A pull request as it is read to be shown.
     */
    private static final Decoder<ForgejoPullRequestDetails> DETAILS = map4(
            map3(field("number", longValue()), text("title"), USER, Header::new),
            STATUS,
            BRANCHES,
            map4(ACTIVITY, SIZE, CONVERSATION, text("html_url"), Rest::new),
            (header, status, branches, rest) -> new ForgejoPullRequestDetails(
                    header.number(),
                    header.title(),
                    header.author(),
                    status,
                    branches,
                    rest.activity(),
                    rest.size(),
                    rest.conversation(),
                    rest.url()));

    private static final Decoder<ForgejoReview> REVIEW = map4(
            USER,
            text("state").map(ForgejoReview.Verdict::named),
            flag("stale"),
            flag("dismissed"),
            ForgejoReview::new);

    /**
     * How a repository lets its pull requests be merged: a style it does not say it allows is not allowed.
     */
    private static final Decoder<ForgejoMergeSettings> MERGE_SETTINGS = map6(
            flag("allow_merge_commits"),
            flag("allow_squash_merge"),
            flag("allow_rebase"),
            flag("allow_rebase_explicit"),
            flag("allow_fast_forward_only_merge"),
            flag("default_delete_branch_after_merge"),
            (merge, squash, rebase, rebaseMerge, fastForward, delete) -> new ForgejoMergeSettings(
                    List.of(
                                    Tuple.of(merge, ForgejoMergeStyle.MERGE),
                                    Tuple.of(squash, ForgejoMergeStyle.SQUASH),
                                    Tuple.of(rebase, ForgejoMergeStyle.REBASE),
                                    Tuple.of(rebaseMerge, ForgejoMergeStyle.REBASE_MERGE),
                                    Tuple.of(fastForward, ForgejoMergeStyle.FAST_FORWARD_ONLY))
                            .filter(Tuple2::_1)
                            .map(Tuple2::_2)
                            .toLinkedSet(),
                    delete));

    private PullRequestJson() {}

    /**
     * How a repository lets its pull requests be merged.
     */
    static Decoder<ForgejoMergeSettings> mergeSettings() {
        return MERGE_SETTINGS;
    }

    /**
     * The pull requests of a page of the list of the open ones.
     */
    static Decoder<List<ForgejoPullRequest>> summaries() {
        return list(SUMMARY);
    }

    /**
     * A pull request as creating it answers it (ADR 178), with what the list of the open ones gives.
     */
    static Decoder<ForgejoPullRequest> summary() {
        return SUMMARY;
    }

    /**
     * A pull request as it is read to be shown.
     */
    static Decoder<ForgejoPullRequestDetails> details() {
        return DETAILS;
    }

    /**
     * The reviews of a pull request.
     */
    static Decoder<List<ForgejoReview>> reviews() {
        return list(REVIEW);
    }

    private static Decoder<String> text(String name) {
        return optionalField(name, string()).map(value -> value.getOrElse(""));
    }

    private static Decoder<Boolean> flag(String name) {
        return optionalField(name, bool()).map(value -> value.getOrElse(false));
    }

    private static Decoder<Integer> count(String name) {
        return optionalField(name, integer()).map(value -> value.getOrElse(0));
    }

    /**
     * The non-empty names {@code name} decodes of each element of the array {@code field}; none without it.
     */
    private static Decoder<List<String>> names(String field, Decoder<String> name) {
        return optionalField(field, list(name))
                .map(names -> names.getOrElse(List.empty()).filter(each -> !each.isEmpty()));
    }

    private record Header(long number, String title, String author) {}

    private record Rest(Activity activity, Size size, Conversation conversation, String url) {}
}

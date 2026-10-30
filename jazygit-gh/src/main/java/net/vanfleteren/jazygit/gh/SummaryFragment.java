package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.instant;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.map5;
import static net.vanfleteren.jazygit.json.Decoder.map6;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple5;
import io.vavr.Tuple6;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * What a list of pull requests shows of each, in GraphQL and as it is decoded (ADR 42). Its checks are the state of
 * their rollup on its last commit, and pending while a workflow run on it waits to start.
 */
final class SummaryFragment {

    static final String FRAGMENT = """
            fragment summary on PullRequest { number title url updatedAt isDraft isCrossRepository headRefName \
            author { login } repository { nameWithOwner } reviewDecision %s \
            commits(last: 1) { nodes { commit { statusCheckRollup { state } %s } } } }""".formatted(StackFragment.FIELDS, SuiteFragment.FIELDS);

    // The checks on a commit: the state of their rollup, and PENDING for each workflow run that waits to start.
    static final Decoder<Checks> COMMIT = map2(
            field("statusCheckRollup", nullable(field("state", string()))),
            SuiteFragment.WAITING,
            (state, waiting) -> CheckFragment.checks(
                    state.toList().filter(value -> !value.isEmpty()).appendAll(waiting.map(run -> "PENDING"))));

    // The checks on the last commit of a pull request, none without one.
    static final Decoder<Checks> CHECKS = at("commits.nodes", list(field("commit", COMMIT)))
            .map(commits -> commits.headOption().getOrElse(Checks.NONE));

    // Its repository, number, title, author, empty for a deleted account, head branch and url.
    static final Decoder<Tuple6<String, Integer, String, String, String, String>> NAMED = map6(
            at("repository.nameWithOwner", string()),
            field("number", integer()),
            field("title", string()),
            field("author", nullable(field("login", string()))).map(login -> login.getOrElse("")),
            field("headRefName", string()),
            field("url", string()),
            Tuple::of);

    // Whether it is a draft and from a fork, its review, its checks and when it was updated.
    static final Decoder<Tuple5<Boolean, Boolean, Review, Checks, Instant>> STATE = map5(
            field("isDraft", bool()),
            field("isCrossRepository", bool()),
            field("reviewDecision", nullable(string())).map(decision -> CliGitHub.review(decision.getOrElse(""))),
            CHECKS,
            field("updatedAt", instant()),
            Tuple::of);

    static final Decoder<PullRequestSummary> SUMMARY = map3(
            NAMED,
            STATE,
            StackFragment.DECODER,
            (named, state, stack) -> new PullRequestSummary(
                    named._1(),
                    named._2(),
                    named._3(),
                    named._4(),
                    named._5(),
                    state._1(),
                    state._2(),
                    state._4(),
                    state._3(),
                    state._5(),
                    named._6(),
                    stack));

    /**
     * A hit of a search of issues: a pull request, or none for an issue, which answers only its {@code __typename}.
     */
    static final Decoder<Option<PullRequestSummary>> HIT = byField(
            "__typename",
            HashMap.of("PullRequest", SUMMARY.map(Option::some)),
            succeed(Option.<PullRequestSummary>none()));

    private SummaryFragment() {}

    /**
     * A list of {@code nodes} that {@code node} decodes, of which {@code total} match in all.
     */
    static Decoder<PullRequestList> pullRequests(String nodes, Decoder<Option<PullRequestSummary>> node, String total) {
        return map2(
                field(nodes, list(node)).map(found -> found.flatMap(summary -> summary)),
                field(total, integer()),
                PullRequestList::new);
    }
}

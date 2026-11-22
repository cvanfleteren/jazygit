package net.vanfleteren.jazygit.core.gh.graphql;

import static net.vanfleteren.jazygit.lib.json.Decoder.at;
import static net.vanfleteren.jazygit.lib.json.Decoder.bool;
import static net.vanfleteren.jazygit.lib.json.Decoder.byField;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.instant;
import static net.vanfleteren.jazygit.lib.json.Decoder.integer;
import static net.vanfleteren.jazygit.lib.json.Decoder.list;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.map5;
import static net.vanfleteren.jazygit.lib.json.Decoder.map6;
import static net.vanfleteren.jazygit.lib.json.Decoder.nullable;
import static net.vanfleteren.jazygit.lib.json.Decoder.optionalField;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;
import static net.vanfleteren.jazygit.lib.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple5;
import io.vavr.Tuple6;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * What a list of pull requests shows of each, in GraphQL and as it is decoded (ADR 42). Its checks are the state of
 * their rollup on its last commit, and pending while a workflow run on it waits to start.
 */
public final class SummaryFragment {

    public static final String FRAGMENT = """
            fragment summary on PullRequest { number title url updatedAt isDraft isCrossRepository headRefName baseRefName headRefOid baseRefOid \
            headRepository { nameWithOwner } \
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
    static final Decoder<Tuple6<String, PrNumber, String, String, GhBranch, String>> NAMED = map6(
            at("repository.nameWithOwner", string()),
            field("number", integer().map(PrNumber::new)),
            field("title", string()),
            field("author", nullable(field("login", string()))).map(login -> login.getOrElse("")),
            field("headRefName", string()).map(GhBranch::new),
            field("url", string()),
            Tuple::of);

    // Whether it is a draft and from a fork, its review, its checks and when it was updated.
    static final Decoder<Tuple5<Boolean, Boolean, Review, Checks, Instant>> STATE = map5(
            field("isDraft", bool()),
            field("isCrossRepository", bool()),
            ReviewingFragment.DECISION,
            CHECKS,
            field("updatedAt", instant()),
            Tuple::of);

    // The commits its base and its branch are at, which its changes are diffed between (ADR 61); none for a branch
    // that is gone.
    static final Decoder<Tuple2<Option<Sha>, Option<Sha>>> COMMITS =
            map2(commit("baseRefOid"), commit("headRefOid"), Tuple::of);

    // The repository its branch is in, none for a fork that was deleted (ADR 177).
    static final Decoder<Option<String>> HEAD_REPOSITORY =
            field("headRepository", nullable(field("nameWithOwner", string())));

    public static final Decoder<PullRequestSummary> SUMMARY = map6(
            NAMED,
            STATE,
            StackFragment.DECODER,
            COMMITS,
            field("baseRefName", string()).map(GhBranch::named),
            HEAD_REPOSITORY,
            (named, state, stack, commits, base, head) -> new PullRequestSummary(
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
                    stack,
                    commits._1(),
                    commits._2(),
                    base,
                    head));

    /**
     * A hit of a search of issues: a pull request, or none for an issue, which answers only its {@code __typename}.
     */
    public static final Decoder<Option<PullRequestSummary>> HIT = byField(
            "__typename",
            HashMap.of("PullRequest", SUMMARY.map(Option::some)),
            succeed(Option.<PullRequestSummary>none()));

    private SummaryFragment() {}

    private static Decoder<Option<Sha>> commit(String name) {
        return optionalField(name, nullable(string())).map(oid -> oid.flatMap(read -> read.flatMap(Sha::parse)));
    }

    /**
     * A list of {@code nodes} that {@code node} decodes, of which {@code total} match in all.
     */
    public static Decoder<PullRequestList> pullRequests(
            String nodes, Decoder<Option<PullRequestSummary>> node, String total) {
        return map2(
                field(nodes, list(node)).map(found -> found.flatMap(summary -> summary)),
                field(total, integer()),
                PullRequestList::new);
    }
}

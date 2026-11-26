package net.vanfleteren.jazygit.core.gh.graphql;

import static net.vanfleteren.jazygit.lib.json.Decoder.at;
import static net.vanfleteren.jazygit.lib.json.Decoder.bool;
import static net.vanfleteren.jazygit.lib.json.Decoder.enumOf;
import static net.vanfleteren.jazygit.lib.json.Decoder.fail;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.integer;
import static net.vanfleteren.jazygit.lib.json.Decoder.list;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.map3;
import static net.vanfleteren.jazygit.lib.json.Decoder.map7;
import static net.vanfleteren.jazygit.lib.json.Decoder.nullable;
import static net.vanfleteren.jazygit.lib.json.Decoder.optionalField;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;
import static net.vanfleteren.jazygit.lib.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * Where a pull request sits in its stack, and the pull requests of it, in GraphQL and as it is decoded (ADR 24, 42).
 */
public final class StackFragment {

    // The stack of a pull request and where it sits in it, in GraphQL: both null when it is not stacked.
    public static final String FIELDS = """
            stackEntry { position } stack { number size \
            entries(first: 100) { nodes { position pullRequest { number state headRefName baseRefName headRefOid \
            isInMergeQueue isCrossRepository } } } }""";

    // A pull request of a stack; none when its pull request cannot be read.
    static final Decoder<Option<PullRequestStack.Entry>> ENTRY = nullable(map2(
                    field("position", integer()),
                    field(
                            "pullRequest",
                            nullable(map7(
                                    field("number", integer().map(PrNumber::new)),
                                    field("state", enumOf(State.class)),
                                    field("headRefName", string()).map(GhBranch::named),
                                    field("baseRefName", string()).map(GhBranch::named),
                                    // Read since ADR 74; an answer without them is still read.
                                    optionalField("headRefOid", nullable(string()))
                                            .map(oid ->
                                                    oid.flatMap(read -> read).flatMap(Sha::parse)),
                                    optionalField("isInMergeQueue", bool()).map(queued -> queued.getOrElse(false)),
                                    // Read since ADR 99.
                                    optionalField("isCrossRepository", bool()).map(fork -> fork.getOrElse(false)),
                                    Tuple::of))),
                    (position, pullRequest) -> pullRequest.map(pr -> new PullRequestStack.Entry(
                            position, pr._1(), pr._2(), pr._3(), pr._4(), pr._5(), pr._6(), pr._7()))))
            .map(entry -> entry.flatMap(readable -> readable));

    // The number, the size and the pull requests of a stack.
    static final Decoder<Tuple3<StackId, Integer, List<PullRequestStack.Entry>>> STACK = map3(
            field("number", integer().map(StackId::new)),
            field("size", integer()),
            at("entries.nodes", list(ENTRY)).map(entries -> entries.flatMap(entry -> entry)),
            Tuple::of);

    /**
     * Where a pull request of {@link #FIELDS} sits in its stack: none when it is not stacked, and a failure when it
     * has a stack but no place in it, or the other way round.
     */
    public static final Decoder<Option<PullRequestStack>> DECODER = map2(
                    field("stack", nullable(STACK)),
                    field("stackEntry", nullable(field("position", integer()))),
                    Tuple::of)
            .flatMap(stacked -> stacked._1().isEmpty() && stacked._2().isEmpty()
                    ? succeed(Option.<PullRequestStack>none())
                    : stacked._1()
                            .flatMap(stack -> stacked._2()
                                    .map(position ->
                                            new PullRequestStack(stack._1(), position, stack._2(), stack._3())))
                            .fold(
                                    () -> fail("expected both a stack and a place in it, or neither"),
                                    stack -> succeed(Option.some(stack))));

    private StackFragment() {}
}

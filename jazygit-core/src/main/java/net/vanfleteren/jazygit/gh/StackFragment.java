package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.enumOf;
import static net.vanfleteren.jazygit.json.Decoder.fail;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * Where a pull request sits in its stack, and the pull requests of it, in GraphQL and as it is decoded (ADR 24, 41).
 */
final class StackFragment {

    // The stack of a pull request and where it sits in it, in GraphQL: both null when it is not stacked.
    static final String FIELDS = """
            stackEntry { position } stack { number size \
            entries(first: 100) { nodes { position pullRequest { number state } } } }""";

    // A pull request of a stack; none when its pull request cannot be read.
    static final Decoder<Option<PullRequestStack.Entry>> ENTRY = nullable(map2(
                    field("position", integer()),
                    field(
                            "pullRequest",
                            nullable(map2(field("number", integer()), field("state", enumOf(State.class)), Tuple::of))),
                    (position, pullRequest) ->
                            pullRequest.map(pr -> new PullRequestStack.Entry(position, pr._1(), pr._2()))))
            .map(entry -> entry.flatMap(readable -> readable));

    // The number, the size and the pull requests of a stack.
    static final Decoder<Tuple3<Integer, Integer, List<PullRequestStack.Entry>>> STACK = map3(
            field("number", integer()),
            field("size", integer()),
            at("entries.nodes", list(ENTRY)).map(entries -> entries.flatMap(entry -> entry)),
            Tuple::of);

    /**
     * Where a pull request of {@link #FIELDS} sits in its stack: none when it is not stacked, and a failure when it
     * has a stack but no place in it, or the other way round.
     */
    static final Decoder<Option<PullRequestStack>> DECODER = map2(
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

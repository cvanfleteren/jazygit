package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Either;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * How far behind its base a pull request is, from what GitHub answers the query of {@link BehindQuery}.
 */
class BehindQueryTest {

    private static Either<GhError.UnexpectedOutput, Option<Integer>> behind(String ref) {
        return GhProcess.decoded("{\"data\": {\"repository\": {\"ref\": " + ref + "}}}", BehindQuery.DECODER);
    }

    @Test
    void howFarBehindItsBaseAPullRequestIs() {
        assertEquals(Either.right(Option.of(3)), behind("{\"compare\": {\"behindBy\": 3}}"));
    }

    @Test
    void aBaseThatIsGoneOrCannotBeComparedIsNotKnown() {
        assertEquals(Either.right(Option.none()), behind("null"));
        assertEquals(Either.right(Option.none()), behind("{\"compare\": null}"));
        assertEquals(Either.right(Option.none()), behind("{\"compare\": {\"behindBy\": null}}"));
    }

    @Test
    void aCountThatIsNoNumberIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput(
                        "data.repository.ref.compare.behindBy: expected a number, got a string")),
                behind("{\"compare\": {\"behindBy\": \"many\"}}"));
    }
}

package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import org.junit.jupiter.api.Test;

/**
 * The protected branches of the repository, from all pages GitHub answers (ADR 72).
 */
class ProtectedBranchesQueryTest {

    private static Either<GhError.UnexpectedOutput, List<String>> protectedBranches(String json) {
        return GhProcess.decoded(json, ProtectedBranchesQuery.DECODER);
    }

    @Test
    void theNamesOfTheProtectedBranchesOverAllPages() {
        assertEquals(Either.right(List.of("main", "develop", "release/1")), protectedBranches("""
                        [ [ { "name": "main", "protected": true, "commit": { "sha": "abc" } },
                            { "name": "develop", "protected": true } ],
                          [ { "name": "release/1", "protected": true } ] ]"""));
    }

    @Test
    void aRepositoryThatProtectsNothingHasNoProtectedBranches() {
        assertEquals(Either.right(List.empty()), protectedBranches("[ [] ]"));
    }

    @Test
    void theCommandAsksOnlyForTheProtectedBranches() {
        assertEquals(
                List.of("api", "repos/{owner}/{repo}/branches?protected=true&per_page=100", "--paginate", "--slurp"),
                ProtectedBranchesQuery.COMMAND.args());
    }
}

package net.vanfleteren.jazygit.core.gh.pullrequests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhCommit;
import net.vanfleteren.jazygit.core.gh.model.MergeMethod;
import org.junit.jupiter.api.Test;

/**
 * Turning auto-merge on and off through GraphQL (ADR 112).
 */
class AutoMergeMutationTest {

    @Test
    void autoMergeIsTurnedOnWithTheMethodOnlyAtTheHeadAsRead() {
        assertEquals(
                List.of(
                        "api",
                        "graphql",
                        "-f",
                        "query=" + AutoMergeMutation.ENABLE,
                        "-f",
                        "pullRequest=PR_1",
                        "-f",
                        "method=SQUASH",
                        "-f",
                        "head=abc123"),
                args(AutoMergeMutation.enable("PR_1", MergeMethod.SQUASH, Option.of(new GhCommit("abc123")))));
    }

    @Test
    void withoutAHeadAutoMergeIsTurnedOnAtWhateverItIs() {
        assertEquals(
                List.of(
                        "api",
                        "graphql",
                        "-f",
                        "query=" + AutoMergeMutation.ENABLE,
                        "-f",
                        "pullRequest=PR_1",
                        "-f",
                        "method=REBASE"),
                args(AutoMergeMutation.enable("PR_1", MergeMethod.REBASE, Option.none())));
    }

    @Test
    void autoMergeIsTurnedOff() {
        assertEquals(
                List.of("api", "graphql", "-f", "query=" + AutoMergeMutation.DISABLE, "-f", "pullRequest=PR_1"),
                args(AutoMergeMutation.disable("PR_1")));
    }

    private static List<String> args(net.vanfleteren.jazygit.core.gh.process.GhCommand command) {
        return List.ofAll(command.args());
    }
}

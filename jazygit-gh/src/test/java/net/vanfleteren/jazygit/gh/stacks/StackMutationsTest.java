package net.vanfleteren.jazygit.gh.stacks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.process.GhCommand;
import org.junit.jupiter.api.Test;

/**
 * The commands that make a stack on GitHub, add to its top and take it apart, through the Stacks REST API (ADR 73).
 */
class StackMutationsTest {

    private static List<String> args(GhCommand command) {
        return command.executed().commandLine();
    }

    @Test
    void aStackIsMadeOfItsPullRequestsFromTheBottomAsNumbers() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "--method",
                        "POST",
                        "repos/{owner}/{repo}/stacks",
                        "-F",
                        "pull_requests[]=96",
                        "-F",
                        "pull_requests[]=97"),
                args(StackMutations.create(List.of(new PrNumber(96), new PrNumber(97)))));
    }

    @Test
    void onlyThePullRequestsPutOnTopAreGivenToAdd() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "--method",
                        "POST",
                        "repos/{owner}/{repo}/stacks/12/add",
                        "-F",
                        "pull_requests[]=98"),
                args(StackMutations.add(12, List.of(new PrNumber(98)))));
    }

    @Test
    void unstackingNamesOnlyTheStack() {
        assertEquals(
                List.of("gh", "api", "--method", "POST", "repos/{owner}/{repo}/stacks/12/unstack"),
                args(StackMutations.unstack(12)));
    }
}

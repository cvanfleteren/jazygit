package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.collection.List;

/**
 * Making a stack of pull requests on GitHub, adding to its top and taking it apart, through the Stacks REST API, as
 * {@code gh-stack} does (ADR 73). gh fills in the repository of the working tree for {owner} and {repo}.
 */
final class StackMutations {

    private StackMutations() {}

    /**
     * The command that makes a stack of {@code pullRequests}, the bottom one first: two or more, each with the head of
     * the one below it as its base.
     */
    static GhCommand create(List<Integer> pullRequests) {
        return post("repos/{owner}/{repo}/stacks", pullRequests);
    }

    /**
     * The command that puts {@code pullRequests} on the top of stack {@code stack}, the lowest one first.
     */
    static GhCommand add(int stack, List<Integer> pullRequests) {
        return post("repos/{owner}/{repo}/stacks/" + stack + "/add", pullRequests);
    }

    /**
     * The command that takes stack {@code stack} apart: its pull requests stay open, with their bases.
     */
    static GhCommand unstack(int stack) {
        return gh("api", "--method", "POST", "repos/{owner}/{repo}/stacks/" + stack + "/unstack");
    }

    // The numbers go as a JSON array of numbers: -F, unlike -f, sends them typed.
    private static GhCommand post(String path, List<Integer> pullRequests) {
        return gh(List.of("api", "--method", "POST", path)
                .appendAll(pullRequests.flatMap(number -> List.of("-F", "pull_requests[]=" + number)))
                .toJavaArray(String[]::new));
    }
}

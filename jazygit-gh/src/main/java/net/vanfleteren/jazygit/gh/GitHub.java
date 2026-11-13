package net.vanfleteren.jazygit.gh;

import lombok.With;

/**
 * Everything the TUI does with GitHub, through the {@code gh} command line tool, split into roles (ADR 114), as
 * {@code Git} is (ADR 69). Each operation that changes something on GitHub returns the command lines it ran, or why
 * it failed together with the command lines run up to then.
 *
 * <p>{@link CliGitHub} gives one for the repository in a working tree.
 *
 * @param pullRequests the pull requests: listing and reading them, and creating, checking out, merging, closing and
 *                     editing them
 * @param reviews      the conversation on a pull request and its review threads: reading them, commenting, reviewing,
 *                     and changing what was said
 * @param checks       how the CI checks are doing on the branches, how long they take, and which branches are
 *                     protected
 * @param stacks       making stacks of pull requests on GitHub and taking them apart (ADR 73)
 * @param account      the account gh runs as, and what it opens in the browser set for that account (ADR 85, 88)
 */
public record GitHub(
        @With PullRequests pullRequests,
        @With Reviews reviews,
        @With Checks checks,
        @With Stacks stacks,
        @With Account account) {}

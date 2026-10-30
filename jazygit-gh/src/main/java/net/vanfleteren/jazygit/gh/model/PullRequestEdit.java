package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;

/**
 * What an edit changes of a pull request: each of its title, its description and its base, only when it is given.
 *
 * @param title its new title
 * @param body  its new description, which may be empty
 * @param base  the branch it merges into from now on
 */
public record PullRequestEdit(Option<String> title, Option<String> body, Option<GhBranch> base) {

    /**
     * Whether it changes anything.
     */
    public boolean changes() {
        return title.isDefined() || body.isDefined() || base.isDefined();
    }
}

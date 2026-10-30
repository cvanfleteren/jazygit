package net.vanfleteren.jazygit.feature.github.edit;

import lombok.With;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * What the popup to edit a pull request holds of it.
 *
 * @param title its title
 * @param body  its description, which may be empty
 * @param base  the branch on the remote it merges into
 * @param draft whether it is a draft
 */
@With
public record PullRequestFields(String title, String body, RemoteBranch base, boolean draft) {

    /**
     * Whether {@code other} merges into the same branch: GitHub only knows the branches by their name.
     */
    boolean sameBase(PullRequestFields other) {
        return base.branch().equals(other.base().branch());
    }
}

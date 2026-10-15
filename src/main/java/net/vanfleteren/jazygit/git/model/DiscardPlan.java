package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;

/**
 * What to do to throw changes away, by how each path has to be treated.
 *
 * @param untracked paths git does not know, which are deleted
 * @param fromIndex tracked paths whose working tree is reset to the index, keeping what is staged
 * @param fromHead  tracked paths whose index and working tree are both reset to HEAD
 * @param added     paths added to the index but not in HEAD, which are removed from both
 */
public record DiscardPlan(List<String> untracked, List<String> fromIndex, List<String> fromHead, List<String> added) {

    public boolean isEmpty() {
        return untracked.isEmpty() && fromIndex.isEmpty() && fromHead.isEmpty() && added.isEmpty();
    }
}

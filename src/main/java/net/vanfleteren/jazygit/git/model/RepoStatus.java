package net.vanfleteren.jazygit.git.model;

import java.util.List;

/**
 * The state of the working tree and HEAD, as reported by {@code git status}.
 *
 * @param head    the checked-out branch name, or {@value #DETACHED}
 * @param headOid the commit HEAD points to, or {@value #INITIAL} in a repository without commits
 * @param files   the changed and untracked files, sorted by path
 */
public record RepoStatus(String head, String headOid, List<FileEntry> files) {

    public static final String DETACHED = "(detached)";
    public static final String INITIAL = "(initial)";

    public RepoStatus {
        files = List.copyOf(files);
    }

    /**
     * The checked-out branch, or a description of the detached HEAD.
     */
    public String branchLabel() {
        if (!DETACHED.equals(head)) {
            return head;
        }
        return "HEAD detached at " + headOid.substring(0, Math.min(7, headOid.length()));
    }
}

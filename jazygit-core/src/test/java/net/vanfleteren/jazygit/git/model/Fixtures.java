package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;

/**
 * Model values for tests, with what a test does not care about left at a default: a branch that is no one's default,
 * follows nothing, is on no remote and is checked out in no other worktree; a commit without parents or tags; a file
 * that is wholly staged when it is added, and wholly unstaged otherwise.
 */
public final class Fixtures {

    private Fixtures() {}

    public static Branch branch(BranchName name, boolean current, Sha tip) {
        return branch(name, current, tip, Instant.EPOCH, false, false);
    }

    public static Branch branch(BranchName name, boolean current, Sha tip, Instant tipTime, boolean hasRemote) {
        return branch(name, current, tip, tipTime, hasRemote, false);
    }

    public static Branch branch(
            BranchName name, boolean current, Sha tip, Instant tipTime, boolean hasRemote, boolean isDefault) {
        return branch(name, current, tip, tipTime, hasRemote, isDefault, new Tracking.None());
    }

    /**
     * A branch following {@code upstream}, or nothing.
     */
    public static Branch branch(
            BranchName name,
            boolean current,
            Sha tip,
            Instant tipTime,
            boolean hasRemote,
            boolean isDefault,
            Option<Upstream> upstream) {
        return branch(
                name,
                current,
                tip,
                tipTime,
                hasRemote,
                isDefault,
                upstream.<Tracking>map(Tracking.Tracks::new).getOrElse(new Tracking.None()));
    }

    public static Branch branch(
            BranchName name,
            boolean current,
            Sha tip,
            Instant tipTime,
            boolean hasRemote,
            boolean isDefault,
            Tracking tracking) {
        return new Branch(name, current, tip, tipTime, hasRemote, isDefault, tracking, Option.none());
    }

    public static Commit commit(
            Sha sha, String authorName, String authorEmail, Instant authorTime, String message, String body) {
        return commit(sha, authorName, authorEmail, authorTime, message, body, List.of());
    }

    public static Commit commit(
            Sha sha,
            String authorName,
            String authorEmail,
            Instant authorTime,
            String message,
            String body,
            List<Sha> parents) {
        return new Commit(sha, authorName, authorEmail, authorTime, message, body, parents, List.of());
    }

    public static FileEntry fileEntry(String path, ChangeType type) {
        return new FileEntry(path, type, type == ChangeType.ADDED, type != ChangeType.ADDED);
    }
}

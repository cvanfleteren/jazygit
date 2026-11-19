package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.Random;

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
        return new Branch(name, current, tip, tipTime, Option.none(), hasRemote, isDefault, tracking, Option.none());
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

    /**
     * A log of up to 60 commits, newest first, where about one in four is a merge.
     *
     * @param shuffled whether some commits come before one of their children
     */
    public static List<Commit> randomLog(Random random, boolean shuffled) {
        int size = 1 + random.nextInt(60);
        List<Commit> log = List.range(0, size).map(at -> {
            int parents = at == size - 1 ? 0 : random.nextInt(4) == 0 ? 2 + random.nextInt(2) : 1;
            List<String> shas = List.range(0, parents)
                    .map(parent -> random.nextInt(10) == 0
                            ? "gone" + at + "-" + parent
                            : "c" + (at + 1 + random.nextInt(Math.min(size - at - 1, 1 + random.nextInt(12)))));
            return commit(new Sha("c" + at), "Ada", "ada@example.com", Instant.EPOCH, "c" + at, "", shas.map(Sha::new));
        });
        return shuffled
                ? List.range(0, size / 4).foldLeft(log, (swapped, swap) -> {
                    int at = random.nextInt(size);
                    int other = random.nextInt(size);
                    return swapped.update(at, swapped.get(other)).update(other, swapped.get(at));
                })
                : log;
    }
}

package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.stream.Stream;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import net.vanfleteren.jazygit.git.model.MetadataStamp.Entry;
import net.vanfleteren.jazygit.git.model.MetadataStamp.State;

/**
 * Stamps the files of the git directory that the branches, the stashes and the worktrees are read from, by looking
 * at them rather than running git.
 *
 * <p>Git changes a ref by writing it anew and renaming it into place, so the directory it is in changes too: of the
 * loose refs, only the directories are looked at, which are much fewer.
 *
 * <p>A file system keeps when a file was modified only so precisely: some to the second or two. Two changes within
 * that time can leave a directory looking the same, so a stamp with a file modified that recently tells nothing, as
 * git's own index does not trust such a file either.
 */
final class CliMetadata {

    /**
     * How long ago a file must have been modified for its stamp to tell a later change apart: the coarsest time a
     * file system keeps, as FAT or many network mounts do.
     */
    static final Duration SETTLED = Duration.ofSeconds(2);

    private CliMetadata() {}

    /**
     * Where the git directories of the working tree at {@code worktree} are; none when they cannot be found.
     */
    static Option<Locations> locate(Path worktree) {
        return HeadFile.gitDir(worktree)
                .flatMap(gitDir -> RepositoryIdentity.read(worktree)
                        .map(identity -> new Locations(gitDir.toAbsolutePath(), identity.commonDir())));
    }

    /**
     * The stamps of the files at {@code at}, looked at {@code now}.
     */
    static GitMetadata read(Locations at, Instant now) {
        Path common = at.commonDir();
        List<Path> worktrees = children(common.resolve("worktrees"));
        // A reftable repository keeps the HEAD of each worktree in its tables, not in its HEAD file, which git leaves
        // as it was: the main worktree's with the branches, a linked one's in tables of its own.
        List<Path> heads = List.of(at.gitDir().resolve("HEAD"), common.resolve("HEAD"))
                .appendAll(worktrees.flatMap(
                        worktree -> List.of(worktree.resolve("HEAD"), worktree.resolve("reftable/tables.list"))));
        // The upstreams and the parents are in the config, and the checked out branches in the HEAD of each worktree.
        MetadataStamp branches = stamp(heads.appendAll(List.of(
                        common.resolve("config"),
                        common.resolve("packed-refs"),
                        common.resolve("reftable"),
                        common.resolve("reftable/tables.list")))
                .appendAll(directories(common.resolve("refs/heads")))
                .appendAll(directories(common.resolve("refs/remotes"))));
        // A stash is read from the reflog of refs/stash.
        MetadataStamp stashes = stamp(List.of(
                common.resolve("refs/stash"),
                common.resolve("logs/refs/stash"),
                common.resolve("packed-refs"),
                common.resolve("reftable/tables.list")));
        // What a worktree has checked out is in its HEAD; a commit on its branch moves the branch, not the HEAD, and
        // the commit it moved to is not shown with the worktree (ADR 57). The main worktree of a reftable repository
        // has its HEAD in the tables of the branches, so there a commit lists the worktrees again.
        MetadataStamp worktreeList = stamp(heads.append(common.resolve("reftable/tables.list"))
                .append(common.resolve("worktrees"))
                .appendAll(worktrees.flatMap(
                        worktree -> List.of(worktree.resolve("gitdir"), worktree.resolve("locked")))));
        return new GitMetadata(settled(branches, now), settled(stashes, now), settled(worktreeList, now));
    }

    /**
     * {@code stamp}, unless one of its files was modified too shortly before {@code now} to tell a later change apart.
     */
    private static Option<MetadataStamp> settled(MetadataStamp stamp, Instant now) {
        Instant recent = now.minus(SETTLED);
        return Option.when(
                stamp.entries()
                        .forAll(entry -> entry.state()
                                .forAll(state -> state.modified().toInstant().isBefore(recent))),
                stamp);
    }

    private static MetadataStamp stamp(List<Path> paths) {
        return new MetadataStamp(paths.map(path -> new Entry(path, state(path))));
    }

    private static Option<State> state(Path path) {
        return Try.of(() -> Files.readAttributes(path, BasicFileAttributes.class))
                .toOption()
                .map(attributes ->
                        new State(attributes.lastModifiedTime(), attributes.size(), Option.of(attributes.fileKey())));
    }

    /**
     * What is right inside the directory {@code dir}; nothing when it is not there.
     */
    private static List<Path> children(Path dir) {
        return Try.of(() -> {
                    try (Stream<Path> listed = Files.list(dir)) {
                        return List.ofAll(listed.sorted());
                    }
                })
                .getOrElse(List.empty());
    }

    /**
     * The directory {@code root} and all the directories under it, in the order they are walked; nothing when it is
     * not there.
     */
    private static List<Path> directories(Path root) {
        ArrayList<Path> found = new ArrayList<>();
        Try.run(() -> Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                found.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        }));
        return List.ofAll(found).sorted();
    }

    /**
     * Where the git directories of a working tree are.
     *
     * @param gitDir    the git directory of the working tree itself, which holds its HEAD
     * @param commonDir the git directory all the worktrees share, which holds the refs; the same as {@code gitDir}
     *                  for the main worktree
     */
    record Locations(Path gitDir, Path commonDir) {}
}

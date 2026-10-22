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
 */
final class CliMetadata {

    private CliMetadata() {}

    /**
     * Where the git directories of the working tree at {@code workTree} are; none when they cannot be found.
     */
    static Option<Locations> locate(Path workTree) {
        return HeadFile.gitDir(workTree)
                .flatMap(gitDir -> RepositoryIdentity.read(workTree)
                        .map(identity -> new Locations(gitDir.toAbsolutePath(), identity.commonDir())));
    }

    static GitMetadata read(Locations at) {
        Path common = at.commonDir();
        List<Path> worktrees = children(common.resolve("worktrees"));
        // The upstreams are in the config, and the checked out branches in the HEAD of each worktree.
        MetadataStamp branches = stamp(List.of(
                        at.gitDir().resolve("HEAD"),
                        common.resolve("HEAD"),
                        common.resolve("config"),
                        common.resolve("packed-refs"),
                        common.resolve("reftable"),
                        common.resolve("reftable/tables.list"))
                .appendAll(worktrees.map(worktree -> worktree.resolve("HEAD")))
                .appendAll(directories(common.resolve("refs/heads")))
                .appendAll(directories(common.resolve("refs/remotes"))));
        // A stash is read from the reflog of refs/stash.
        MetadataStamp stashes = stamp(List.of(
                common.resolve("refs/stash"),
                common.resolve("logs/refs/stash"),
                common.resolve("packed-refs"),
                common.resolve("reftable/tables.list")));
        // What a worktree has checked out moves with its branch.
        MetadataStamp worktreeList = new MetadataStamp(branches.entries()
                .appendAll(stamp(List.of(common.resolve("worktrees"))
                                .appendAll(worktrees.flatMap(
                                        worktree -> List.of(worktree.resolve("gitdir"), worktree.resolve("locked")))))
                        .entries()));
        return new GitMetadata(branches, stashes, worktreeList);
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

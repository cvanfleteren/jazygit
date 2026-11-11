package net.vanfleteren.jazygit.git;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;

/**
 * The branches that the other worktrees of a repository rebase or bisect, with their HEAD detached meanwhile, so that
 * {@code %(worktreepath)} does not name them (ADR 110). Git still takes such a branch as in use there, and refuses to
 * check it out in another worktree; it reads that from the files it keeps in the git directory of each worktree, as
 * this does, without running git.
 */
final class BranchesInUse {

    // The directory under the common git directory with the git directory of each linked worktree.
    private static final String WORKTREES = "worktrees";
    // In the git directory of a linked worktree: the path of the .git file in its root.
    private static final String GITDIR = "gitdir";
    // In the git directory of a linked worktree: the path of the common git directory.
    private static final String COMMON_DIR = "commondir";
    // The name of the git directory in the root of the main worktree.
    private static final String DOT_GIT = ".git";
    // The ref of the branch a rebase works on, by the backend that runs it.
    private static final List<String> REBASE_HEAD_NAMES = List.of("rebase-merge/head-name", "rebase-apply/head-name");
    // The branch a bisect started on, by its name, or the commit when it started on a detached HEAD.
    private static final String BISECT_START = "BISECT_START";

    private BranchesInUse() {}

    /**
     * The branches the worktrees of the repository other than the one at {@code worktree} rebase or bisect, each with
     * the root of the worktree that does. Nothing that cannot be read is in it.
     */
    static Map<BranchName, Path> elsewhere(Path worktree) {
        return HeadFile.gitDir(worktree)
                .map(own -> own.toAbsolutePath().normalize())
                .map(own -> worktrees(common(own))
                        .filter(other -> !other._1.equals(own))
                        .flatMap(other -> busyWith(other._1).map(branch -> Tuple.of(branch, other._2))))
                .map(HashMap::ofEntries)
                .getOrElse(HashMap.empty());
    }

    /**
     * The common git directory of the repository whose worktree has its git directory at {@code gitDir}: that one,
     * unless it names another in {@code commondir}, as a linked worktree's does.
     */
    private static Path common(Path gitDir) {
        return HeadFile.firstLine(gitDir.resolve(COMMON_DIR))
                .map(line -> gitDir.resolve(line.strip()).normalize())
                .getOrElse(gitDir);
    }

    /**
     * The git directory and the root of each worktree of the repository whose common git directory is {@code common}:
     * the main one, unless the repository is bare, and those in {@code worktrees}.
     */
    private static List<Tuple2<Path, Path>> worktrees(Path common) {
        Option<Tuple2<Path, Path>> main = Option.of(common.getFileName())
                .filter(name -> name.toString().equals(DOT_GIT))
                .flatMap(name -> Option.of(common.getParent()))
                .map(root -> Tuple.of(common, root));
        return main.toList().appendAll(linked(common.resolve(WORKTREES)));
    }

    /**
     * The git directory and the root of each linked worktree whose git directory is in {@code worktrees}: the root is
     * where the {@code .git} file is that {@code gitdir} names.
     */
    private static List<Tuple2<Path, Path>> linked(Path worktrees) {
        return Try.withResources(() -> Files.list(worktrees))
                .of(Stream::toList)
                .map(List::ofAll)
                .getOrElse(List.empty())
                .filter(Files::isDirectory)
                .flatMap(gitDir -> HeadFile.firstLine(gitDir.resolve(GITDIR))
                        .flatMap(line -> Option.of(
                                gitDir.resolve(line.strip()).normalize().getParent()))
                        .map(root -> Tuple.of(gitDir.toAbsolutePath().normalize(), root)));
    }

    /**
     * The branch the worktree with its git directory at {@code gitDir} rebases, or else bisects, if any.
     */
    static Option<BranchName> busyWith(Path gitDir) {
        return REBASE_HEAD_NAMES
                .flatMap(file -> HeadFile.firstLine(gitDir.resolve(file)))
                .map(String::strip)
                .find(ref -> ref.startsWith(BranchRef.HEADS) && ref.length() > BranchRef.HEADS.length())
                .map(ref -> new BranchName(ref.substring(BranchRef.HEADS.length())))
                .orElse(() -> HeadFile.firstLine(gitDir.resolve(BISECT_START))
                        .map(String::strip)
                        .filter(name -> !name.isEmpty())
                        .map(BranchName::new));
    }
}

package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * What HEAD points to, read from the files of a repository rather than asked of git: cheap enough to do for
 * every recent repository at once. The tip of a branch is not read.
 */
public final class HeadFile {

    private static final String GIT_DIR = ".git";
    private static final String GIT_DIR_LINK = "gitdir:";
    private static final String REF = "ref:";

    private HeadFile() {}

    /**
     * What HEAD of the repository at {@code workTree} points to; none when it cannot be read, or points to
     * something other than a local branch or a commit. The git directory of a linked worktree or a submodule is
     * where its {@code .git} file says.
     */
    public static Option<Head> read(Path workTree) {
        return gitDir(workTree)
                .flatMap(gitDir -> firstLine(gitDir.resolve("HEAD")))
                .flatMap(HeadFile::parse);
    }

    /**
     * The git directory of the working tree at {@code workTree}: its {@code .git} directory, or where its
     * {@code .git} file says, as for a linked worktree or a submodule.
     */
    static Option<Path> gitDir(Path workTree) {
        Path dotGit = workTree.resolve(GIT_DIR);
        return Files.isDirectory(dotGit)
                ? Option.of(dotGit)
                : firstLine(dotGit)
                        .filter(line -> line.startsWith(GIT_DIR_LINK))
                        .map(line -> workTree.resolve(
                                line.substring(GIT_DIR_LINK.length()).strip()));
    }

    static Option<String> firstLine(Path file) {
        return Try.of(() -> List.ofAll(Files.readAllLines(file))).toOption().flatMap(List::headOption);
    }

    /**
     * What the first line of a HEAD file says.
     */
    static Option<Head> parse(String line) {
        if (line.startsWith(REF)) {
            String ref = line.substring(REF.length()).strip();
            return Option.when(
                    ref.startsWith(Refs.HEADS),
                    () -> new Head.OnBranch(new Branch.Name(ref.substring(Refs.HEADS.length())), Option.none()));
        }
        return Sha.parse(line).map(Head.Detached::new);
    }
}

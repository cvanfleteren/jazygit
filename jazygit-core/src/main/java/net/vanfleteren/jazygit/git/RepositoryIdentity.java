package net.vanfleteren.jazygit.git;

import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;

/**
 * Which repository a working tree belongs to, read from its files as git finds its git directory: cheap enough to
 * do for every recent repository at once.
 *
 * @param commonDir      the git directory all the worktrees of the repository share
 * @param name           the name of the repository: the directory of the main worktree, or for a linked one, the
 *                       directory holding the common git directory, as lazygit names it ({@code proj} for
 *                       {@code proj/.bare}); a bare {@code app.git} is {@code app}
 * @param linkedWorktree the name of the directory of the working tree, when it is a linked worktree
 */
public record RepositoryIdentity(Path commonDir, String name, Option<String> linkedWorktree) {

    // The file in the git directory of a linked worktree that says where the common git directory is.
    private static final String COMMON_DIR = "commondir";
    private static final String GIT_SUFFIX = ".git";

    /**
     * The repository the working tree at {@code workTree}, its root, belongs to; none when its git directory
     * cannot be found.
     */
    public static Option<RepositoryIdentity> read(Path workTree) {
        Path root = real(workTree);
        return HeadFile.gitDir(root)
                .map(RepositoryIdentity::real)
                .map(gitDir -> HeadFile.firstLine(gitDir.resolve(COMMON_DIR))
                        .map(common -> real(gitDir.resolve(common.strip())))
                        .map(common -> new RepositoryIdentity(common, nameOf(common), Option.of(name(root))))
                        .getOrElse(() -> new RepositoryIdentity(gitDir, name(root), Option.none())));
    }

    /**
     * How the repository is named where it is shown: its name, then the worktree in parentheses when it is a
     * linked one, as lazygit does: {@code jazygit(jazygit-feature)}.
     */
    public String label() {
        return name + linkedWorktree.map(worktree -> "(" + worktree + ")").getOrElse("");
    }

    /**
     * How the repository of the working tree at {@code workTree} is named where it is shown, as {@link #label()}
     * names it; the name of its directory when its git directory cannot be found. The one name the header and the
     * recent repositories show.
     */
    public static String labelOf(Path workTree) {
        return read(workTree).map(RepositoryIdentity::label).getOrElse(() -> name(workTree));
    }

    /**
     * The name of the repository whose common git directory is {@code common}.
     */
    private static String nameOf(Path common) {
        String own = name(common);
        return own.endsWith(GIT_SUFFIX) && !own.equals(GIT_SUFFIX)
                ? own.substring(0, own.length() - GIT_SUFFIX.length())
                : Option.of(common.getParent()).map(RepositoryIdentity::name).getOrElse(own);
    }

    private static String name(Path path) {
        // The root directory has no name.
        return Option.of(path.getFileName()).map(Path::toString).getOrElse(path::toString);
    }

    private static Path real(Path path) {
        Path normal = path.toAbsolutePath().normalize();
        return Try.of(normal::toRealPath).getOrElse(normal);
    }
}

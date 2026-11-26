package net.vanfleteren.jazygit.core.git.model;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * A changed file, known by its path in the repository, as a {@link FileTree} arranges it.
 */
public interface Pathed {

    /**
     * The repository-relative path of the file.
     */
    RepoPath path();

    /**
     * The path the file was renamed from, if it was.
     */
    Option<RepoPath> origin();
}

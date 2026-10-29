package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * A changed file, known by its path in the repository, as a {@link FileTree} arranges it.
 */
public interface Pathed {

    /**
     * The repository-relative path of the file.
     */
    String path();

    /**
     * The path the file was renamed from, if it was.
     */
    Option<String> origin();
}

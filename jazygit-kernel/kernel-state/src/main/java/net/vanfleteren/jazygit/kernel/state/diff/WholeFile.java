package net.vanfleteren.jazygit.kernel.state.diff;

import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * How a file is read whole while an area shows {@code content}: the diff of the file with the path {@code path},
 * read again the way {@code source} says, with {@code options}. What shows a file read whole keeps this, not the read
 * itself, which knows where in the model it puts what it read.
 */
public record WholeFile(ScrolledContent content, Source source, RepoPath path, DiffOptions options) {

    /**
     * This with {@code chosen} options.
     */
    public WholeFile withOptions(DiffOptions chosen) {
        return new WholeFile(content, source, path, chosen);
    }

    /**
     * How a file is read whole: as the diff it is shown in was read.
     */
    public sealed interface Source {

        /**
         * The staged changes of {@code file} in the working tree, or the unstaged ones.
         */
        record WorkingTree(FileEntry file, boolean staged) implements Source {}

        /**
         * What the commit {@code sha} changed in {@code file}.
         */
        record OfCommit(Sha sha, CommitFile file) implements Source {}

        /**
         * What the stash {@code sha} stashed of {@code file}.
         */
        record OfStash(Sha sha, CommitFile file) implements Source {}

        /**
         * What {@code of}, whose files the files pane lists, changed in {@code file}.
         */
        record OfFiles(Changes of, CommitFile file) implements Source {}

        /**
         * The file at {@code path} in the working tree, without changes, as it is on disk (ADR 108).
         */
        record OnDisk(RepoPath path) implements Source {}

        /**
         * The file at {@code path} in the commit {@code sha}, which it did not change, as it is in it (ADR 128).
         */
        record AtCommit(Sha sha, RepoPath path) implements Source {}
    }
}

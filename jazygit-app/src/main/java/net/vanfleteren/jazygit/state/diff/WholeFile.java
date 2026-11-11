package net.vanfleteren.jazygit.state.diff;

import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * How a file is read whole while an area shows {@code content}: the diff of the file with the path {@code path},
 * read again the way {@code source} says, with {@code options}. What shows a file read whole keeps this, not the read
 * itself, which knows where in the model it puts what it read.
 */
public record WholeFile(ScrolledContent content, Source source, String path, DiffOptions options) {

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
        record OnDisk(String path) implements Source {}
    }
}

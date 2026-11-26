package net.vanfleteren.jazygit.features.changes.files;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What keeps files of the working tree from being staged or discarded.
 */
public sealed interface FilesError extends AppError {

    /**
     * The file or directory at {@code path}, listed with all files of the working tree, was asked to be staged or
     * discarded, but nothing in it has changed (ADR 108).
     */
    record Unchanged(RepoPath path) implements FilesError {

        /**
         * That {@code files} have no changes: the {@code directory} they were asked for as, else the one file, else
         * the deepest directory that holds them all.
         */
        public static Unchanged of(List<FileEntry> files, Option<RepoPath> directory) {
            return new Unchanged(directory.getOrElse(() -> files.size() == 1
                    ? files.head().path()
                    : files.map(FileEntry::path).reduceOption(Unchanged::shared).getOrElse(new RepoPath(""))));
        }

        /**
         * The deepest directory that holds both {@code a} and {@code b}, or the root.
         */
        private static RepoPath shared(RepoPath a, RepoPath b) {
            List<String> left = List.of(a.value().split("/")).init();
            List<String> right = List.of(b.value().split("/")).init();
            return new RepoPath(left.zip(right)
                    .takeWhile(pair -> pair._1.equals(pair._2))
                    .map(pair -> pair._1)
                    .mkString("/"));
        }

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.error.unchanged", path.value().isEmpty() ? "/" : path.value());
        }
    }
}

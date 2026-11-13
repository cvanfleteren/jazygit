package net.vanfleteren.jazygit.feature.files;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What keeps files of the working tree from being staged or discarded.
 */
public sealed interface FilesError extends AppError {

    /**
     * The file or directory at {@code path}, listed with all files of the working tree, was asked to be staged or
     * discarded, but nothing in it has changed (ADR 108).
     */
    record Unchanged(String path) implements FilesError {

        /**
         * That {@code files} have no changes: the {@code directory} they were asked for as, else the one file, else
         * the deepest directory that holds them all.
         */
        public static Unchanged of(List<FileEntry> files, Option<String> directory) {
            return new Unchanged(directory.getOrElse(() -> files.size() == 1
                    ? files.head().path()
                    : files.map(FileEntry::path).reduceOption(Unchanged::shared).getOrElse("")));
        }

        /**
         * The deepest directory that holds both {@code a} and {@code b}, or the root.
         */
        private static String shared(String a, String b) {
            List<String> left = List.of(a.split("/")).init();
            List<String> right = List.of(b.split("/")).init();
            return left.zip(right)
                    .takeWhile(pair -> pair._1.equals(pair._2))
                    .map(pair -> pair._1)
                    .mkString("/");
        }

        @Override
        public Phrase phrase() {
            return Phrase.of("error.unchanged", path.isEmpty() ? "/" : path);
        }
    }
}

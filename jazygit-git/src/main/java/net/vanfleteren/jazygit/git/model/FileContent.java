package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * What a file of the working tree holds on disk, as a file without changes shows it (ADR 108).
 */
public sealed interface FileContent {

    /**
     * Text: the file as a diff of it against itself, every line a line of context, so that it is drawn, lexed and
     * walked with the cursor as a diff is. It is cut at the limit of a diff (ADR 79), and then says so.
     */
    record Text(DiffText asDiff) implements FileContent {}

    /**
     * A file with a NUL byte in its first 8000 bytes, as git tells binary files, of {@code size} bytes.
     */
    record Binary(long size) implements FileContent {}

    /**
     * A symbolic link to {@code target}.
     */
    record Link(String target) implements FileContent {}

    /**
     * A submodule, at the commit the index has for it, when that can be told.
     */
    record Submodule(Option<Sha> commit) implements FileContent {}
}

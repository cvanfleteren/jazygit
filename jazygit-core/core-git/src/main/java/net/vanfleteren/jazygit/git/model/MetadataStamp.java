package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * What some files and directories of the git directory looked like at one moment: when two stamps are equal,
 * none of them changed in between, so what git reads from them did not either. It is a lot cheaper to take than
 * running git.
 *
 * @param entries each file or directory looked at, in the order they were
 */
public record MetadataStamp(List<Entry> entries) {

    /**
     * @param path  the file or directory
     * @param state what it looked like; none when it was not there
     */
    public record Entry(Path path, Option<State> state) {}

    /**
     * Git writes a file anew and renames it into place, which gives it another key, and adds an entry to the
     * directory it is in, which changes when that was modified; the key also tells a change apart within the
     * time a file system keeps.
     *
     * @param modified when it was last modified
     * @param size     how many bytes it holds
     * @param key      what the file system knows it by, as an inode, if it tells
     */
    public record State(FileTime modified, long size, Option<Object> key) {}
}

package net.vanfleteren.jazygit.state.view;

import net.vanfleteren.jazygit.git.model.FileContent;

/**
 * A node of the files pane, listing all files of the working tree, with no changes under it (ADR 108): the content
 * panel shows what a file holds, or how many files a directory does, in place of a diff.
 */
public sealed interface UnchangedNode {

    /**
     * The file whose path is the key of {@code content}, and what it holds on disk, once read.
     */
    record File(Preview<String, FileContent> content) implements UnchangedNode {}

    /**
     * The directory at {@code path}, the root when it is empty, which holds {@code files} files shown.
     */
    record Directory(String path, int files) implements UnchangedNode {}
}

package net.vanfleteren.jazygit.state.compose;

import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * The file text is written in, in the editor, and what writing it there is logged as, or fails as.
 *
 * @param name      the name of the file; editors take a {@code .md} one for Markdown, as GitHub does
 * @param operation what the editor ran is logged as
 */
public record EditorFile(String name, Operation operation) {}

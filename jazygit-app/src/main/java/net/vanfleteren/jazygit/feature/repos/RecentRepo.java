package net.vanfleteren.jazygit.feature.repos;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Head;

/**
 * A recent repository that can be switched to.
 *
 * @param directory the root of its working tree
 * @param label     how it is named, as the header names it: with the worktree in parentheses when that is a linked
 *                  one
 * @param head      what its HEAD points to; none when that could not be read
 */
public record RecentRepo(String directory, String label, Option<Head> head) {}

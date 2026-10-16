package net.vanfleteren.jazygit.feature.repos;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;

/**
 * A recent repository that can be switched to.
 *
 * @param directory the root of its working tree
 * @param head      what its HEAD points to; none when that could not be read
 */
public record RecentRepo(String directory, Option<Head> head) {}

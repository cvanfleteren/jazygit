package net.vanfleteren.jazygit.features.github.builds;

import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * A file at a commit: the file of a workflow at the commit of a run, which says which job needs which (ADR 131).
 *
 * @param sha  the commit
 * @param path its path
 */
public record FileAt(Sha sha, String path) {}

package net.vanfleteren.jazygit.gh.model;

/**
 * What a pull request says and changes, to show it.
 *
 * @param base      the branch it merges into
 * @param body      its description, as written
 * @param diff      what it changes, as a unified diff, or only its first lines when it is truncated
 * @param truncated whether lines of the diff were left out
 */
public record PullRequestPreview(String base, String body, String diff, boolean truncated) {}

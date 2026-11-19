package net.vanfleteren.jazygit.core.gh.model;

/**
 * A workflow of GitHub Actions of a repository (ADR 131).
 *
 * @param id     its id on GitHub, to read its runs with
 * @param name   its name, as its runs show it
 * @param path   the path of its file
 * @param active whether it runs: not disabled
 */
public record RepoWorkflow(String id, String name, String path, boolean active) {}

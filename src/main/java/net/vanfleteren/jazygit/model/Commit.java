package net.vanfleteren.jazygit.model;

/**
 * A git commit, including a fake diff-like body used to populate the content panel.
 */
public record Commit(String shortSha, String author, String date, String message, String body) {
}

package net.vanfleteren.jazygit.gh.model;

/**
 * What a new pull request says about itself.
 *
 * @param title its title
 * @param body  its description, which may be empty
 * @param draft whether it is a draft, which cannot be merged until it is marked ready for review
 */
public record NewPullRequest(String title, String body, boolean draft) {}

package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.control.Option;

/**
 * An open pull request of a repository on Forgejo, as its list gives it (ADR 176).
 *
 * @param number         its number in the repository
 * @param title          its title
 * @param head           the branch it merges
 * @param headRepository the repository that branch is in, as {@code owner/name}; none when that was deleted
 * @param base           the branch it merges into
 * @param draft          whether it is a draft
 * @param url            where it is on the web
 */
public record ForgejoPullRequest(
        long number,
        String title,
        String head,
        Option<String> headRepository,
        String base,
        boolean draft,
        String url) {}

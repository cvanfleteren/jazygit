package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.LinkedHashMap;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Json;

/**
 * A merge of a pull request to ask of Forgejo (ADR 180), as its API asks for it. No title or message: Forgejo writes
 * its own.
 *
 * @param style        how it is merged
 * @param head         the commit its head was at as last read: Forgejo refuses the merge once the head moved on, so
 *                     that nothing is merged that was not seen
 * @param deleteBranch whether Forgejo deletes the head branch once merged; Forgejo's API does not apply the
 *                     repository's default by itself, so it is sent as read
 */
public record ForgejoMerge(ForgejoMergeStyle style, Sha head, boolean deleteBranch) {

    /**
     * What is sent to merge it. Forgejo reads {@code Do} by the name of its Go field, without a JSON tag, and matches
     * it ignoring case, as Gitea's {@code do} is: written so, both read it.
     */
    String json() {
        return new Json.JObject(LinkedHashMap.of(
                        "Do", new Json.JString(style.value()),
                        "head_commit_id", new Json.JString(head.value()),
                        "delete_branch_after_merge", new Json.JBool(deleteBranch)))
                .text();
    }
}

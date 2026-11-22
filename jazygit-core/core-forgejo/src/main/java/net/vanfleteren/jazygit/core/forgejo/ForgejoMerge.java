package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.LinkedHashMap;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Json;

/**
 * A merge of a pull request to ask of Forgejo (ADR 180), as its API asks for it. No title or message: Forgejo writes
 * its own.
 *
 * @param style             how it is merged
 * @param head              the commit its head was at as last read: Forgejo refuses the merge once the head moved on,
 *                          so that nothing is merged that was not seen; it does not look at it when the merge is
 *                          scheduled
 * @param deleteBranch      whether Forgejo deletes the head branch once merged; Forgejo's API does not apply the
 *                          repository's default by itself, so it is sent as read
 * @param whenChecksSucceed whether Forgejo merges it once the rules of its base branch are met, instead of now (ADR
 *                          181): it is scheduled then, also when they are met already
 */
public record ForgejoMerge(ForgejoMergeStyle style, Sha head, boolean deleteBranch, boolean whenChecksSucceed) {

    /**
     * A merge now.
     */
    public ForgejoMerge(ForgejoMergeStyle style, Sha head, boolean deleteBranch) {
        this(style, head, deleteBranch, false);
    }

    /**
     * What is sent to merge it. Forgejo reads {@code Do} by the name of its Go field, without a JSON tag, and matches
     * it ignoring case, as Gitea's {@code do} is: written so, both read it.
     */
    String json() {
        LinkedHashMap<String, Json> fields = LinkedHashMap.of(
                "Do", new Json.JString(style.value()),
                "head_commit_id", new Json.JString(head.value()),
                "delete_branch_after_merge", new Json.JBool(deleteBranch));
        return new Json.JObject(
                        whenChecksSucceed ? fields.put("merge_when_checks_succeed", new Json.JBool(true)) : fields)
                .text();
    }
}

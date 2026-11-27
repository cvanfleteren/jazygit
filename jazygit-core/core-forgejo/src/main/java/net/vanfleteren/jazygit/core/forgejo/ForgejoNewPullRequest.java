package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.lib.json.Json;

/**
 * A pull request to create on Forgejo (ADR 178), as its API asks for it.
 *
 * @param head  the branch it merges: in the repository it is created in, or in a fork of it
 * @param base  the branch of the repository it merges into
 * @param title its title, as typed
 * @param body  what it says, which may be empty
 * @param draft whether it is a draft, which Forgejo tells from a prefix of the title
 */
public record ForgejoNewPullRequest(Head head, ForgeBranch base, String title, String body, boolean draft) {

    /**
     * The prefix a draft's title is sent with: the first of Forgejo's {@code WORK_IN_PROGRESS_PREFIXES} by default.
     */
    static final String DRAFT_PREFIX = "WIP: ";

    // Forgejo's default prefixes, either of which a title may start with already.
    private static final List<String> DRAFT_PREFIXES = List.of("wip:", "[wip]");

    /**
     * The title as it is sent: with {@link #DRAFT_PREFIX} before it for a draft, unless it starts with one of
     * Forgejo's default prefixes already, as Forgejo compares them, ignoring case.
     */
    public String sentTitle() {
        String lower = title.toLowerCase(Locale.ROOT);
        return draft && DRAFT_PREFIXES.forAll(prefix -> !lower.startsWith(prefix)) ? DRAFT_PREFIX + title : title;
    }

    /**
     * What is sent to create it.
     */
    String json() {
        return new Json.JObject(LinkedHashMap.of(
                        "head", new Json.JString(head.wire()),
                        "base", new Json.JString(base.name()),
                        "title", new Json.JString(sentTitle()),
                        "body", new Json.JString(body)))
                .text();
    }

    /**
     * The branch a pull request merges.
     *
     * @param owner  the owner of the fork it is in; none when it is in the repository the pull request is created in
     * @param branch the branch, as the forge names it
     */
    public record Head(Option<String> owner, ForgeBranch branch) {

        /**
         * The head of a pull request into {@code home} from {@code branch} of {@code repository}: in a fork, owned by
         * the owner of {@code repository}, unless that is {@code home}, as Forgejo compares them, ignoring case.
         */
        public static Head of(ForgejoRepository home, ForgejoRepository repository, ForgeBranch branch) {
            boolean inHome = repository.owner().equalsIgnoreCase(home.owner())
                    && repository.name().equalsIgnoreCase(home.name());
            return new Head(Option.when(!inHome, repository::owner), branch);
        }

        /**
         * As Forgejo writes it: {@code branch} in the repository, {@code owner:branch} in a fork.
         */
        public String wire() {
            return owner.map(fork -> fork + ":").getOrElse("") + branch.name();
        }
    }
}

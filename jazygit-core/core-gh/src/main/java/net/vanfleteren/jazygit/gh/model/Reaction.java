package net.vanfleteren.jazygit.gh.model;

/**
 * A reaction GitHub lets anyone add to a comment, as its GraphQL {@code ReactionContent} names it (ADR 86).
 */
public enum Reaction {
    THUMBS_UP("👍"),
    THUMBS_DOWN("👎"),
    LAUGH("😄"),
    HOORAY("🎉"),
    CONFUSED("😕"),
    HEART("❤️"),
    ROCKET("🚀"),
    EYES("👀");

    private final String emoji;

    Reaction(String emoji) {
        this.emoji = emoji;
    }

    /**
     * How GitHub's site shows it.
     */
    public String emoji() {
        return emoji;
    }
}

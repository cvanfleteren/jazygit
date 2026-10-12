package net.vanfleteren.jazygit.git.model;

/**
 * The full object id of a git commit.
 */
public record Sha(String value) {

    private static final int SHORT_LENGTH = 7;

    public Sha {
        if (value.isBlank()) {
            throw new IllegalArgumentException("A sha cannot be blank");
        }
    }

    /**
     * The first 7 characters, or all of them if there are fewer.
     */
    public String shortSha() {
        return value.substring(0, Math.min(SHORT_LENGTH, value.length()));
    }

    @Override
    public String toString() {
        return value;
    }
}

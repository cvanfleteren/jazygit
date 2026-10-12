package net.vanfleteren.jazygit.git.model;

/**
 * The message of a commit.
 *
 * @param summary     the first line
 * @param description the extended message; may be blank
 */
public record CommitMessage(String summary, String description) {

    public boolean hasDescription() {
        return !description.isBlank();
    }

    /**
     * The message as git stores it: the summary, and the description after a blank line.
     */
    public String text() {
        return hasDescription() ? summary + "\n\n" + description + "\n" : summary + "\n";
    }
}

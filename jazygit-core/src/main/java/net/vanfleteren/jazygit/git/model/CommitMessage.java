package net.vanfleteren.jazygit.git.model;

/**
 * The message of a commit.
 *
 * @param summary     the first line
 * @param description the extended message; may be blank
 */
public record CommitMessage(String summary, String description) {

    /**
     * The message without the whitespace around its summary and description.
     */
    public CommitMessage stripped() {
        return new CommitMessage(summary.strip(), description.strip());
    }

    public boolean hasSummary() {
        return !summary.isBlank();
    }

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

package net.vanfleteren.jazygit.git;

/**
 * A checkout that git refused because uncommitted changes to tracked files would be overwritten. Stashing
 * the changes first lets it go through.
 */
public final class LocalChangesException extends GitCommandException {

    public LocalChangesException(String message, java.util.List<String> commands) {
        super(message, commands);
    }
}

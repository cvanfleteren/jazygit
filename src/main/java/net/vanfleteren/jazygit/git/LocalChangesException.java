package net.vanfleteren.jazygit.git;

/**
 * A checkout that git refused because uncommitted changes to tracked files would be overwritten. Stashing
 * the changes first lets it go through.
 */
public final class LocalChangesException extends IllegalStateException {

    public LocalChangesException(String message) {
        super(message);
    }
}

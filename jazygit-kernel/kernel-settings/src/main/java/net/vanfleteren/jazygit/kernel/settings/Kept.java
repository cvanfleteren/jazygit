package net.vanfleteren.jazygit.kernel.settings;

/**
 * When a setting is kept in the settings file.
 */
public enum Kept {
    /**
     * At its initial value too: the file shows it, as it does the theme.
     */
    ALWAYS,
    /**
     * Only once it differs from its initial value; set back to that, its entry goes, and so does a table it leaves
     * empty.
     */
    WHEN_CHANGED,
    /**
     * Never: only the user writes it, and saving leaves it as it is in the file, as the editors are (ADR 110).
     */
    NEVER
}

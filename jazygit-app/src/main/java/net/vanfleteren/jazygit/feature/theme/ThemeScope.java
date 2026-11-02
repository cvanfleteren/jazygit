package net.vanfleteren.jazygit.feature.theme;

/**
 * Where a theme chosen holds (ADR 70).
 */
public enum ThemeScope {
    /**
     * Every repository the git configuration gives no theme, kept in the settings.
     */
    EVERYWHERE,
    /**
     * The repository shown, kept in its own git configuration.
     */
    REPOSITORY
}

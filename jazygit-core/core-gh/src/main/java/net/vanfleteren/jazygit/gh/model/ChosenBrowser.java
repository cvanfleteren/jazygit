package net.vanfleteren.jazygit.gh.model;

/**
 * The browser gh would open a link in, and what chose it (ADR 85).
 *
 * @param command the browser as it is set: a command line the link is added to
 * @param source  where it is set
 */
public record ChosenBrowser(String command, Source source) {

    /**
     * Where a browser is set, in gh's order.
     */
    public enum Source {
        GH_BROWSER,
        GH_SETTING,
        BROWSER,
        DESKTOP
    }
}

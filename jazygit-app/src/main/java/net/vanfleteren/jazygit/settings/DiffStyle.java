package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * How the diffs of the working tree, of a commit and of a stash are drawn (ADR 44, ADR 47).
 */
public enum DiffStyle {
    /**
     * The text git prints, its lines coloured by what they are.
     */
    PLAIN("plain"),
    /**
     * A bar for each file, the numbers of the lines, and the words that changed in them highlighted (ADR 44).
     */
    RICH("rich");

    private final String id;

    DiffStyle(String id) {
        this.id = id;
    }

    /**
     * The style named {@code id}, if there is one.
     */
    public static Option<DiffStyle> of(String id) {
        return List.of(values()).find(style -> style.id.equals(id));
    }

    /**
     * How the style is named in the settings file.
     */
    public String id() {
        return id;
    }

    /**
     * The other style.
     */
    public DiffStyle toggled() {
        return this == PLAIN ? RICH : PLAIN;
    }
}

package net.vanfleteren.jazygit.feature.help;

import java.util.List;

/**
 * The left-side panels that can list their keybindings, with what each key does.
 */
public enum HelpTopic {

    FILES("panel.files.title", List.of(
            new Binding("space", "help.files.stage"),
            new Binding("c", "help.files.commit"),
            new Binding("A", "help.files.amend"),
            new Binding("d", "help.files.discard"))),
    BRANCHES("panel.branches.title", List.of(
            new Binding("space", "help.branches.checkout"),
            new Binding("P", "help.branches.push"),
            new Binding("n", "help.branches.new"),
            new Binding("d", "help.branches.delete"))),
    COMMITS("panel.commits.title", List.of(
            new Binding("r", "help.commits.reword")));

    /**
     * A key and the message key of the text describing its action.
     */
    public record Binding(String key, String descriptionKey) {
    }

    private final String titleKey;
    private final List<Binding> bindings;

    HelpTopic(String titleKey, List<Binding> bindings) {
        this.titleKey = titleKey;
        this.bindings = bindings;
    }

    /**
     * The message key of the panel's title.
     */
    public String titleKey() {
        return titleKey;
    }

    public List<Binding> bindings() {
        return bindings;
    }
}

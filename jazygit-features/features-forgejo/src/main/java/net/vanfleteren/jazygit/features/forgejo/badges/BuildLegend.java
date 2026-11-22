package net.vanfleteren.jazygit.features.forgejo.badges;

import net.vanfleteren.jazygit.kernel.ui.keys.Legend;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.CheckSymbols;

/**
 * What Forgejo's badges in the branches pane mean, which its help lists (ADR 168, 174).
 */
public final class BuildLegend {

    /**
     * How the last build of a branch on Forgejo went.
     */
    public static final Legend BRANCHES = new Legend(
            Scopes.BRANCHES.id(),
            CheckSymbols.PASSED.text() + " " + CheckSymbols.FAILED.text() + " " + CheckSymbols.RUNNING.text(),
            "forgejo.help.branches.legend.builds");

    private BuildLegend() {}
}

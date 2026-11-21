package net.vanfleteren.jazygit.features.forgejo.badges;

import net.vanfleteren.jazygit.kernel.ui.keys.Legend;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * What Forgejo's badges in the branches pane mean, which its help lists (ADR 168, 174).
 */
public final class BuildLegend {

    /**
     * How the last build of a branch on Forgejo went.
     */
    public static final Legend BRANCHES = new Legend(
            Scopes.BRANCHES.id(),
            BuildSymbols.PASSED + " " + BuildSymbols.FAILED + " " + BuildSymbols.RUNNING,
            "forgejo.help.branches.legend.builds");

    private BuildLegend() {}
}

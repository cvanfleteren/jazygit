package net.vanfleteren.jazygit.features.forgejo;

import java.time.Duration;
import net.vanfleteren.jazygit.features.forgejo.badges.BuildLegend;
import net.vanfleteren.jazygit.features.forgejo.badges.ForgejoBadge;
import net.vanfleteren.jazygit.features.forgejo.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.forgejo.builds.DetectForgejo;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoReactions;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * How the builds of the branches on Forgejo went (ADR 174): a badge on each branch whose tip, or that of the branch
 * it follows, is on Forgejo, with what it means in the help.
 */
public final class ForgejoFeature {

    /**
     * The id of the feature, by which it is on or off for a repository (ADR 171).
     */
    public static final String ID = "forgejo";

    /**
     * How often the commits not read yet, those whose builds run and those read before a fetch are read.
     */
    private static final Duration READ_INTERVAL = Duration.ofSeconds(10);

    /**
     * The badges of the branches, read as the timer says and after a fetch, from Forgejo's API, with fj's token; on
     * with a remote on Forgejo.
     */
    public static final Feature FEATURE = Feature.of()
            .every(READ_INTERVAL, BuildsMsg.Tick::new)
            .reacting(ForgejoReactions::to)
            .badging(ForgejoBadge::of)
            .explaining(BuildLegend.BRANCHES)
            .running(ForgejoServices::running)
            .applying(ID, model -> new DetectForgejo());

    private ForgejoFeature() {}
}

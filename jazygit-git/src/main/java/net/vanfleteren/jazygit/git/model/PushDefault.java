package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * What git's {@code push.default} says {@code git push} pushes, without a refspec (ADR 40).
 */
public enum PushDefault {
    NOTHING,
    CURRENT,
    UPSTREAM,
    SIMPLE,
    MATCHING;

    /**
     * The value {@code text} spells, as git reads it: {@code tracking} is another name of {@code upstream}. None for a
     * value git does not know, which makes {@code git push} fail.
     */
    public static Option<PushDefault> parse(String text) {
        return switch (text.strip()) {
            case "nothing" -> Option.of(NOTHING);
            case "current" -> Option.of(CURRENT);
            case "upstream", "tracking" -> Option.of(UPSTREAM);
            case "simple" -> Option.of(SIMPLE);
            case "matching" -> Option.of(MATCHING);
            default -> Option.none();
        };
    }
}

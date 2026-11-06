package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;

/**
 * How the pictures of review remarks are drawn: in half blocks (ADR 84), or by the terminal itself, with the Kitty
 * graphics protocol (ADR 86) or with Sixel (ADR 87).
 */
public enum PictureMode {
    HALF_BLOCKS,
    KITTY,
    SIXEL;

    /**
     * How pictures are drawn in this terminal: as {@code JAZYGIT_IMAGES} says ({@code kitty}, {@code sixel} or
     * {@code halfblocks}), or else with Kitty's protocol in Kitty and Ghostty outside tmux and screen, which do not pass
     * it on without being told to, and in half blocks elsewhere.
     */
    public static final PictureMode CURRENT = of(
            Option.of(System.getenv("JAZYGIT_IMAGES")),
            Option.of(System.getenv("TERM")),
            Option.of(System.getenv("TERM_PROGRAM")),
            Option.of(System.getenv("TMUX")).isDefined()
                    || Option.of(System.getenv("STY")).isDefined());

    /**
     * How pictures are drawn when {@code asked} for, in terminal {@code term} or {@code program}, inside a multiplexer
     * or not. Sixel is only drawn when asked for: no variable tells a terminal that has it.
     */
    static PictureMode of(Option<String> asked, Option<String> term, Option<String> program, boolean multiplexed) {
        return asked.map(String::strip)
                .filter(what -> !what.isEmpty() && !what.equalsIgnoreCase("auto"))
                .map(what -> switch (what.toLowerCase(java.util.Locale.ROOT)) {
                    case "kitty" -> KITTY;
                    case "sixel" -> SIXEL;
                    default -> HALF_BLOCKS;
                })
                .getOrElse(() -> !multiplexed
                                && (term.exists(t -> t.equals("xterm-kitty") || t.equals("xterm-ghostty"))
                                        || program.exists(p -> p.equalsIgnoreCase("ghostty")))
                        ? KITTY
                        : HALF_BLOCKS);
    }

    /**
     * Whether a picture is drawn as placeholder cells, which say which image and which part of it they are (ADR 86).
     */
    public boolean placeholders() {
        return this != HALF_BLOCKS;
    }
}

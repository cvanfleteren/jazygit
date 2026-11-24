package net.vanfleteren.jazygit.kernel.ui.markdown;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Locale;
import java.util.function.Function;

/**
 * How pictures are drawn (ADR 188): by the terminal itself with the Kitty graphics protocol where it has it, else with
 * Sixel where it has that, else in half blocks (ADR 84), which every terminal draws.
 */
public enum PictureMode {
    HALF_BLOCKS,
    KITTY,
    SIXEL;

    /**
     * How pictures are drawn in this terminal, as {@link #of} tells from the environment.
     */
    public static final PictureMode CURRENT =
            of(Option.of(System.getenv("JAZYGIT_IMAGES")), name -> Option.of(System.getenv(name)));

    /**
     * How pictures are drawn when {@code asked} for ({@code kitty}, {@code sixel} or {@code halfblocks}), else in the
     * terminal the environment {@code env} tells of: half blocks inside tmux or screen, which pass neither protocol on
     * without being told to; Kitty's protocol in Kitty and Ghostty, which have its Unicode placeholders; Sixel in
     * WezTerm, iTerm2, mintty, foot, mlterm, contour, Konsole and Windows Terminal; half blocks elsewhere. A terminal is
     * not asked what it has: its answer would come in as keys do.
     */
    static PictureMode of(Option<String> asked, Function<String, Option<String>> env) {
        return asked.map(String::strip)
                .filter(what -> !what.isEmpty() && !what.equalsIgnoreCase("auto"))
                .map(what -> switch (what.toLowerCase(Locale.ROOT)) {
                    case "kitty" -> KITTY;
                    case "sixel" -> SIXEL;
                    default -> HALF_BLOCKS;
                })
                .getOrElse(() -> detected(env));
    }

    private static PictureMode detected(Function<String, Option<String>> env) {
        Option<String> term = env.apply("TERM").map(t -> t.toLowerCase(Locale.ROOT));
        Option<String> program = env.apply("TERM_PROGRAM").map(p -> p.toLowerCase(Locale.ROOT));
        if (env.apply("TMUX").isDefined() || env.apply("STY").isDefined()) {
            return HALF_BLOCKS;
        }
        if (term.exists(t -> t.equals("xterm-kitty") || t.equals("xterm-ghostty")) || program.contains("ghostty")) {
            return KITTY;
        }
        boolean sixel = program.exists(SixelTerminals.PROGRAMS::contains)
                || term.exists(t -> SixelTerminals.TERMS.exists(t::startsWith))
                || env.apply("KONSOLE_VERSION").isDefined()
                || env.apply("WT_SESSION").isDefined();
        return sixel ? SIXEL : HALF_BLOCKS;
    }

    /**
     * Whether a picture is drawn as placeholder cells, which say which image and which part of it they are: for the
     * terminal to draw, or for Sixel to be painted over.
     */
    public boolean placeholders() {
        return this != HALF_BLOCKS;
    }

    /**
     * The terminals that draw Sixel: the programs, as {@code TERM_PROGRAM} names them, and the terminals, as
     * {@code TERM} starts. In a class of their own, made once {@link #CURRENT} asks for them: an enum's constants, and
     * {@code CURRENT} with them, are made before any other of its static fields is set, which would still be null.
     */
    private static final class SixelTerminals {
        static final Set<String> PROGRAMS = HashSet.of("wezterm", "iterm.app", "mintty");
        static final Set<String> TERMS = HashSet.of("foot", "mlterm", "contour", "yaft");
    }
}

package net.vanfleteren.jazygit.ui.content;

import io.vavr.control.Option;
import java.time.ZoneId;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * How a {@link Body} is drawn.
 *
 * @param styles  how it is coloured
 * @param zone    the time zone its times are shown in
 * @param asGiven the lines of the hunk a range is open in, shown as git gives them (ADR 83)
 * @param width   how wide its area is, which a body drawn to its width is drawn as wide as (ADR 103)
 */
public record DrawContext(Theme.SpanStyles styles, ZoneId zone, Option<DiffStops.HunkAt> asGiven, int width) {}

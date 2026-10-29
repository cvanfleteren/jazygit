package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * How the user wants things shown: what is kept from one session to the next, and what holds for this one only.
 *
 * @param settings      what the user chose to keep from one session to the next, like the theme
 * @param themeOverride the theme asked for at startup, or highlighted in the theme dialog, for this session only,
 *                      until the user chooses one
 * @param ignoreWhitespace whether the diffs leave out changes to whitespace only, for this session only
 * @param dragged       the width the left column of a screen is dragged to, which is kept once it is let go
 */
@With
public record Preferences(
        Settings settings, Option<ThemeName> themeOverride, boolean ignoreWhitespace, Option<Dragged> dragged) {

    public static final Preferences DEFAULT = new Preferences(Settings.DEFAULT, Option.none(), false, Option.none());

    /**
     * How the diffs of the files and the commits are read: a merge against its first parent when changes are drawn
     * rich, as the rich view does not draw git's combined diff (ADR 44).
     */
    public DiffOptions diffOptions() {
        return new DiffOptions(ignoreWhitespace, settings.diffStyle() == DiffStyle.RICH);
    }

    /**
     * How the changes of a commit or a stash are drawn.
     */
    public DiffStyle diffStyle() {
        return settings.diffStyle();
    }

    /**
     * The theme the UI is drawn in: the one asked for at startup, else the saved one.
     */
    public ThemeName theme() {
        return themeOverride.getOrElse(settings.theme());
    }

    /**
     * How wide the left column of {@code screen} is, in percent of the width: where it is dragged to, else the
     * width kept.
     */
    public int leftWidth(Screen screen) {
        return dragged.filter(drag -> drag.screen() == screen)
                .map(Dragged::width)
                .getOrElse(() -> keptWidth(screen));
    }

    /**
     * How wide the left column of {@code screen} is kept, in percent of the width.
     */
    public int keptWidth(Screen screen) {
        return switch (screen) {
            case REPOSITORY -> settings.repositoryWidth();
            case PULL_REQUESTS -> settings.pullRequestsWidth();
        };
    }

    /**
     * The settings with the left column of {@code screen} kept {@code width} wide.
     */
    public Settings keepingWidth(Screen screen, int width) {
        return switch (screen) {
            case REPOSITORY -> settings.withRepositoryWidth(width);
            case PULL_REQUESTS -> settings.withPullRequestsWidth(width);
        };
    }

    /**
     * The left column of {@code screen}, dragged to {@code width} percent of the width.
     */
    public record Dragged(Screen screen, int width) {}
}

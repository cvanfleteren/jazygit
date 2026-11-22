package net.vanfleteren.jazygit.kernel.ui;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.process.SshAsking;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Reactions.Reaction;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeUpdate;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Legend;
import net.vanfleteren.jazygit.kernel.ui.screen.ScreenSpec;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;

/**
 * A feature, as the app knows it: its actions, which bring its keys and its popups, what it shows in the content
 * panel, the settings it keeps, the sections it adds to the settings panel, what it does every so often, what it
 * answers of what happens in the kernel, what it shows of the branches in the branches pane, the screens it brings,
 * and what its symbols in a panel mean. Each feature package declares one, as {@code XxxFeature.FEATURE}, and
 * {@code Features.ALL} lists it; that is all a new feature changes outside its package (ADR 127).
 *
 * <p>Making the standard keymap makes every feature, so a feature does not ask for {@code Keymap.standard()} while its
 * class is initialised.
 *
 * @param actions     the actions, in the order their keys are listed in a panel
 * @param contents    what the feature shows in the content panel, asked in this order, before the content panel's
 *                    own (ADR 154)
 * @param settingKeys the settings it keeps in the settings file, which the app reads and writes with the kernel's
 *                    (ADR 163)
 * @param settings    the sections it adds to the settings panel, in this order (ADR 163)
 * @param timers      what it dispatches every so often, without input
 * @param reactions   what it answers of what happens in the kernel, whichever part it keeps (ADR 158)
 * @param badges      what it shows of a branch in the branches pane, beside what git says (ADR 167)
 * @param groupings   the groups it makes of the local branches in the branches pane (ADR 167)
 * @param screens     the screens it brings, which the left of the window shows after the repository's (ADR 168)
 * @param legends     what it adds to the legends of the panels it draws in, which their help lists (ADR 168)
 * @param fills       what it gives for the keys of the panes, as the line above a list or what a pane lists (ADR 172)
 */
public record Feature(
        List<FeatureAction> actions,
        List<ContentSource> contents,
        List<SettingKey<?>> settingKeys,
        List<SettingsSection> settings,
        List<Timer> timers,
        List<Reaction> reactions,
        List<BranchBadges.Badge> badges,
        List<BranchGroups.Grouping> groupings,
        List<ScreenSpec> screens,
        List<Legend> legends,
        Fills fills,
        Running running,
        Predicate<Model> spinning,
        Function<Model, List<ShownTime>> clocking,
        Option<Applying> applying) {

    /**
     * The feature of {@code actions}, in that order, which shows nothing in the content panel of its own, has no
     * settings, dispatches nothing every so often, answers nothing, shows nothing of the branches, brings no screen
     * and adds nothing to a legend.
     */
    public static Feature of(FeatureAction... actions) {
        return new Feature(
                List.of(actions),
                List.empty(),
                List.empty(),
                List.empty(),
                List.empty(),
                List.empty(),
                List.empty(),
                List.empty(),
                List.empty(),
                List.empty(),
                Fills.NONE,
                Running.NONE,
                model -> false,
                model -> List.empty(),
                Option.none());
    }

    /**
     * This feature, showing {@code contents} in the content panel as well, asked in that order.
     */
    public Feature showing(ContentSource... contents) {
        return new Feature(
                actions,
                this.contents.appendAll(List.of(contents)),
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, keeping the settings of {@code keys} in the settings file as well.
     */
    public Feature keeping(SettingKey<?>... keys) {
        return new Feature(
                actions,
                contents,
                settingKeys.appendAll(List.of(keys)),
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, adding {@code sections} to the settings panel as well, in that order.
     */
    public Feature withSettings(SettingsSection... sections) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings.appendAll(List.of(sections)),
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also dispatching the message {@code msg} makes every {@code interval}: a read it does again, as
     * the message tells it to. The timers of the features are listed in the order of the features.
     */
    public Feature every(Duration interval, Supplier<Msg> msg) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers.append(new Timer(interval, msg)),
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also answering what happens in the kernel with {@code reaction}: it is told each event, and
     * leaves the model as it is for those that are none of its concern.
     */
    public Feature reacting(Reaction reaction) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions.append(reaction),
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also showing what {@code badge} makes of a branch in the branches pane, after the badges of the
     * features before it (ADR 167).
     */
    public Feature badging(BranchBadges.Badge badge) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges.append(badge),
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also grouping the local branches in the branches pane with {@code grouping}; a branch the features
     * before it grouped stays in their group (ADR 167).
     */
    public Feature grouping(BranchGroups.Grouping grouping) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings.append(grouping),
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also giving what {@code filler} makes of a model for {@code key} of a pane, after what the features
     * before it give (ADR 172).
     */
    public <T> Feature filling(PaneKey<T> key, Function<Model, Option<T>> filler) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills.with(key, filler),
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also bringing {@code screen}, which the title of the status names after those of the features
     * before it (ADR 168). The settings of its layout are kept as the feature keeps them, with {@link #keeping}.
     */
    public Feature screen(ScreenSpec screen) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens.append(screen),
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also saying what its symbols in a panel mean with {@code legends}, which the help of that panel
     * lists after the panel's own and those of the features before it (ADR 168).
     */
    public Feature explaining(Legend... legends) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                this.legends.appendAll(List.of(legends)),
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, building what it runs against with {@code running} once a repository is opened, and again for
     * each one opened after it (ADR 171).
     */
    public Feature running(Running running) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                applying);
    }

    /**
     * This feature, also keeping the spinner turning while {@code spinning} says one of its views shows one (ADR 171).
     */
    public Feature spinning(Predicate<Model> spinning) {
        Predicate<Model> before = this.spinning;
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                before.or(spinning),
                clocking,
                applying);
    }

    /**
     * This feature, also waking the app when a time {@code clocking} says one of its popups shows changes, as a
     * screen does (ADR 124, 168, 171).
     */
    public Feature clocking(Function<Model, List<ShownTime>> clocking) {
        Function<Model, List<ShownTime>> before = this.clocking;
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                model -> before.apply(model).appendAll(clocking.apply(model)),
                applying);
    }

    /**
     * {@code keys} with the keys of this feature added, in the order of its actions.
     */
    public Keymap keys(Keymap keys) {
        UnaryOperator<Keymap> adding = given ->
                actions.foldLeft(given, (keymap, action) -> action.keys().apply(keymap));
        return applying.map(feature -> keys.owned(feature.id(), adding)).getOrElse(() -> adding.apply(keys));
    }

    /**
     * This feature, a forge, on for a repository only once the kernel chose it (ADR 171, 177): {@code detect} answers
     * with {@code FeatureMsg.Claimed}, the remotes of the repository on it, and the kernel switches on the forge of
     * the home, or asks the user which it is. It is asked once a repository is opened; until then, the feature is off.
     * A worktree of the repository shown before is not asked again (ADR 182).
     */
    public Feature applying(String id, Function<Model, Cmd> detect) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                badges,
                groupings,
                screens,
                legends,
                fills,
                running,
                spinning,
                clocking,
                Option.of(new Applying(id, detect)));
    }

    /**
     * This feature as the app runs it (ADR 171): when it is on or off for a repository, nothing of it shows or runs
     * while it is off, but its settings; it is off from the opening of a repository until it found it applies. A
     * feature that decides nothing is as it is.
     */
    public Feature gated() {
        return applying.map(this::gated).getOrElse(this);
    }

    private Feature gated(Applying applying) {
        String id = applying.id();
        // A worktree of the repository shown before opens on the forge decided there (ADR 182).
        Reaction detecting =
                (model, event) -> event instanceof KernelEvent.RepositoryOpened && !ForgeUpdate.known(model)
                        ? ForgeUpdate.opening(FeatureUpdate.off(model, id), id)
                                .and(applying.detect().apply(model))
                        : Update.Next.of(model);
        return new Feature(
                actions,
                contents.map(source -> (ContentSource) new GatedSource(id, source)),
                settingKeys,
                settings,
                timers.map(timer -> new Timer(
                        timer.interval(),
                        () -> new FeatureMsg.Owned(id, timer.msg().get()))),
                reactions
                        .map(reaction -> (Reaction) new GatedReaction(id, reaction))
                        .prepend(detecting),
                badges.map(badge -> (BranchBadges.Badge)
                        (model, branch) -> model.on(id) ? badge.of(model, branch) : BranchBadges.Shown.NONE),
                groupings.map(grouping -> (BranchGroups.Grouping)
                        (model, branches) -> model.on(id) ? grouping.of(model, branches) : List.empty()),
                screens,
                legends.map(legend -> legend.of(id)),
                fills.gated(model -> model.on(id)),
                running,
                model -> model.on(id) && spinning.test(model),
                model -> model.on(id) ? clocking.apply(model) : List.empty(),
                Option.of(applying));
    }

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return actions.flatMap(action -> action.popups().apply(dispatch));
    }

    /**
     * Dispatches what {@code msg} makes every {@code interval}.
     */
    public record Timer(Duration interval, Supplier<Msg> msg) {}

    /**
     * What a feature builds to run against once a repository is opened (ADR 171): the services of the {@link Env},
     * and the credentials git runs with, as a forge's token.
     */
    @FunctionalInterface
    public interface Running {

        /**
         * A feature that builds nothing.
         */
        Running NONE = opening -> Contribution.NONE;

        Contribution of(RepositoryOpening opening);
    }

    /**
     * What a feature is given to build what it runs against, once a repository is opened.
     *
     * @param git          the repository, as git reads it
     * @param settings     the settings of the session
     * @param store        where the settings are kept, read again where a feature needs what was saved since
     * @param ssh          how ssh, run for what the user starts, may ask the user (ADR 150)
     * @param stateFile    the file of what jazygit keeps by itself of all repositories at once
     * @param repositories the directory where what is kept for each repository is, by its directory
     * @param cacheDir     the directory where what is only cached is kept
     */
    public record RepositoryOpening(
            Git git,
            Settings settings,
            SettingsStore store,
            SshAsking ssh,
            Path stateFile,
            Path repositories,
            Path cacheDir) {}

    /**
     * How a forge tells which remotes of a repository are on it, from which the kernel decides whether it applies to
     * the repository (ADR 171, 177).
     *
     * @param id     its id, by which the model keeps whether it is on
     * @param detect what finds out in a model which remotes are on it, and answers with {@code FeatureMsg.Claimed}
     */
    public record Applying(String id, Function<Model, Cmd> detect) {}

    /**
     * {@code source}, of the feature {@code id}, which shows nothing while that is off.
     */
    public record GatedSource(String id, ContentSource source) implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored pane) {
            return model.on(id) ? source.shown(model, pane) : Option.none();
        }

        @Override
        public List<ContentArea> areas() {
            return source.areas();
        }
    }

    /**
     * {@code reaction}, which answers nothing while the feature {@code id} is off.
     */
    private record GatedReaction(String id, Reaction reaction) implements Reaction {

        @Override
        public Update.Next to(Model model, KernelEvent event) {
            return model.on(id) ? reaction.to(model, event) : Update.Next.of(model);
        }

        @Override
        public Model started(Model model, List<Cmd> cmds) {
            return model.on(id) ? reaction.started(model, cmds) : model;
        }
    }

    /**
     * What a feature runs against for a repository.
     *
     * @param services       what it runs against of its own, under its services
     * @param gitCredentials the environment git runs with to talk to the remotes as the account the feature knows,
     *                       if it knows one
     */
    public record Contribution(Env.Services services, Option<Supplier<Map<String, String>>> gitCredentials) {

        public static final Contribution NONE = new Contribution(Env.Services.NONE, Option.none());
    }
}

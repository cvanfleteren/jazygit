package net.vanfleteren.jazygit.kernel.ui;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.process.SshAsking;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Reactions.Reaction;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeUpdate;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Heading;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Legend;
import net.vanfleteren.jazygit.kernel.ui.screen.ScreenSpec;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;

/**
 * A feature, as the app knows it: its actions, which bring its keys and its popups, what it shows in the content
 * panel, the settings it keeps, the sections it adds to the settings panel, what it does every so often, what it
 * answers of what happens in the kernel, what it shows of the branches in the branches pane, the screens it brings,
 * the panes it brings to a screen, and what its symbols in a panel mean. Each feature package declares one, as
 * {@code XxxFeature.FEATURE}, and {@code Features.ALL} lists it; that is all a new feature changes outside its package
 * (ADR 127).
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
 * @param screens     the screens it brings, which the left of the window shows after the repository's (ADR 168)
 * @param panes       the panes it brings to a screen, the repository's or its own, which the screen orders (ADR 211)
 * @param legends     what it adds to the legends of the panels it draws in, which their help lists (ADR 168)
 * @param fills       what it gives for the slots, as the line above a list, whether a view spins or the pictures
 *                    its views may draw (ADR 172, 202)
 */
public record Feature(
        List<FeatureAction> actions,
        List<ContentSource> contents,
        List<SettingKey<?>> settingKeys,
        List<SettingsSection> settings,
        List<Timer> timers,
        List<Reaction> reactions,
        List<ScreenSpec> screens,
        List<ScreenPane> panes,
        List<Legend> legends,
        Fills fills,
        Running running,
        Option<Applying> applying) {

    /**
     * The feature of {@code actions}, in that order, which shows nothing in the content panel of its own, has no
     * settings, dispatches nothing every so often, answers nothing, fills no slot, brings no screen and adds
     * nothing to a legend.
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
                Fills.NONE,
                Running.NONE,
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
                screens,
                panes,
                legends,
                fills,
                running,
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
                screens,
                panes,
                legends,
                fills,
                running,
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
                screens,
                panes,
                legends,
                fills,
                running,
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
                screens,
                panes,
                legends,
                fills,
                running,
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
                screens,
                panes,
                legends,
                fills,
                running,
                applying);
    }

    /**
     * This feature, also giving what {@code filler} makes of a model for {@code slot}, after what the features before it
     * give (ADR 172, 202): one or nothing as an {@code Option}, or as many as a {@code List} holds. The kernel's own
     * slots, as whether a view spins, are in {@link KernelSlots}.
     */
    public <T> Feature filling(Slot<T> slot, Function<Model, ? extends Iterable<? extends T>> filler) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                screens,
                panes,
                legends,
                fills.with(slot, filler),
                running,
                applying);
    }

    /**
     * This feature, also giving what {@code filler} makes for {@code slot} of a model and of what the features give
     * for the other slots, after what the features before it give (ADR 211): for a slot that joins what a part shows
     * with what the features add to it, as the times of the branches pane and of the badges on its branches.
     */
    public <T> Feature reading(Slot<T> slot, BiFunction<Model, Fills, ? extends Iterable<? extends T>> filler) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                screens,
                panes,
                legends,
                fills.reading(slot, filler),
                running,
                applying);
    }

    /**
     * This feature, also bringing the pane {@code pane} makes to {@code screen} (ADR 211), which shows it where its
     * {@link Screen#panes()} lists its id. The repository's screen is given its panes this way, as is a feature's own.
     * A feature that is on or off for a repository brings panes only to its own screen, which hides with it, as the
     * layout of a screen counts all its panes; it shows them only while it is on ({@link #gated}).
     */
    public Feature pane(Screen screen, ScreenPane.Factory pane) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                screens,
                panes.append(new ScreenPane(screen, pane)),
                legends,
                fills,
                running,
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
                screens.append(screen),
                panes,
                legends,
                fills,
                running,
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
                screens,
                panes,
                this.legends.appendAll(List.of(legends)),
                fills,
                running,
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
                screens,
                panes,
                legends,
                fills,
                running,
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
    public Feature applying(FeatureId id, Function<Model, Cmd> detect) {
        return new Feature(
                actions,
                contents,
                settingKeys,
                settings,
                timers,
                reactions,
                screens,
                panes,
                legends,
                fills,
                running,
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
        FeatureId id = applying.id();
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
                screens,
                panes.map(pane -> pane.gated(model -> model.on(id))),
                legends.map(legend -> legend.of(id)),
                fills.gated(model -> model.on(id)),
                running,
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
    public record Applying(FeatureId id, Function<Model, Cmd> detect) {}

    /**
     * {@code source}, of the feature {@code id}, which shows nothing while that is off.
     */
    public record GatedSource(FeatureId id, ContentSource source) implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored pane) {
            return model.on(id) ? source.shown(model, pane) : Option.none();
        }

        @Override
        public Option<Heading> heading(Model model, Mirrored pane) {
            return model.on(id) ? source.heading(model, pane) : Option.none();
        }

        @Override
        public List<ContentArea> areas() {
            return source.areas();
        }
    }

    /**
     * {@code reaction}, which answers nothing while the feature {@code id} is off.
     */
    private record GatedReaction(FeatureId id, Reaction reaction) implements Reaction {

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

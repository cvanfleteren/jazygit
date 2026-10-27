package net.vanfleteren.jazygit.feature.start;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.repos.RecentReposDialog;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PopupView;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;
import net.vanfleteren.jazygit.ui.widgets.WithDetails;

/**
 * The dialogs shown when jazygit starts outside a repository: what to do instead, the path of another folder, and
 * the recent repositories. A problem shows in a box under the dialog.
 */
public final class StartDialogs {

    public static final ChoiceDialog.Spec<NoRepositoryPopup> NO_REPOSITORY = new ChoiceDialog.Spec<>(
                    "no-repository",
                    NoRepositoryPopup.class,
                    popup -> Messages.get(
                            popup.exists() ? "dialog.start.notARepository" : "dialog.start.notFound",
                            RecentReposDialog.shown(popup.directory())),
                    List.of(
                            new Choice<NoRepositoryPopup>('i', "dialog.start.init", StartMsg.InitChosen::new)
                                    .shownWhen((model, popup) -> popup.exists()),
                            new Choice<>('o', "dialog.start.openFolder", StartMsg.OpenFolderChosen::new),
                            new Choice<NoRepositoryPopup>(
                                            'r', "dialog.start.openRecent", StartMsg.OpenRecentChosen::new)
                                    .shownWhen((model, popup) -> popup.recent().nonEmpty()),
                            new Choice<>('q', "dialog.start.quit", Msg.Quit::new)))
            .cancelledBy(Msg.Quit::new);

    public static final TextInputDialog.Spec<OpenFolderPopup> OPEN_FOLDER = new TextInputDialog.Spec<>(
                    "open-folder",
                    OpenFolderPopup.class,
                    popup -> Messages.get(
                            "dialog.start.openFolder.title",
                            RecentReposDialog.shown(popup.outside().directory())),
                    Option.of(Messages.get("dialog.start.openFolder.placeholder")),
                    StartMsg.FolderConfirmed::new)
            .cancelledBy(StartMsg.FolderCancelled::new);

    public static final PickDialog.Spec<OpenRecentPopup, String> OPEN_RECENT = new PickDialog.Spec<>(
                    "open-recent",
                    OpenRecentPopup.class,
                    popup -> Messages.get("dialog.recentRepos.title"),
                    popup -> popup.outside().recent().map(RecentReposDialog::row),
                    StartMsg.RecentPicked::new)
            .cancelledBy(StartMsg.RecentCancelled::new);

    private StartDialogs() {}

    /**
     * The dialogs, which send what the user does to {@code dispatch}. At most one shows at a time.
     */
    public static List<PopupView> popups(Consumer<Msg> dispatch) {
        return List.of(
                new Detailed(
                        new ChoiceDialog<>(NO_REPOSITORY, dispatch),
                        model -> model.popup(NoRepositoryPopup.class)
                                .flatMap(NoRepositoryPopup::problem)
                                .map(ErrorText::of)),
                new Detailed(
                        new TextInputDialog<>(OPEN_FOLDER, dispatch),
                        model -> model.popup(OpenFolderPopup.class).flatMap(StartDialogs::problem)),
                new PickDialog<>(OPEN_RECENT, dispatch));
    }

    /**
     * What is wrong with the path last confirmed in {@code popup}: it names no folder, or one that git could not
     * open.
     */
    private static Option<String> problem(OpenFolderPopup popup) {
        return popup.notFound()
                .map(directory -> Messages.get("dialog.start.openFolder.notFound", RecentReposDialog.shown(directory)))
                .orElse(() -> popup.problem().map(ErrorText::of));
    }

    /**
     * {@code view}, with what {@code details} says of the model in a box under it.
     */
    private record Detailed(PopupView view, Function<Model, Option<String>> details) implements PopupView {

        @Override
        public Option<Element> render(Model model) {
            Option<String> shown = details.apply(model);
            return view.render(model).map(dialog -> new WithDetails(dialog, shown, shown.toList()));
        }

        @Override
        public List<String> ids() {
            return view.ids();
        }
    }
}

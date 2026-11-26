package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.BaseBranchError;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.load.LoadAhead;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitFiles;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitTree;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.state.view.ViewIntent;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Listing the files a commit, a stash, a pull request or a branch changed in the files pane, in place of the changed files of
 * the working tree, and going back to those (ADR 44). The view moves the focus: into the files pane when they are
 * listed, and back to the pane they were opened from when they are closed. The files of a change a part states remarks for
 * can list only the files that have some (ADR 57, 171).
 */
public final class CommitFilesUpdate {

    private CommitFilesUpdate() {}

    public static Next update(Model model, CommitFilesMsg msg) {
        return switch (msg) {
            case CommitFilesMsg.Opened(var of, var pane) -> opened(locked(model, pane), of, pane);
            // Escape moves the focus back to the list the files are of, which the app is asked to do: they stay (ADR
            // 141), and follow the list with the focus again (ADR 149, 207).
            case CommitFilesMsg.Closed() ->
                Next.of(model.mapRepoView(repoView -> repoView.mapFiles(files -> files.withLocked(Option.none()))))
                        .asking(model.repoView().files().backTo().map(ViewIntent.ToPane::to));
            // Only the files of a change that a part states remarks for are listed by them (ADR 57, 171).
            case CommitFilesMsg.ListedCycled() ->
                Next.of(model.mapRepoView(repoView ->
                        repoView.mapFiles(files -> files.withCommitFiles(files.commitFiles()
                                .map(view -> model.remarks(view.of()).isDefined()
                                        ? view.withListed(view.listed().next())
                                        : view)))));
            case CommitFilesMsg.Moved(Changes from, Changes to) -> moved(model, from, to);
            case CommitFilesMsg.BranchFilesRequested(BranchName branch) -> branchFiles(model, branch);
            case CommitFilesMsg.AllToggled() -> allToggled(model);
        };
    }

    /**
     * {@code model} with the files pane locked anew to the list the pane {@code pane} shows: what it lists follows the
     * highlight of that list only, whichever pane has the focus (ADR 149).
     */
    private static Model locked(Model model, PaneId pane) {
        return model.mapRepoView(repoView -> repoView.mapFiles(files ->
                files.lockedTo(pane, FilesLock.In.of(repoView, pane.equals(Scopes.BRANCHES.pane())))));
    }

    /**
     * Enter on {@code of}, in the pane {@code pane}, or {@code F} on a branch: the files listed already, as they follow
     * its highlight or were opened before, stay as they are, read or being read, and are listed whole (ADR 141, 149).
     */
    private static Next opened(Model model, Changes of, PaneId pane) {
        return model.repoView()
                .files()
                .commitFiles()
                .filter(view -> view.of().equals(of))
                .map(view -> Next.of(model.mapRepoView(repoView -> repoView.mapFiles(files ->
                        files.withCommitFiles(Option.of(view.withFollowed(false).withPane(pane)))))))
                .getOrElse(() -> open(model, CommitFilesView.of(of, pane, Scopes.FILES.pane())));
    }

    /**
     * Shows {@code view}, reading its files, and all files of its commit when it lists them; the features are told the
     * files were opened, so that what goes with them is read too, as the remarks a forge has on them (ADR 171).
     */
    private static Next open(Model model, CommitFilesView view) {
        return Events.happened(
                new Next(
                        model.mapRepoView(
                                repoView -> repoView.mapFiles(files -> files.withCommitFiles(Option.of(view)))),
                        List.<Cmd>of(LoadCommitFiles.of(view)).appendAll(treeLoad(view))),
                new KernelEvent.ChangesOpened(view.of()));
    }

    /**
     * The files of {@code from}, while they are listed, as those of {@code to} in their place, as a pull request that
     * moved on to other commits since its files were read: they are read anew, and what is listed, collapsed and
     * filtered of them stays, and whether all files are listed, read anew at the commit they end at (ADR 55, 105, 128).
     * The features are not told they were opened: what goes with them is what they had.
     */
    private static Next moved(Model model, Changes from, Changes to) {
        return model.repoView()
                .files()
                .commitFiles()
                .filter(view -> view.of().equals(from))
                .map(view -> {
                    CommitFilesView again = CommitFilesView.of(to, view.pane(), view.listedIn())
                            .withListed(view.listed())
                            .withCollapsed(view.collapsed())
                            .withFilter(view.filter())
                            .withAllFiles(view.allFiles())
                            .withTree(Option.when(view.allFiles(), Loadable::loading));
                    return new Next(
                            model.mapRepoView(
                                    repoView -> repoView.mapFiles(files -> files.withCommitFiles(Option.of(again)))),
                            List.<Cmd>of(LoadCommitFiles.of(again)).appendAll(treeLoad(again)));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Lists all files of the commit the files listed end at, or only those changed again (ADR 128). All files are
     * read the first time they are listed, and again only when reading them failed.
     */
    private static Next allToggled(Model model) {
        return model.repoView()
                .files()
                .commitFiles()
                .map(view -> {
                    boolean all = !view.allFiles();
                    boolean reads = all && view.tree().forAll(Loadable::isFailed);
                    CommitFilesView toggled =
                            view.listingAll(all).withTree(reads ? Option.of(Loadable.loading()) : view.tree());
                    return new Next(
                            model.mapRepoView(
                                    repoView -> repoView.mapFiles(files -> files.withCommitFiles(Option.of(toggled)))),
                            reads ? List.<Cmd>of(new LoadCommitTree(view.of())) : List.empty());
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The files of {@code branch}: as a part knows them, as those of its pull request, else against its base. An
     * error shown before goes, as the files are asked for anew.
     */
    private static Next branchFiles(Model model, BranchName branch) {
        Model cleared = model.clearError();
        return cleared.repo()
                .branch(branch)
                .flatMap(known -> cleared.parts().files(known))
                .map(files -> files.apply(cleared))
                .getOrElse(() -> againstBase(cleared, branch));
    }

    /**
     * Lists the files {@code branch} changes against its base, the base of ADR 20, at the commits the branches read
     * last have them at (ADR 61). The main branch has no base to be compared with: all files of its tip are listed,
     * as committed, whatever is checked out (ADR 128). A branch without a base has nothing to be compared with. A
     * branch with an open pull request, the main branch too, shows the files of that instead, before this is asked.
     */
    public static Next againstBase(Model model, BranchName branch) {
        LoadAhead load = AheadUpdate.of(model, new BranchRef.Local(branch));
        return AheadUpdate.main(model).contains(branch)
                ? load.tip()
                        .map(tip -> open(
                                locked(model, Scopes.BRANCHES.pane()),
                                CommitFilesView.of(
                                                new Changes.OfTip(branch, tip),
                                                Scopes.BRANCHES.pane(),
                                                Scopes.FILES.pane())
                                        .withAllFiles(true)
                                        .withTree(Option.of(Loadable.loading()))))
                        .getOrElse(() -> Next.of(model))
                : load.base()
                        .map(base -> load.tip()
                                .map(tip -> update(
                                        model,
                                        new CommitFilesMsg.Opened(
                                                new Changes.OfBranch(branch, base, tip), Scopes.BRANCHES.pane())))
                                .getOrElse(() -> Next.of(model)))
                        .getOrElse(() -> Next.of(model.fail(new BaseBranchError.None(branch))));
    }

    /**
     * All files of the commit the files of {@code view} end at, when it lists them and they are still to be read.
     */
    private static Option<LoadCommitTree> treeLoad(CommitFilesView view) {
        return Option.when(
                view.allFiles() && view.tree().exists(tree -> tree instanceof Loadable.Loading<?>),
                () -> new LoadCommitTree(view.of()));
    }
}

# 68. Asking where to check out, in a linked worktree

Date: 2026-11-01

## Context

Checking out a branch ran `git checkout` in the worktree shown, whichever that was (issue #297). A linked worktree is
often someone's sandbox, an agent's say: shown in jazygit to look at it, its branch and its files changed under the
agent that was still working in it.

Only one case already looked past the worktree shown: a branch that another worktree has checked out git refuses to
check out, so switching to that worktree is offered instead (`SwitchWorktreePopup`). Nothing moves a HEAD there.

What was wanted: the user chooses where a checkout goes, and the branch of a worktree never changes but where the
user chose it to.

## Decision

1. **In a linked worktree, every checkout asks where it goes.** That is checking out a local branch, a remote one
   (`CheckoutRemote`, also when the name of a local branch is taken and another one is asked for), a pull request,
   and creating a branch, which switches to it (`NewBranchCmd`): each moves HEAD and changes the files. The main
   worktree does not ask: checking out there stays one key. Next to a bare repository, where there is no main
   worktree, every worktree is a linked one, so every checkout asks. Moving commits, which also leaves another branch
   checked out, is not a checkout the user asks for, and does not ask.
2. **The choices are: here, in the main worktree, or in a new worktree.** Each shows what it changes: the worktree
   and the branch it has checked out, whether the main one has changes, and why the one shown is locked, if it is.
   Checking out in another linked worktree is not offered: that is where the next sandbox is most likely to be.
   - *Here* does what checking out did before. It is not offered while the worktree shown is locked; the reason it
     is locked is shown in its place.
   - *In the main worktree* is not offered next to a bare repository.
   - *In a new worktree* asks where it goes, as adding a worktree does (`NewWorktreePopup`), starting out at the
     same location. Once added, the app switches to it without asking (`SwitchToAddedPopup` is skipped): going there
     was what was chosen.
3. **Each choice has a letter; Enter is never "here".** The dialog opens on *in the main worktree*, or on *in a new
   worktree* without one, so that a key pressed out of habit does not change the worktree shown.
4. **The main worktree is switched to first, and checks out there once it is read.** The app opens the main worktree
   in place of the one shown, and checking out is asked there anew (`CheckoutRequest.again()`): with the message the
   user started with, `CheckoutMsg.Requested` or `RemoteRequested`, where there is one; else with
   `CheckoutMsg.Placed`, for a new branch, a remote branch under another name, or a pull request. As the main
   worktree does not ask (1), that runs the checkout as it always ran: the local branch that follows a remote one is
   found, a taken name is asked again, uncommitted changes are offered to be stashed. What can have changed is looked
   at again, so a branch that is gone by then fails as any checkout of it would, and one that another worktree has
   checked out offers to switch to that one. Which repository a pull request is of is not asked again: it was in the
   worktree it was asked for in, and the main worktree may not have read its pull requests yet. When the main
   worktree cannot be opened, the app stays where it was, and the message is dropped.

   Opening another repository builds a new model, so the message cannot wait in the old one. It goes along in the
   `Landing`, which is all that is handed from one repository to the next: `Landing.then`. The new model keeps it,
   in `Jobs.onArrival`, until its branches and worktrees are read, and `Update.update` handles it then, once.
5. **Which worktree asks is decided where the checkout would start.** One function, `CheckoutUpdate.place`, takes
   the checkout asked for, a `CheckoutRequest`, and either starts it or opens the dialog; the checkouts of (1) all go
   through it. A `CheckoutRequest` says how it is checked out here, what a new worktree for it checks out, and how it
   is asked for again. It is an interface rather than a sealed one, as checking out a pull request is one too, of the
   GitHub feature, which the branch feature does not use. A branch that another worktree has checked out still offers
   to switch to that one first: that moves no HEAD. Until the worktrees are read it is not known which one is shown,
   and it is checked out here, as before; they are read with the branches, which a checkout needs too.

   Creating a branch went through `Msg.PopupConfirmed`, which starts what the popup confirms without a message of the
   feature (ADR 38). It gets a message of its own, as it may ask first.

## Consequences

- A pull request checked out in a linked worktree that has changes is asked where first; the changes only refuse it
  here.
- In a linked worktree, checking out takes a key more. There is no setting to turn that off; one can be added should
  it be missed.
- A new way of checking out goes through the function of (5), or it changes linked worktrees silently again.
- A `Landing` can say what to do once the repository is shown, not only where; other features can use that to finish
  something they started in another worktree.
- Whether anything works in a worktree is not looked for: a linked one is taken to be someone's, as a lock file says
  too little, and too late.

Amends ADR 38 (`0038-a-popup-closes-by-itself.md`), for creating a branch.

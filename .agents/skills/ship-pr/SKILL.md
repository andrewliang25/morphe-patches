---
name: ship-pr
description: Turn a tested branch into a pull request into dev - clean up the diff, run the simple-english pass, write one scoped conventional commit, push and open the PR. Use for steps 4 to 6 of the development procedure ("commit, push and open the PR"), and when a stacked PR conflicts after its base PR was squash-merged.
---

# Ship

Steps 4 to 6 of the development procedure in `AGENTS.md`, run in order. The rules live in
`AGENTS.md` ("Development procedure", "Release pipeline"). This skill gives the order and the checks.

## Steps

1. **Scope the diff.** Run `git fetch origin`, `git status --short` and
   `git diff --stat origin/dev...HEAD`. Done when you can name the task of each changed file. Stage
   only the files of this task: the working tree often holds edits from other sessions.
2. **Clean up.** Read the whole diff (`git diff origin/dev...HEAD`) line by line. Remove probe
   patches and classes (`[Probe]`, `[DIAG]`), logging added for the investigation, dead and
   commented-out code, and the story in comments (issue numbers, how it was found). Done when each
   comment is a rule plus a one-clause reason, and the diff holds only the shipping change and its
   docs.
3. **Apply again** when step 2 changed code: `verify.sh` with the changed patches. Done on
   `failed 0` and `sweep: OK` for each pinned version.
4. **Simple English.** Load the `simple-english` skill. Run its self-check on each new or changed
   comment, doc paragraph, patch `name` and `description` and Settings switch text, and later on the
   commit message and the PR body. A LINE switch text also needs its ja and zh-TW entry in
   `LineStrings.java` (see "Translated text" in `AGENTS.md`). Run the check again after any later
   rename, split or merge of a patch. Done when each text passes.
5. **Commit.** One commit for each app: `type(<App>): subject`, or `type(<App> - <Patch name>):`
   for one patch. Pick the type by what the user gets:

   | Change | Type |
   |---|---|
   | New patch, or a new ability of a patch | `feat` |
   | A patch that broke or misbehaves | `fix` |
   | Support for a new app version | `bump` |
   | Support dropped for a version | add a `BREAKING CHANGE:` footer |
   | Docs or wording only | `docs` |
   | Skills, scripts, CI | `chore` or `ci` |

   Write no `Co-Authored-By` trailer. When the branch holds several commits, make them one for each
   app (`git reset --soft origin/dev`, then commit). Ask before you force-push a branch that is
   already on the remote. Done when `git log --oneline origin/dev..HEAD` shows one commit for each
   app, with the right type.
6. **Push and open the PR** into `dev` with `gh pr create --base dev --body-file -` and the
   template below. Use `--draft` when an item of the device test is still open. Done when `gh`
   prints the URL. Give the URL to the user.

## PR body template

```markdown
## Summary
<Who is blocked today, and what they get.>

## How it works
<The mechanism, with the class and method anchors. Name each app version where the sites differ.>

## Testing
- [x] `verify.sh` on <versions>: applied, sweep OK
- [x] Device (<app version>): <what the screen showed>
- [ ] <item not seen on the device>: <why not>

Steps 4 (clean up) and 5 (simple English) ran.
```

## Stacked PR after a squash merge

PR B branches off PR A. When the user squash-merges A, B still holds A's own commits, and they
conflict with the squashed commit on `dev`. Move only B's commits onto `dev`:

```bash
git fetch origin
A_HEAD=$(gh pr view <A> --json headRefOid -q .headRefOid)   # A's last commit before the merge
git rebase --onto origin/dev "$A_HEAD" <B-branch>
```

Run `verify.sh` again, then `git push --force-with-lease`. Done when
`git log --oneline origin/dev..HEAD` shows only B's commits.

## After the merge

When the user says the PR is merged: `git switch dev && git pull`, then `git branch -d <branch>`.
`-d` refuses a branch that is not merged, so it is safe. If the release run on `dev` fails, follow
the `rerelease.yml` rule in `AGENTS.md`.

# Submitting a pull request/merge request

If you find a bug that you'd like to fix, or a new feature that you'd
like to implement then please submit a pull request/merge request.

If it is a big feature then make an issue first so it can be discussed.

First, create a fork via GitHub's/GitLab's Web Interface.

Now in your terminal, git clone your fork.

And get hacking.

Make sure you

  * Use the [drasyl code style](https://github.com/drasyl-overlay/drasyl/blob/master/.editorconfig)
  * Add [changelog](https://github.com/drasyl-overlay/drasyl/blob/master/CHANGELOG.md) entry
  * Add documentation for a new feature.
  * Add tests for a new feature.
  * squash commits down to one per feature.
  * rebase to master with `git rebase master`

When ready - run the tests

    mvn test

When you are done with that git push your changes.

Go to the GitHub/GitLab website and click "New pull request/merge request".

Your patch will get reviewed and you might get asked to fix some stuff.

If so, then make the changes in the same branch, squash the commits (make multiple commits one
commit) by running:
```
git log # See how many commits you want to squash
git reset --soft HEAD~2 # This squashes the 2 latest commits together.
git status # Check what will happen, if you made a mistake resetting, you can run git reset 'HEAD@{1}' to undo.
git commit # Add a new commit message.
git push --force # Push the squashed commit to your fork repo.
```
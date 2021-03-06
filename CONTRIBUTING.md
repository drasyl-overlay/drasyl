# Contributing to drasyl

This is a short guide on how to contribute things to drasyl.

## Reporting a bug

When filing an issue, please include the following information if possible as well as a description
of the problem. Make sure you test with the [latest snapshot version of drasyl](https://docs.drasyl.org/master/getting-started/quick-start/):

  * drasyl version
  * Expected behavior
  * Actual behavior
  * Steps to reproduce
    * Bonus points: provide a minimal working example

### Important: "Getting Help Vs Reporting an Issue"

The issue tracker is not a general support forum, but a place to report bugs and asks for new features.

For end-user related support questions, try using first:
- the drasyl gitter room: [![Gitter](https://badges.gitter.im/drasyl-overlay/drasyl.svg)](https://gitter.im/drasyl-overlay/drasyl)

## Submitting a pull request/merge request

If you find a bug that you'd like to fix, or a new feature that you'd
like to implement then please submit a pull request/merge request.

If it is a big feature then make an issue first so it can be discussed.

First, create a fork via GitHub's/GitLab's Web Interface.

Now in your terminal, git clone your fork.

And get hacking.

Make sure you

  * Use the drasyl code style:
    * [.editorconfig](.editorconfig)
    * Use `final` keyword where possible.
    * Each file must have the following copyright notice in the header: 
```
Copyright (c) 2020-$today.year.

This file is part of drasyl.

 drasyl is free software: you can redistribute it and/or modify
 it under the terms of the GNU Lesser General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 
 drasyl is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.
 
 You should have received a copy of the GNU Lesser General Public License
 along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
```
  * Add [changelog](./CHANGELOG.md) entry
  * Add documentation for a new feature.
  * Add tests for a new feature.
  * squash commits down to one per feature.
  * rebase to master with `git rebase master`
  * keep your pull request/merge request as small as possible.
  * Make sure that `mvn checkstyle:check` does not print warnings for checkstyle

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

## Making a release ##

There are separate instructions for making a release in the [RELEASE](RELEASE.md)
file.

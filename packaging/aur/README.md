# AUR packaging

`PKGBUILD` and `mcrl.install` here are ready to publish to the AUR, but actually
publishing needs a personal AUR account with an SSH key on file, not something that
can be done from this repo alone. One-time submission steps, on an Arch machine (or
any machine with `base-devel` installed):

```
git clone ssh://aur@aur.archlinux.org/mcrl.git
cp packaging/aur/PKGBUILD packaging/aur/mcrl.install mcrl/
cd mcrl
makepkg --printsrcinfo > .SRCINFO
git add PKGBUILD mcrl.install .SRCINFO
git commit -m "Initial import"
git push
```

To bump the version for a new mcrl release: update `pkgver` and `sha256sums` in
`PKGBUILD` (the sha256 is printed at the end of every mcrl release, also in that
release's `SHA256SUMS.txt`), regenerate `.SRCINFO`, commit, push.

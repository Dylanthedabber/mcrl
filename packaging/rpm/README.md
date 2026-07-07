# RPM packaging

Not built or published, `rpmbuild` wasn't available to build/test this here. Two ways
to actually use it:

## Build and install locally

On Fedora/RHEL/openSUSE, with `rpm-build` installed:

```
spectool -g -R packaging/rpm/mcrl.spec   # downloads Source0 into ~/rpmbuild/SOURCES
rpmbuild -bb packaging/rpm/mcrl.spec
sudo rpm -i ~/rpmbuild/RPMS/noarch/mcrl-1.3.0-1*.rpm
```

## Publish via Fedora Copr

Copr is Fedora's equivalent of a personal Homebrew tap or the AUR: any Fedora account
can create a Copr project and have it build/host packages, no central moderation
needed. Create a project at [copr.fedorainfracloud.org](https://copr.fedorainfracloud.org),
point it at this repo/spec file (Copr can build straight from a `.spec` URL or a
`SCM` source pointed at this repo), and it handles building and serving the repo.

To bump the version for a new mcrl release: update `Version:` in `mcrl.spec` and rerun.

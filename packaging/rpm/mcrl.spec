Name:           mcrl
Version:        1.3.0
Release:        1%{?dist}
Summary:        Lift Minecraft's account chat restriction across every loader/version

License:        MIT
URL:            https://github.com/Sm0keSkreen/mcrl
Source0:        https://github.com/Sm0keSkreen/mcrl/releases/download/v%{version}/mcrl.jar

BuildArch:      noarch
Requires:       java-headless

%description
JVM javaagent that lifts Minecraft's account chat restriction, applied globally
via JDK_JAVA_OPTIONS so it works across vanilla and every loader (Fabric, Forge,
Quilt, NeoForge) without per-instance setup.

%prep
# Nothing to unpack; Source0 is the jar itself, not an archive.

%build
# Nothing to compile.

%install
mkdir -p %{buildroot}%{_datadir}/mcrl
install -m 644 %{SOURCE0} %{buildroot}%{_datadir}/mcrl/mcrl.jar

%files
%{_datadir}/mcrl/mcrl.jar

%post
echo ""
echo "mcrl.jar is installed at %{_datadir}/mcrl/mcrl.jar"
echo "One-time setup still needed: point JDK_JAVA_OPTIONS at that path (see the mcrl"
echo "README), or just run the full installer instead, which does that for you:"
echo "  curl -fsSL https://github.com/Sm0keSkreen/mcrl/releases/latest/download/install.sh | bash"

%changelog
* Tue Jul 07 2026 Sm0keSkreen <149922324+Sm0keSkreen@users.noreply.github.com> - 1.3.0-1
- Initial package

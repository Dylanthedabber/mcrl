Name:           mcrl
Version:        1.3.0
Release:        2%{?dist}
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
echo "README)."
echo "Want the Realms/telemetry/profanity extras? Writes config.json right next to this"
echo "jar, no separate download or install directory (needs sudo, %{_datadir}/mcrl is a"
echo "system directory):"
echo "  curl -fsSL https://github.com/Sm0keSkreen/mcrl/releases/latest/download/install.sh -o /tmp/mcrl-install.sh"
echo "  sudo bash /tmp/mcrl-install.sh --configure-only %{_datadir}/mcrl"

%changelog
* Tue Jul 07 2026 Sm0keSkreen <149922324+Sm0keSkreen@users.noreply.github.com> - 1.3.0-2
- Point the extras setup message at install.sh's new --configure-only mode
* Tue Jul 07 2026 Sm0keSkreen <149922324+Sm0keSkreen@users.noreply.github.com> - 1.3.0-1
- Initial package

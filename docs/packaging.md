# Native packages

[Yonaka Reader](../README.md)

## Standalone macOS application

On macOS with JDK 25 and Python 3, run:

```shell
./gradlew clean test packageDmg
```

The DMG is written to `build/macos/dmg/`. Open it, drag **Yonaka Reader.app**
to **Applications**, then eject the disk image. Java is bundled inside the app;
no system Java, installer, administrator service or background agent is needed.
The package matches the architecture of the JDK used to build it. CI currently
builds Apple Silicon; use an Intel JDK on an Intel Mac for an Intel package.

Finder's **Open With → Yonaka Reader** supports `.jpg`, `.jpeg`, `.png`, `.gif`,
`.zip` and `.cbz`. Registration does not force a new default application. Opening from
Finder works both at startup and while the reader is already running. If multiple
files are selected, the first is opened because the reader displays one book at a time.
Command-line paths containing spaces must be quoted.

To uninstall, quit Yonaka Reader and move the app from Applications to Trash.
The bundled Java is removed with it. Reading history and settings remain in
`~/Library/Application Support/YonakaReader` for future reinstalls. To clear them
as well, use **Help → Reset comic database…** before deleting the app. Books are
never deleted by reset or uninstall.

These development packages are ad-hoc signed, not Developer ID signed or notarized;
Gatekeeper may block downloaded builds. Public distribution signing/notarization is
a separate release step. `verifyMacApp` validates the generated file associations,
checks the bundle signature, and tests SQLite using the embedded Java runtime.
Finder integration and drag-to-Applications installation still require a manual check.

The displayed application version comes from the Gradle release version. macOS
`CFBundleVersion` is a separate build identifier with the major component offset
by one, because jpackage requires it to be positive even for a 0.x release.

Branding uses **Yonaka Reader ・ 夜中**. The existing `org.dustyroom.mangareader`
bundle identifier and storage paths remain stable so renaming
the app preserves reading history and preferences. The macOS icon is generated from
`src/main/resources/org/dustyroom/ui/branding/yonaka-original.png` at standard and Retina sizes.
The original crescent icon is preserved alongside it as `yonaka-original.png`.

## Windows packages

Build on Windows x64 with JDK 25. MSI packaging also requires **WiX Toolset 3.x**
(`candle.exe` and `light.exe` on `PATH`; WiX 4/5 alone are not compatible with this
JDK 25 packaging setup).

```powershell
.\gradlew.bat clean test packageMsi packageWindowsZip
```

Outputs are under `build/desktop-packages/windows/msi/` and
`build/desktop-packages/windows/portable/`. The MSI installs for the current user,
adds Start Menu/desktop shortcuts and file associations, and can be removed through
Windows Settings → Apps. A stable upgrade UUID identifies future MSI upgrades.
The portable ZIP contains `Yonaka Reader.exe` and its Java runtime; extract the
whole directory before launching. Portable builds do not register file associations.
Deleting a portable directory removes the app. Both forms preserve user settings
and reading history; use **Help → Reset comic database…** to clear them explicitly.

The binaries are currently unsigned. SmartScreen may warn about an unknown publisher;
Smart App Control or managed-device policies can block unsigned software. There is
no Apple-style notarization step, but trusted signing remains a separate release task.

## Ubuntu packages

Build on Ubuntu 22.04 or newer with JDK 25 and `fakeroot` (`sudo apt install fakeroot`).
CI targets Ubuntu 22.04 x64 to avoid unnecessarily raising the system-library baseline.

```shell
./gradlew clean test packageDeb packageLinuxTar
sudo apt install ./build/desktop-packages/linux/deb/*.deb
```

The DEB installs the application with a desktop launcher, icon and file associations.
Remove it with `sudo apt remove yonaka-reader`. Installing this local DEB does not
require a developer signing certificate; a future APT repository would need its own
repository signing setup. Configuration and history remain in the user's data directory.

For a portable copy, extract the archive from `build/desktop-packages/linux/portable/`
and launch `Yonaka Reader/bin/Yonaka Reader`. Keep the entire extracted directory;
Java is included, but graphical system libraries and a desktop session are still needed.

All package tasks appear under Gradle's **distribution** group and must run on the
target OS. CI tests the embedded runtime before packaging, then extracts each MSI/DEB
and repeats the SQLite smoke test with the extracted runtime. On disposable GitHub-hosted
runners it also installs and uninstalls MSI/DEB, checks that binaries are removed and
that sentinel user data/books remain. Windows checks the uninstall registration too.
The lifecycle script refuses to run on developer workstations. Installer UI and desktop
integration still require manual platform checks. No workflow publishes a release.

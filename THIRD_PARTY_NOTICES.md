# Third-party notices

## Ayu colors

The application's Ayu Dark and Ayu Light palettes are inspired by the
[Ayu color scheme](https://github.com/ayu-theme/ayu-colors).
Palette reference: `themes/dark.yaml` and `themes/light.yaml` in that repository.
The application adapts these colors for Swing controls and readable foregrounds.

## FlatLaf

The application uses [FlatLaf](https://github.com/JFormDesigner/FlatLaf),
Copyright FormDev Software GmbH, under the
[Apache License 2.0](https://github.com/JFormDesigner/FlatLaf/blob/main/LICENSE).
FlatLaf provides Swing control rendering and platform menu integration;
the application supplies its own two palettes and retains native window decorations.

## SQLite JDBC

The application uses [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc)
for its embedded database, under the [Apache License 2.0](https://github.com/xerial/sqlite-jdbc/blob/master/LICENSE).
SQLite is embedded; a separate database server or SQLite installation is not required.

## Gson

JSON language catalogs are read using [Gson](https://github.com/google/gson),
Copyright Google Inc., under the
[Apache License 2.0](https://github.com/google/gson/blob/main/LICENSE).

## Apache PDFBox

[Apache PDFBox](https://pdfbox.apache.org/) renders PDF pages and previews.
PDFBox and FontBox are distributed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## JPEG 2000

[jpx-imageio 0.1.0](https://github.com/pcorless/jpx-imageio/tree/v0.1.0) decodes
JPEG 2000 images embedded in PDFs. Copyright 2026 Patrick Corless, licensed under
Apache License 2.0. The decoder includes code derived from OpenJPEG 2.5.4 under
the BSD 2-Clause license. The application JAR contains the decoder’s LICENSE,
NOTICE and OpenJPEG copyright and license under `META-INF/licenses/jpx-imageio-0.1.0/`.

## Other bundled components

Logback Classic and Core 1.6.6, copyright QOS.ch, are distributed here under
the GNU Lesser General Public License 2.1. The JAR includes the upstream
notice and license in `META-INF/licenses/logback/`. The
[corresponding source](https://github.com/qos-ch/logback/tree/v_1.6.6)
and the application's Gradle build are available to rebuild the combined JAR.

SLF4J API is distributed under the MIT license. Apache Commons Lang, Apache
Commons Logging, the Apache JBIG2 ImageIO decoder and Google's Error Prone
annotations are distributed under the Apache License 2.0. The JAR includes
the Apache license in `META-INF/licenses/flatlaf-3.8/LICENSE` and the
component notices supplied by their publishers.

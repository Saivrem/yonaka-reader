# Reading history and profiles

[Yonaka Reader](../README.md)

## Drag and drop and saved state

Drop one image, PDF, ZIP/CBZ archive, or image folder anywhere in the application content.
The Open dialog also accepts folders. Dropping multiple objects is rejected with an
explanation; unsupported or corrupt sources leave the current book visible.

Open starts in the last successfully opened directory, including after a restart.
A deleted or unreadable directory falls back to the home folder. Cancelled dialogs
and failed opens do not change the saved directory; application reset clears it.

A local SQLite database stores theme, scale and last-directory preferences, plus the last displayed
page, page count, reading direction and spread mode for each book. A book is identified
by the absolute normalized path of its folder, PDF or ZIP/CBZ archive. Reopening it resumes
reading; explicitly opening an image always selects that image. Page names are used
before numeric positions, so inserting earlier pages does not shift the bookmark.
If the saved page was removed, its old position is clamped to the remaining pages.
Moving or renaming a book creates a new history entry.

Storage locations:

- macOS: `~/Library/Application Support/YonakaReader/reader.sqlite3`
- Windows: `%APPDATA%/YonakaReader/reader.sqlite3` (fallback: `~/AppData/Roaming/YonakaReader/reader.sqlite3`)
- Linux: `$XDG_DATA_HOME/yonaka-reader/reader.sqlite3` (fallback: `~/.local/share/yonaka-reader/reader.sqlite3`)

Custom translation files live in the active data folder under `translations/`. When switching data folders, use translation export and import to bring a catalog to the new folder.

For portable storage, override the directory before `-jar`:

```bash
java -Dyonaka.dataDir=./reader-data -jar build/libs/yonaka-reader-0.23.1.jar
```

### Reset and compatibility

Use **Help → Reset comic database…** to clear settings, library membership, completion marks and reading history. The
confirmation defaults to Cancel. After a successful reset the app closes; the next
launch uses defaults. Image files, PDFs, folders and ZIP/CBZ archives are never deleted.
Reset is transactional: if clearing any table fails, all retain their data.
Queued saves are drained before reset, and late loading callbacks cannot repopulate it.
This is an application reset, not secure erasure of SQLite pages. No automatic database copies are created.

The data directory and database filename do not include the application version.
Application releases such as 0.8 and 0.9 share the same storage location. The database
schema version is independent: an unchanged schema is opened without running DDL.
Schema upgrades use ordered transactional migrations without automatic backups.
Failures roll back; migrations never trigger an automatic wipe or database recreation.
A newer, unsupported schema is rejected without changing its contents; the app can
still read books without saving progress. **Help → Reset comic database…** remains
available when initialization fails: after explicit confirmation it clears application
data transactionally, allowing a fresh startup. If SQLite cannot read a corrupt database,
reset is refused rather than deleting an unreadable file blindly.


The database uses reader schema version 2 and WAL journaling; SQLite may create adjacent
`-wal` and `-shm` files. Settings and progress are written on the background worker,
and queued saves finish before normal application exit. A database failure is reported
without preventing reading. No book images or archive contents are stored in the database.

## Switching profiles

Profile selection is stored in `profile.path` in the standard data directory. Moving a
profile closes pending writes and SQLite first. A small `profile-switch.properties`
metadata journal permits completing an interrupted move; it is not a database backup.
A missing selected profile produces an error instead of silently creating an empty one.
The explicit `yonaka.dataDir` launch option takes precedence and disables the folder switch.

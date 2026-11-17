# Interface translations

Choose a language in **Options → Settings → Appearance → Language**, then click **Apply** or **OK**.
Menus, controls, tooltips and library views update immediately. The choice is saved with your settings.

English, Russian and Japanese are included. Missing messages fall back to English.
Book titles and filesystem paths retain their original spelling.

## Add a language

1. Copy `src/main/resources/i18n/en.json` to a new file in the same directory, such as `de.json`.
2. Set `id` to a unique language tag, such as `de` or `pt-BR`. Set `name` to the language's own display name, such as `Deutsch`.
3. Translate the values inside `messages`. Keep the keys unchanged. Save the file as UTF-8 JSON.
4. Run `./gradlew clean test fatJar`, launch the application and select the new language. Check both Ayu themes, menus, Settings, recent books, library cards and dialogs.
5. Submit the JSON file in a pull request.

Gradle generates the language index from the JSON files. The packaged application uses the same index.
Adding a language requires no Java changes or manual registration.

## Formatting

Messages use indexed Java format placeholders.

```json
{
  "id": "de",
  "name": "Deutsch",
  "messages": {
    "reading.progress": "Seite %1$d von %2$d"
  }
}
```

In this message, `%1$d` is the current page and `%2$d` is the total.
You can change their order while keeping their indexes and types.
`%1$s` is a text value, such as a book title or a filename.
Write `%%` for a literal percent sign and `\n` for a line break.
Apostrophes need no special escaping beyond normal JSON syntax.

Do not translate shortcut key names, JSON keys, file extensions or language IDs.
Keep Yonaka Reader and Ayu as product names.

A partial translation may omit message keys. A malformed file is reported in the log and omitted from the language list.
An invalid format falls back to the English message at runtime.

## Maintain translations

New interface text needs a stable, descriptive key in `en.json` and matching entries in the Russian and Japanese catalogs.
Keep runtime values in indexed placeholders so translators can reorder them.

Use `I18n.text(key, arguments)` for messages created when an operation runs.
For an existing Swing widget, use the `UiText` helpers or register an update with `UiText.bind`.
Bindings use weak references and run on the Swing event dispatch thread.
The update receives the widget as its argument. Avoid capturing that widget in the callback.

Tests check that the bundled translations cover the English keys, retain placeholder indexes and types, and format correctly.
They also check live widget updates, language persistence and locale-independent data paths.

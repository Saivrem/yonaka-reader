package org.dustyroom.be.io;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import lombok.experimental.UtilityClass;
import org.dustyroom.be.models.i18n.Language;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Map;

@UtilityClass
public class TranslationJson {
    // Gson also accepts maps encoded as arrays of pairs. Catalogs require a JSON object.
    private static final class Messages implements JsonDeserializer<Map<String, String>> {
        @Override
        public Map<String, String> deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            return context.deserialize(json.getAsJsonObject(), type);
        }
    }

    private record Catalog(String id, String name, @JsonAdapter(Messages.class) Map<String, String> messages) {
    }

    private static final TypeToken<Catalog> CATALOG = TypeToken.get(Catalog.class);

    public static Language read(Reader input) {
        Catalog catalog = JsonSerde.read(input, CATALOG);
        if (catalog == null || catalog.id() == null || catalog.id().isBlank()
                || catalog.name() == null || catalog.name().isBlank() || catalog.messages() == null
                || catalog.messages().containsValue(null)) {
            throw new IllegalArgumentException("Expected a translation with an id, name and messages");
        }
        return new Language(catalog.id(), catalog.name(), catalog.messages());
    }

    public static void write(Writer output, Language language) throws IOException {
        JsonSerde.write(output, new Catalog(language.getId(), language.getName(), language.getMessages()));
        output.write('\n');
    }
}

package org.dustyroom.be.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

@UtilityClass
public class JsonSerde {
    private static final JsonDeserializer<String> STRING = (value, type, context) -> {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected a JSON string");
        }
        return value.getAsString();
    };
    private static final Gson MAPPER = new GsonBuilder()
            .registerTypeAdapter(String.class, STRING)
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public static <T> T read(Reader input, TypeToken<T> type) {
        return MAPPER.fromJson(input, type);
    }

    public static void write(Writer output, Object value) throws IOException {
        output.write(MAPPER.toJson(value));
    }
}

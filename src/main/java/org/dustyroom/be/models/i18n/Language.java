package org.dustyroom.be.models.i18n;

import lombok.Value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Value
public class Language {
    String id;
    String name;
    Map<String, String> messages;

    public Language(String id, String name, Map<String, String> messages) {
        this.id = id;
        this.name = name;
        this.messages = Collections.unmodifiableMap(new LinkedHashMap<>(messages));
    }

    @Override
    public String toString() {
        return name;
    }
}

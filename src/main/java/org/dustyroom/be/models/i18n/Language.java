package org.dustyroom.be.models.i18n;

import lombok.Value;

import java.util.Map;

@Value
public class Language {
    String id;
    String name;
    Map<String, String> messages;

    @Override
    public String toString() {
        return name;
    }
}

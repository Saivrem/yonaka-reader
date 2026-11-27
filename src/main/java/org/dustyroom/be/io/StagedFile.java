package org.dustyroom.be.io;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

@UtilityClass
public class StagedFile {
    @FunctionalInterface
    public interface Content {
        void write(Writer output) throws IOException;
    }

    public static void write(Path target, Content content, CopyOption... moveOptions) throws IOException {
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".yonaka-", ".tmp");
        try {
            try (Writer output = Files.newBufferedWriter(temporary)) {
                content.write(output);
            }
            Files.move(temporary, target, moveOptions);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

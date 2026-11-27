package org.dustyroom.be.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StagedFileTest {
    @Test
    void failedWritePreservesTargetAndRemovesTemporaryFile(@TempDir Path directory) throws IOException {
        Path target = Files.writeString(directory.resolve("catalog"), "previous");
        assertThrows(IOException.class, () -> StagedFile.write(target, output -> {
            output.write("partial");
            throw new IOException("write failed");
        }, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));

        assertEquals("previous", Files.readString(target));
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(target), files.toList());
        }
    }
}

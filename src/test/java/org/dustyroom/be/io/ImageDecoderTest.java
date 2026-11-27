package org.dustyroom.be.io;

import org.dustyroom.be.iterators.ImageTestFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ImageDecoderTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void streamCachePreservesPixelsWhenReaderSeeks(boolean conflictingMarkers) throws IOException {
        byte[] data = conflictingMarkers ? ImageTestFixtures.jpegWithConflictingColorMarkers() : ImageTestFixtures.pngBytes();
        int[] expected = decodePixels(data, -1);

        assertArrayEquals(expected, decodePixels(data, data.length));
    }

    private static int[] decodePixels(byte[] data, long reportedSize) throws IOException {
        try (ImageInputStream input = ImageDecoder.openStream(new ByteArrayInputStream(data), reportedSize)) {
            BufferedImage image = ImageDecoder.read(input);
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        }
    }
}

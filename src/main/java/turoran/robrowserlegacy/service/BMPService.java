package turoran.robrowserlegacy.service;

import jakarta.inject.Singleton;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Singleton
public class BMPService {

    /**
     * Creates a {@link BufferedImage} from a raw BMP byte array.
     *
     * @param buffer the byte array containing BMP data
     * @return a {@link BufferedImage} decoded from the BMP data
     * @throws IllegalArgumentException if the buffer is invalid or the BMP format is unsupported
     */
    public BufferedImage createImageFromBmp(byte[] buffer) {
        if (buffer == null || buffer.length < 54) {
            throw new IllegalArgumentException("Invalid BMP file");
        }

        // Verify BMP signature ("BM")
        if (buffer[0] != 'B' || buffer[1] != 'M') {
            throw new IllegalArgumentException("Not a BMP file");
        }

        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        // Read BMP header fields
        // fileSize at offset 2 (informational, not strictly needed)
        int dataOffset   = bb.getInt(10);
        // headerSize at offset 14 (informational)
        int width        = bb.getInt(18);
        int height       = bb.getInt(22);
        short planes     = bb.getShort(26);
        short bitsPerPixel = bb.getShort(28);

        if (planes != 1 || bitsPerPixel != 24) {
            throw new IllegalArgumentException(
                    "Unsupported BMP format: Only 24-bit BMP files are supported");
        }

        // BMP rows are padded to a multiple of 4 bytes
        int rowSize = ((bitsPerPixel * width + 31) / 32) * 4;

        // BMP stores rows bottom-to-top; we flip them to top-to-bottom
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            // BMP row 0 is the bottom row of the image
            int bmpRow = height - 1 - y;
            int rowOffset = dataOffset + bmpRow * rowSize;

            for (int x = 0; x < width; x++) {
                int bufferIndex = rowOffset + x * 3;

                // BMP stores colours in BGR order
                int blue  = buffer[bufferIndex]     & 0xFF;
                int green = buffer[bufferIndex + 1] & 0xFF;
                int red   = buffer[bufferIndex + 2] & 0xFF;
                int alpha = 255;

                int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                image.setRGB(x, y, argb);
            }
        }

        return image;
    }
}

package turoran.grfloader.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DESDecoderTest {

    @Test
    public void testDecodeHeader() {
        // Since we don't have a known ciphertext/plaintext pair, we'll just check if it runs without error
        // and produces some output. In a real scenario, we'd use a known pair.
        byte[] data = new byte[160]; // 20 blocks
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        byte[] original = data.clone();
        
        DESDecoder.decodeHeader(data, data.length);
        
        // It should have changed the data
        assertFalse(java.util.Arrays.equals(original, data));
    }

    @Test
    public void testDecodeFull() {
        byte[] data = new byte[400]; // 50 blocks
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        byte[] original = data.clone();

        DESDecoder.decodeFull(data, data.length, 123);

        assertFalse(java.util.Arrays.equals(original, data));
    }
}

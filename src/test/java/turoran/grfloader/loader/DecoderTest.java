package turoran.grfloader.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

public class DecoderTest {

    @Test
    public void testDecodeBytes() {
        String original = "안녕하세요"; // "Hello" in Korean
        byte[] bytes = original.getBytes(Decoder.CP949);
        
        String decoded = Decoder.decodeBytes(bytes, "cp949");
        assertEquals(original, decoded);
        
        String decodedEucKr = Decoder.decodeBytes(bytes, "euc-kr");
        assertEquals(original, decodedEucKr);
    }

    @Test
    public void testMojibakeDetectionAndFixing() {
        String original = "유저인터페이스";
        String mojibake = Decoder.toMojibake(original);
        
        // Example output for "유저인터페이스" is "À¯ÀúÀÎÅÍÆäÀÌ½º"
        assertTrue(Decoder.isMojibake(mojibake), "Should detect mojibake: " + mojibake);
        assertFalse(Decoder.isMojibake(original), "Should not detect original as mojibake");
        
        String fixed = Decoder.fixMojibake(mojibake);
        assertEquals(original, fixed);
    }

    @Test
    public void testNormalizePath() {
        String original = "data\\유저인터페이스\\test.txt";
        String mojibakePath = "data\\" + Decoder.toMojibake("유저인터페이스") + "\\test.txt";
        
        String normalized = Decoder.normalizePath(mojibakePath);
        assertEquals(original, normalized);
    }

    @Test
    public void testEncodingDetection() {
        String korean = "안녕하세요";
        byte[] cp949Bytes = korean.getBytes(Decoder.CP949);
        byte[] utf8Bytes = korean.getBytes(StandardCharsets.UTF_8);
        
        assertEquals("cp949", Decoder.detectBestKoreanEncoding(new byte[][]{cp949Bytes}));
        assertEquals("utf-8", Decoder.detectBestKoreanEncoding(new byte[][]{utf8Bytes}));
        
        // ASCII should return utf-8
        assertEquals("utf-8", Decoder.detectBestKoreanEncoding(new byte[][]{"Hello".getBytes(StandardCharsets.US_ASCII)}));
    }
}

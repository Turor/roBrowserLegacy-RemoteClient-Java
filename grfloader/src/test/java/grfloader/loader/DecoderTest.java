package turoran.robrowser.grfloader.loader;

import org.junit.jupiter.api.Test;
import turoran.robrowser.grfloader.loader.Decoder;

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

    @Test
    public void testKoreanToMojibakeAndBack() {
        String[][] testCases = {
                {"유저인터페이스", "User Interface"},
                {"아이템", "Item"},
                {"스프라이트", "Sprite"},
                {"몬스터", "Monster"},
                {"데이터", "Data"},
                {"망토", "Mantle/Cape"},
                {"카드", "Card"}
        };

        for (String[] testCase : testCases) {
            String korean = testCase[0];
            String description = testCase[1];

            String mojibake = Decoder.toMojibake(korean);
            String fixed = Decoder.fixMojibake(mojibake);
            boolean detected = Decoder.isMojibake(mojibake);

            System.out.println(description + ":");
            System.out.println("  Korean:   " + korean);
            System.out.println("  Mojibake: " + mojibake);
            System.out.println("  Detected: " + detected);
            System.out.println("  Fixed:    " + fixed);

            assertEquals(korean, fixed, "Failed for " + description);
            assertTrue(detected, "Should detect mojibake for " + description);
        }
    }

    @Test
    public void testPathNormalizationExtended() {
        String[] testPaths = {
                "data\\texture\\À¯ÀúÀÎÅÍÆäÀÌ½º\\cardbmp\\test.bmp",
                "data\\sprite\\¾ÆÀÌÅÛ\\monster.spr",
                "data\\texture\\normal\\test.bmp",
                "data/texture/À¯ÀúÀÎÅÍÆäÀÌ½º/cardbmp/test.bmp"
        };

        String[] expectedPaths = {
                "data\\texture\\유저인터페이스\\cardbmp\\test.bmp",
                "data\\sprite\\아이템\\monster.spr",
                "data\\texture\\normal\\test.bmp",
                "data/texture/유저인터페이스/cardbmp/test.bmp"
        };

        for (int i = 0; i < testPaths.length; i++) {
            String normalized = Decoder.normalizePath(testPaths[i]);
            assertEquals(expectedPaths[i], normalized);
        }
    }

    @Test
    public void testDetectionExtended() {
        Object[][] detectionTests = {
                {"À¯ÀúÀÎÅÍÆäÀÌ½º", true, "Mojibake Korean"},
                {"유저인터페이스", false, "Proper Korean"},
                {"normal_filename.txt", false, "ASCII filename"},
                {"test_ÀÌ¹ÌÁö.bmp", true, "Mixed mojibake"},
                {"données.txt", false, "French accents"}
        };

        for (Object[] test : detectionTests) {
            String str = (String) test[0];
            boolean expected = (boolean) test[1];
            String desc = (String) test[2];

            boolean detected = Decoder.isMojibake(str);
            assertEquals(expected, detected, "Failed for " + desc);
        }
    }

    @Test
    public void testRepairFilename() {
        String mojibake = "data\\texture\\\u00C0\u00AF\u00C0\u00FA\u00C0\u00CE\u00C5\u00CD\u00C6\u00E4\u00C0\u00CC\u00BD\u00BA\\test.bmp";
        String expected = "data\\texture\\유저인터페이스\\test.bmp";
        assertEquals(expected, Decoder.repairFilename(mojibake, Decoder.CP949));
    }
}

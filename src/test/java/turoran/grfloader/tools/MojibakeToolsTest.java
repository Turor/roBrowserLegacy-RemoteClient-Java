package turoran.grfloader.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MojibakeToolsTest {

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
            
            String mojibake = MojibakeTools.toMojibake(korean);
            String fixed = MojibakeTools.fixMojibake(mojibake);
            boolean detected = MojibakeTools.isMojibake(mojibake);

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
    public void testPathNormalization() {
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
            String normalized = MojibakeTools.normalizeEncodingPath(testPaths[i]);
            assertEquals(expectedPaths[i], normalized);
        }
    }

    @Test
    public void testDetection() {
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

            boolean detected = MojibakeTools.isMojibake(str);
            assertEquals(expected, detected, "Failed for " + desc);
        }
    }
}

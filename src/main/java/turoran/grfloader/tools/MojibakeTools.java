package turoran.grfloader.tools;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/tools/test-mojibake.mjs">JS Mojibake Tools</a>
 */
public class MojibakeTools {

    private static final Charset EUC_KR = Charset.forName("windows-949");
    private static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

    public static boolean hasC1Controls(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x80 && c <= 0x9F) return true;
        }
        return false;
    }

    public static int countC1Controls(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x80 && c <= 0x9F) n++;
        }
        return n;
    }

    public static int countHangul(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) n++;
        }
        return n;
    }

    public static int countReplacement(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\uFFFD') n++;
        }
        return n;
    }

    public static String maybeFixLatin1Mojibake(String s, Charset charset) {
        if (s == null) return null;
        boolean hasHigh = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x00A0 && c <= 0x00FF) {
                hasHigh = true;
                break;
            }
        }
        if (!hasHigh) return s;

        int beforeHangul = countHangul(s);
        int beforeC1 = countC1Controls(s);
        int beforeRep = countReplacement(s);

        byte[] bytes = s.getBytes(ISO_8859_1);
        String fixed = new String(bytes, charset);

        int afterHangul = countHangul(fixed);
        int afterC1 = countC1Controls(fixed);
        int afterRep = countReplacement(fixed);

        boolean improved = afterRep <= beforeRep &&
                afterC1 <= beforeC1 &&
                afterHangul >= (beforeHangul + 2);

        return improved ? fixed : s;
    }

    public static String fixC1PrefixInSegment(String seg, Charset charset) {
        if (!hasC1Controls(seg)) return seg;

        int i = 0;
        for (; i < seg.length(); i++) {
            if (seg.charAt(i) > 0xFF) break;
        }
        if (i == 0) return seg;

        byte[] bytes = seg.substring(0, i).getBytes(ISO_8859_1);
        String decodedPrefix = new String(bytes, charset);
        String merged = decodedPrefix + seg.substring(i);

        int beforeC1 = countC1Controls(seg);
        int afterC1 = countC1Controls(merged);
        int beforeRep = countReplacement(seg);
        int afterRep = countReplacement(merged);

        if (afterC1 < beforeC1 && afterRep <= beforeRep) return merged;
        return seg;
    }

    public static String repairFilename(String filename, Charset charset) {
        if (filename == null) return null;
        String s = maybeFixLatin1Mojibake(filename, charset);

        String[] parts = s.split("(?<=[\\\\/])|(?=[\\\\/])");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.equals("\\") || p.equals("/")) {
                sb.append(p);
            } else {
                sb.append(fixC1PrefixInSegment(p, charset));
            }
        }
        return sb.toString();
    }

    // Pattern to detect potential mojibake: strings containing many characters in the range 0x80-0xFF
    // typical of Latin-1 misinterpretation of multibyte encodings like EUC-KR.
    // In EUC-KR, Korean characters are in the range [0xA1-0xFE][0xA1-0xFE].
    // When interpreted as Latin-1, these become pairs of characters in the range 0xA1-0xFE (¡ to þ).
    private static final Pattern MOJIBAKE_PATTERN = Pattern.compile("[\\u0080-\\u00FF]{2,}");

    public static boolean isMojibake(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Check for common mojibake patterns
        // We look for sequences of high-ASCII characters that are typical in mojibake.
        // Also check if the string contains any actual Korean characters - if it does, it might not be mojibake
        // or it might be mixed. The JS implementation seems to return true if it looks like mojibake.
        
        // A simple heuristic: if it contains characters in the range 0x80-0xFF but no actual CJK characters.
        boolean hasHighAscii = false;
        int highAsciiCount = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 0x80 && c <= 0xFF) {
                hasHighAscii = true;
                highAsciiCount++;
            }
            // If we see actual Korean characters, it's probably not (pure) mojibake
            if (c >= 0xAC00 && c <= 0xD7A3) {
                return false;
            }
        }

        return hasHighAscii && highAsciiCount >= 2;
    }

    public static String fixMojibake(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Treat the input string as ISO-8859-1 and convert back to EUC-KR bytes, then decode as EUC-KR
        byte[] bytes = input.getBytes(ISO_8859_1);
        return new String(bytes, EUC_KR);
    }

    public static String toMojibake(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Encode as EUC-KR and then interpret those bytes as ISO-8859-1
        byte[] bytes = input.getBytes(EUC_KR);
        return new String(bytes, ISO_8859_1);
    }

    public static String normalizeEncodingPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // Split by both \ and / to handle different path styles
        String[] parts = path.split("(?<=[\\\\/])|(?=[\\\\/])");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            if (!part.equals("\\") && !part.equals("/") && isMojibake(part)) {
                sb.append(fixMojibake(part));
            } else {
                sb.append(part);
            }
        }

        return sb.toString();
    }
}

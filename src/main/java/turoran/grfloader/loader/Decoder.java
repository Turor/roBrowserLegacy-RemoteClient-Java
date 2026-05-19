package turoran.grfloader.loader;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Korean encoding decoder module
 * Ported from TypeScript implementation.
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/src/decoder.ts">Decoder JS</a>
 */
public class Decoder {

    public static final Charset CP949 = Charset.forName("x-windows-949");
    public static final Charset EUC_KR = Charset.forName("EUC-KR");
    public static final Charset MS1252 = Charset.forName("windows-1252");

    /**
     * Count C1 control characters (U+0080-U+009F) in a string.
     * These usually indicate incorrectly decoded Korean bytes.
     */
    public static int countC1ControlChars(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 0x80 && c <= 0x9F) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count replacement characters (U+FFFD) in a string
     */
    public static int countReplacementChars(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\uFFFD') count++;
        }
        return count;
    }

    /**
     * Count total "bad" characters (replacement + C1 control)
     */
    public static int countBadChars(String str) {
        return countReplacementChars(str) + countC1ControlChars(str);
    }

    /**
     * Decode bytes to string using the specified encoding.
     */
    public static String decodeBytes(byte[] bytes, String encoding) {
        String enc = encoding.toLowerCase();
        Charset charset;

        try {
            switch (enc) {
                case "utf-8":
                case "utf8":
                    charset = StandardCharsets.UTF_8;
                    break;
                case "cp949":
                case "ms949":
                case "x-windows-949":
                    charset = CP949;
                    break;
                case "euc-kr":
                case "euckr":
                    // Use CP949 as it's a superset of EUC-KR and handles more Korean chars
                    charset = CP949;
                    break;
                case "latin1":
                case "iso-8859-1":
                    charset = StandardCharsets.ISO_8859_1;
                    break;
                default:
                    charset = Charset.forName(encoding);
                    break;
            }
            return new String(bytes, charset);
        } catch (Exception e) {
            // Ultimate fallback: decode as ISO-8859-1 (preserves all byte values)
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    public static class DecodeResult {
        public final String text;
        public final int badChars;
        public final int c1Chars;
        public final int replacementChars;

        public DecodeResult(String text, int badChars, int c1Chars, int replacementChars) {
            this.text = text;
            this.badChars = badChars;
            this.c1Chars = c1Chars;
            this.replacementChars = replacementChars;
        }
    }

    /**
     * Try to decode bytes and check quality of the result
     */
    public static DecodeResult tryDecodeWithQuality(byte[] bytes, String encoding) {
        String text = decodeBytes(bytes, encoding);
        int c1Chars = countC1ControlChars(text);
        int replacementChars = countReplacementChars(text);
        int badChars = c1Chars + replacementChars;

        return new DecodeResult(text, badChars, c1Chars, replacementChars);
    }

    // ============================================================================
    // Mojibake Detection and Fixing
    // ============================================================================

    private static final Pattern[] MOJIBAKE_PATTERNS = {
            Pattern.compile("[ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞß][¡-þ]"),
            Pattern.compile("À¯"),
            Pattern.compile("Àú"),
            Pattern.compile("ÀÎ"),
            Pattern.compile("Å¸"),
            Pattern.compile("Æä"),
            Pattern.compile("ÀÌ"),
            Pattern.compile("½º"),
            Pattern.compile("¾Æ"),
            Pattern.compile("¸ð"),
            Pattern.compile("¸®"),
            Pattern.compile("¿¡"),
            Pattern.compile("Áö"),
            Pattern.compile("µ¥"),
            Pattern.compile("ÅØ"),
            Pattern.compile("½ºÆ®"),
            Pattern.compile("¸ÁÅä")
    };

    private static final Pattern KOREAN_PATTERN = Pattern.compile("[\uAC00-\uD7AF]");

    /**
     * Check if a string looks like mojibake (CP949 bytes misread as Windows-1252).
     */
    public static boolean isMojibake(String str) {
        if (str == null || str.isEmpty()) return false;

        // If string contains Korean characters, it's not mojibake
        if (KOREAN_PATTERN.matcher(str).find()) return false;

        // Check for common mojibake patterns
        for (Pattern pattern : MOJIBAKE_PATTERNS) {
            if (pattern.matcher(str).find()) return true;
        }

        // Check for high concentration of Latin Extended characters (0x80-0xFF)
        int highLatinCount = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 0x80 && c <= 0xFF) {
                highLatinCount++;
            }
        }

        double ratio = (double) highLatinCount / str.length();
        return ratio > 0.3;
    }

    /**
     * Fix mojibake by re-encoding as Windows-1252 and decoding as CP949.
     */
    public static String fixMojibake(String garbled) {
        try {
            // Encode the garbled string back to Windows-1252 bytes
            byte[] bytes = garbled.getBytes(MS1252);
            // Decode those bytes as CP949 to get the original Korean
            String fixed = new String(bytes, CP949);

            // Verify the fix worked
            boolean hasKorean = KOREAN_PATTERN.matcher(fixed).find();
            int fixedBadChars = countBadChars(fixed);
            int garbledBadChars = countBadChars(garbled);

            if (hasKorean && fixedBadChars <= garbledBadChars) {
                return fixed;
            }

            return garbled;
        } catch (Exception e) {
            return garbled;
        }
    }

    /**
     * Convert Korean text to mojibake (for testing purposes).
     */
    public static String toMojibake(String korean) {
        try {
            byte[] bytes = korean.getBytes(CP949);
            return new String(bytes, MS1252);
        } catch (Exception e) {
            return korean;
        }
    }

    /**
     * Normalize a filename by detecting and fixing encoding issues.
     */
    public static String normalizeFilename(String filename) {
        if (isMojibake(filename)) {
            return fixMojibake(filename);
        }
        return filename;
    }

    /**
     * Normalize a path by fixing mojibake in each segment.
     */
    public static String normalizePath(String filepath) {
        String separator = filepath.contains("\\") ? "\\\\" : "/";
        String[] segments = filepath.split(separator);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            result.append(normalizeFilename(segments[i]));
            if (i < segments.length - 1) {
                result.append(filepath.contains("\\") ? "\\" : "/");
            }
        }
        return result.toString();
    }

    // ============================================================================
    // Encoding Detection
    // ============================================================================

    /**
     * Detect the best encoding for Korean GRF files by analyzing byte patterns.
     */
    public static String detectBestKoreanEncoding(byte[][] sampleBytes) {
        return detectBestKoreanEncoding(sampleBytes, 0.01);
    }

    public static String detectBestKoreanEncoding(byte[][] sampleBytes, double threshold) {
        if (sampleBytes == null || sampleBytes.length == 0) return "utf-8";

        int utf8BadTotal = 0;
        int cp949BadTotal = 0;
        long totalBytes = 0;
        int samplesWithHighBytes = 0;

        for (byte[] bytes : sampleBytes) {
            boolean hasHighBytes = false;
            for (byte b : bytes) {
                if ((b & 0xFF) > 0x7F) {
                    hasHighBytes = true;
                    break;
                }
            }
            if (!hasHighBytes) continue;

            samplesWithHighBytes++;
            totalBytes += bytes.length;

            DecodeResult utf8Result = tryDecodeWithQuality(bytes, "utf-8");
            DecodeResult cp949Result = tryDecodeWithQuality(bytes, "cp949");

            utf8BadTotal += utf8Result.badChars;
            cp949BadTotal += cp949Result.badChars;
        }

        if (samplesWithHighBytes == 0) {
            return "utf-8";
        }

        double utf8BadRatio = totalBytes > 0 ? (double) utf8BadTotal / totalBytes : 0;
        double cp949BadRatio = totalBytes > 0 ? (double) cp949BadTotal / totalBytes : 0;

        if (utf8BadRatio < threshold) {
            return "utf-8";
        }

        if (cp949BadRatio < utf8BadRatio) {
            return "cp949";
        }

        return "utf-8";
    }
}

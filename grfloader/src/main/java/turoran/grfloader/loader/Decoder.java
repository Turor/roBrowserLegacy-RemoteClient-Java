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
    public static final Charset EUC_KR = Charset.forName("windows-949");
    public static final Charset MS1252 = Charset.forName("windows-1252");
    public static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

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
     * Count Hangul characters (U+AC00-U+D7A3) in a string
     */
    public static int countHangul(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) n++;
        }
        return n;
    }

    /**
     * Decode bytes to string using the specified encoding.
     */
    public static String decodeBytes(byte[] bytes, String encoding) {
        String enc = encoding.toLowerCase();
        Charset charset;

        try {
            charset = switch (enc) {
                case "utf-8", "utf8" -> StandardCharsets.UTF_8;
                case "cp949", "ms949", "x-windows-949" -> CP949;
                case "euc-kr", "euckr" ->
                    // Use CP949 as it's a superset of EUC-KR and handles more Korean chars
                        CP949;
                case "latin1", "iso-8859-1" -> StandardCharsets.ISO_8859_1;
                default -> Charset.forName(encoding);
            };
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

    private static final Pattern KOREAN_PATTERN = Pattern.compile("[가-\uD7AF]");

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
        return fixMojibake(garbled, CP949);
    }

    /**
     * Fix mojibake by re-encoding as Windows-1252 and decoding as the specified charset.
     */
    public static String fixMojibake(String garbled, Charset charset) {
        if (garbled == null || garbled.isEmpty()) return garbled;
        try {
            // Encode the garbled string back to Windows-1252 bytes
            byte[] bytes = garbled.getBytes(MS1252);
            // Decode those bytes as specified charset (usually CP949) to get the original Korean
            String fixed = new String(bytes, charset);

            // Verify the fix worked
            boolean hasKorean = KOREAN_PATTERN.matcher(fixed).find();
            int fixedBadChars = countBadChars(fixed);
            int garbledBadChars = countBadChars(garbled);

            // Additional check from MojibakeTools: check if Hangul count improved significantly
            int beforeHangul = countHangul(garbled);
            int afterHangul = countHangul(fixed);

            if ((hasKorean || afterHangul > beforeHangul) && fixedBadChars <= garbledBadChars) {
                return fixed;
            }

            return garbled;
        } catch (Exception e) {
            return garbled;
        }
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
        int beforeC1 = countC1ControlChars(s);
        int beforeRep = countReplacementChars(s);

        byte[] bytes = s.getBytes(ISO_8859_1);
        String fixed = new String(bytes, charset);

        int afterHangul = countHangul(fixed);
        int afterC1 = countC1ControlChars(fixed);
        int afterRep = countReplacementChars(fixed);

        boolean improved = afterRep <= beforeRep &&
                afterC1 <= beforeC1 &&
                afterHangul >= (beforeHangul + 2);

        return improved ? fixed : s;
    }

    public static String fixC1PrefixInSegment(String seg, Charset charset) {
        int c1Count = countC1ControlChars(seg);
        if (c1Count == 0) return seg;

        int i = 0;
        for (; i < seg.length(); i++) {
            if (seg.charAt(i) > 0xFF) break;
        }
        if (i == 0) return seg;

        byte[] bytes = seg.substring(0, i).getBytes(ISO_8859_1);
        String decodedPrefix = new String(bytes, charset);
        String merged = decodedPrefix + seg.substring(i);

        int beforeC1 = countC1ControlChars(seg);
        int afterC1 = countC1ControlChars(merged);
        int beforeRep = countReplacementChars(seg);
        int afterRep = countReplacementChars(merged);

        if (afterC1 < beforeC1 && afterRep <= beforeRep) return merged;
        return seg;
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
        return normalizeFilename(filename, CP949);
    }

    public static String normalizeFilename(String filename, Charset charset) {
        if (isMojibake(filename)) {
            return fixMojibake(filename, charset);
        }
        return filename;
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

    /**
     * Normalize a path by fixing mojibake in each segment.
     */
    public static String normalizePath(String filepath) {
        return normalizePath(filepath, CP949);
    }

    public static String normalizePath(String filepath, Charset charset) {
        String separator = filepath.contains("\\") ? "\\\\" : "/";
        String[] segments = filepath.split(separator);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            result.append(normalizeFilename(segments[i], charset));
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

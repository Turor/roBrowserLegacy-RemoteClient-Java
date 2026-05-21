package turoran.robrowser.grfloader.loader;

import java.util.Arrays;

/**
 * Ragnarok Online DES decoder implementation
 * Ported from JavaScript
 * <a href="https://github.com/FranciscoWallison/grf-loader/blob/main/src/des.ts">DESDecoder JS</a>
 */
public class DESDecoder {

    private static final byte[] mask = {(byte) 0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01};

    private static final byte[] initialPermutationTable = {
            58, 50, 42, 34, 26, 18, 10, 2,
            60, 52, 44, 36, 28, 20, 12, 4,
            62, 54, 46, 38, 30, 22, 14, 6,
            64, 56, 48, 40, 32, 24, 16, 8,
            57, 49, 41, 33, 25, 17, 9, 1,
            59, 51, 43, 35, 27, 19, 11, 3,
            61, 53, 45, 37, 29, 21, 13, 5,
            63, 55, 47, 39, 31, 23, 15, 7
    };

    private static final byte[] finalPermutationTable = {
            40, 8, 48, 16, 56, 24, 64, 32,
            39, 7, 47, 15, 55, 23, 63, 31,
            38, 6, 46, 14, 54, 22, 62, 30,
            37, 5, 45, 13, 53, 21, 61, 29,
            36, 4, 44, 12, 52, 20, 60, 28,
            35, 3, 43, 11, 51, 19, 59, 27,
            34, 2, 42, 10, 50, 18, 58, 26,
            33, 1, 41, 9, 49, 17, 57, 25
    };

    private static final byte[] transpositionTable = {
            16, 7, 20, 21,
            29, 12, 28, 17,
            1, 15, 23, 26,
            5, 18, 31, 10,
            2, 8, 24, 14,
            32, 27, 3, 9,
            19, 13, 30, 6,
            22, 11, 4, 25
    };

    private static final byte[][] substitutionBoxTable = {
            {
                    (byte) 0xef, 0x03, 0x41, (byte) 0xfd, (byte) 0xd8, 0x74, 0x1e, 0x47, 0x26, (byte) 0xef, (byte) 0xfb, 0x22, (byte) 0xb3, (byte) 0xd8, (byte) 0x84, 0x1e,
                    0x39, (byte) 0xac, (byte) 0xa7, 0x60, 0x62, (byte) 0xc1, (byte) 0xcd, (byte) 0xba, 0x5c, (byte) 0x96, (byte) 0x90, 0x59, 0x05, 0x3b, 0x7a, (byte) 0x85,
                    0x40, (byte) 0xfd, 0x1e, (byte) 0xc8, (byte) 0xe7, (byte) 0x8a, (byte) 0x8b, 0x21, (byte) 0xda, 0x43, 0x64, (byte) 0x9f, 0x2d, 0x14, (byte) 0xb1, 0x72,
                    (byte) 0xf5, 0x5b, (byte) 0xc8, (byte) 0xb6, (byte) 0x9c, 0x37, 0x76, (byte) 0xec, 0x39, (byte) 0xa0, (byte) 0xa3, 0x05, 0x52, 0x6e, 0x0f, (byte) 0xd9
            },
            {
                    (byte) 0xa7, (byte) 0xdd, 0x0d, 0x78, (byte) 0x9e, 0x0b, (byte) 0xe3, (byte) 0x95, 0x60, 0x36, 0x36, 0x4f, (byte) 0xf9, 0x60, 0x5a, (byte) 0xa3,
                    0x11, 0x24, (byte) 0xd2, (byte) 0x87, (byte) 0xc8, 0x52, 0x75, (byte) 0xec, (byte) 0xbb, (byte) 0xc1, 0x4c, (byte) 0xba, 0x24, (byte) 0xfe, (byte) 0x8f, 0x19,
                    (byte) 0xda, 0x13, 0x66, (byte) 0xaf, 0x49, (byte) 0xd0, (byte) 0x90, 0x06, (byte) 0x8c, 0x6a, (byte) 0xfb, (byte) 0x91, 0x37, (byte) 0x8d, 0x0d, 0x78,
                    (byte) 0xbf, 0x49, 0x11, (byte) 0xf4, 0x23, (byte) 0xe5, (byte) 0xce, 0x3b, 0x55, (byte) 0xbc, (byte) 0xa2, 0x57, (byte) 0xe8, 0x22, 0x74, (byte) 0xce
            },
            {
                    0x2c, (byte) 0xea, (byte) 0xc1, (byte) 0xbf, 0x4a, 0x24, 0x1f, (byte) 0xc2, 0x79, 0x47, (byte) 0xa2, 0x7c, (byte) 0xb6, (byte) 0xd9, 0x68, 0x15,
                    (byte) 0x80, 0x56, 0x5d, 0x01, 0x33, (byte) 0xfd, (byte) 0xf4, (byte) 0xae, (byte) 0xde, 0x30, 0x07, (byte) 0x9b, (byte) 0xe5, (byte) 0x83, (byte) 0x9b, 0x68,
                    0x49, (byte) 0xb4, 0x2e, (byte) 0x83, 0x1f, (byte) 0xc2, (byte) 0xb5, 0x7c, (byte) 0xa2, 0x19, (byte) 0xd8, (byte) 0xe5, 0x7c, 0x2f, (byte) 0x83, (byte) 0xda,
                    (byte) 0xf7, 0x6b, (byte) 0x90, (byte) 0xfe, (byte) 0xc4, 0x01, 0x5a, (byte) 0x97, 0x61, (byte) 0xa6, 0x3d, 0x40, 0x0b, 0x58, (byte) 0xe6, 0x3d
            },
            {
                    0x4d, (byte) 0xd1, (byte) 0xb2, 0x0f, 0x28, (byte) 0xbd, (byte) 0xe4, 0x78, (byte) 0xf6, 0x4a, 0x0f, (byte) 0x93, (byte) 0x8b, 0x17, (byte) 0xd1, (byte) 0xa4,
                    0x3a, (byte) 0xec, (byte) 0xc9, 0x35, (byte) 0x93, 0x56, 0x7e, (byte) 0xcb, 0x55, 0x20, (byte) 0xa0, (byte) 0xfe, 0x6c, (byte) 0x89, 0x17, 0x62,
                    (byte) 0x17, 0x62, 0x4b, (byte) 0xb1, (byte) 0xb4, (byte) 0xde, (byte) 0xd1, (byte) 0x87, (byte) 0xc9, 0x14, 0x3c, 0x4a, 0x7e, (byte) 0xa8, (byte) 0xe2, 0x7d,
                    (byte) 0xa0, (byte) 0x9f, (byte) 0xf6, 0x5c, 0x6a, 0x09, (byte) 0x8d, (byte) 0xf0, 0x0f, (byte) 0xe3, 0x53, 0x25, (byte) 0x95, 0x36, 0x28, (byte) 0xcb
            }
    };

    private static final byte[] shuffleDecTable;

    static {
        byte[] list = {
                0x00, 0x2b, 0x6c, (byte) 0x80, 0x01, 0x68, 0x48,
                0x77, 0x60, (byte) 0xff, (byte) 0xb9, (byte) 0xc0, (byte) 0xfe, (byte) 0xeb
        };
        shuffleDecTable = new byte[256];
        for (int i = 0; i < 256; i++) {
            shuffleDecTable[i] = (byte) i;
        }
        for (int i = 0; i < list.length; i += 2) {
            shuffleDecTable[list[i] & 0xFF] = list[i + 1];
            shuffleDecTable[list[i + 1] & 0xFF] = list[i];
        }
    }

    private static void initialPermutation(byte[] src, int index, byte[] tmp) {
        Arrays.fill(tmp, (byte) 0);
        for (int i = 0; i < 64; ++i) {
            int j = (initialPermutationTable[i] & 0xFF) - 1;
            if ((src[index + ((j >> 3) & 7)] & (mask[j & 7] & 0xFF)) != 0) {
                tmp[(i >> 3) & 7] |= mask[i & 7];
            }
        }
        System.arraycopy(tmp, 0, src, index, 8);
    }

    private static void finalPermutation(byte[] src, int index, byte[] tmp) {
        Arrays.fill(tmp, (byte) 0);
        for (int i = 0; i < 64; ++i) {
            int j = (finalPermutationTable[i] & 0xFF) - 1;
            if ((src[index + ((j >> 3) & 7)] & (mask[j & 7] & 0xFF)) != 0) {
                tmp[(i >> 3) & 7] |= mask[i & 7];
            }
        }
        System.arraycopy(tmp, 0, src, index, 8);
    }

    private static void transposition(byte[] src, int index, byte[] tmp) {
        Arrays.fill(tmp, (byte) 0);
        for (int i = 0; i < 32; ++i) {
            int j = (transpositionTable[i] & 0xFF) - 1;
            if ((src[index + (j >> 3)] & (mask[j & 7] & 0xFF)) != 0) {
                tmp[(i >> 3) + 4] |= mask[i & 7];
            }
        }
        System.arraycopy(tmp, 0, src, index, 8);
    }

    private static void expansion(byte[] src, int index, byte[] tmp) {
        Arrays.fill(tmp, (byte) 0);
        tmp[0] = (byte) (((src[index + 7] << 5) | ((src[index + 4] & 0xFF) >> 3)) & 0x3f);
        tmp[1] = (byte) (((src[index + 4] << 1) | ((src[index + 5] & 0xFF) >> 7)) & 0x3f);
        tmp[2] = (byte) (((src[index + 4] << 5) | ((src[index + 5] & 0xFF) >> 3)) & 0x3f);
        tmp[3] = (byte) (((src[index + 5] << 1) | ((src[index + 6] & 0xFF) >> 7)) & 0x3f);
        tmp[4] = (byte) (((src[index + 5] << 5) | ((src[index + 6] & 0xFF) >> 3)) & 0x3f);
        tmp[5] = (byte) (((src[index + 6] << 1) | ((src[index + 7] & 0xFF) >> 7)) & 0x3f);
        tmp[6] = (byte) (((src[index + 6] << 5) | ((src[index + 7] & 0xFF) >> 3)) & 0x3f);
        tmp[7] = (byte) (((src[index + 7] << 1) | ((src[index + 4] & 0xFF) >> 7)) & 0x3f);
        System.arraycopy(tmp, 0, src, index, 8);
    }

    private static void substitutionBox(byte[] src, int index, byte[] tmp) {
        Arrays.fill(tmp, (byte) 0);
        for (int i = 0; i < 4; ++i) {
            tmp[i] = (byte) (
                    (substitutionBoxTable[i][src[i * 2 + 0 + index] & 0xFF] & 0xf0) |
                            (substitutionBoxTable[i][src[i * 2 + 1 + index] & 0xFF] & 0x0f)
            );
        }
        System.arraycopy(tmp, 0, src, index, 8);
    }

    private static void roundFunction(byte[] src, int index, byte[] tmp, byte[] tmp2) {
        System.arraycopy(src, index, tmp2, 0, 8);
        expansion(tmp2, 0, tmp);
        substitutionBox(tmp2, 0, tmp);
        transposition(tmp2, 0, tmp);

        src[index + 0] ^= tmp2[4];
        src[index + 1] ^= tmp2[5];
        src[index + 2] ^= tmp2[6];
        src[index + 3] ^= tmp2[7];
    }

    private static void decryptBlock(byte[] src, int index, byte[] tmp, byte[] tmp2) {
        initialPermutation(src, index, tmp);
        roundFunction(src, index, tmp, tmp2);
        finalPermutation(src, index, tmp);
    }

    private static void shuffleDec(byte[] src, int index, byte[] tmp) {
        tmp[0] = src[index + 3];
        tmp[1] = src[index + 4];
        tmp[2] = src[index + 6];
        tmp[3] = src[index + 0];
        tmp[4] = src[index + 1];
        tmp[5] = src[index + 2];
        tmp[6] = src[index + 5];
        tmp[7] = shuffleDecTable[src[index + 7] & 0xFF];

        System.arraycopy(tmp, 0, src, index, 8);
    }

    public static void decodeFull(byte[] src, int length, int entryLength) {
        byte[] tmp = new byte[8];
        byte[] tmp2 = new byte[8];

        String entryLengthStr = String.valueOf(entryLength);
        int digits = entryLengthStr.length();

        int cycle;
        if (digits < 3) {
            cycle = 1;
        } else if (digits < 5) {
            cycle = digits + 1;
        } else if (digits < 7) {
            cycle = digits + 9;
        } else {
            cycle = digits + 15;
        }

        int nblocks = length >> 3;

        for (int i = 0; i < 20 && i < nblocks; ++i) {
            decryptBlock(src, i * 8, tmp, tmp2);
        }

        for (int i = 20, j = -1; i < nblocks; ++i) {
            if (i % cycle == 0) {
                decryptBlock(src, i * 8, tmp, tmp2);
                continue;
            }

            if (++j > 0 && j % 7 == 0) {
                shuffleDec(src, i * 8, tmp);
            }
        }
    }

    public static void decodeHeader(byte[] src, int length) {
        byte[] tmp = new byte[8];
        byte[] tmp2 = new byte[8];
        int count = length >> 3;

        for (int i = 0; i < 20 && i < count; ++i) {
            decryptBlock(src, i * 8, tmp, tmp2);
        }
    }
}

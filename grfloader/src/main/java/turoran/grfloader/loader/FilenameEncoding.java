package turoran.grfloader.loader;

import lombok.Getter;

@Getter
public enum FilenameEncoding {
    UTF8("utf-8"),
    EUC_KR("euc-kr"),
    CP949("cp949"),
    LATIN1("latin1"),
    AUTO("auto");

    private final String value;

    FilenameEncoding(String value) {
        this.value = value;
    }

    public static FilenameEncoding fromString(String text) {
        for (FilenameEncoding b : FilenameEncoding.values()) {
            if (b.value.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return AUTO;
    }
}

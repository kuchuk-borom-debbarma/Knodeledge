package dev.kuku.knodeledge.services.rag.model;

public enum IndexStatus {
    PENDING("pending"),
    INDEXING("indexing"),
    READY("ready"),
    FAILED("failed");

    private final String value;

    IndexStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static IndexStatus fromValue(String value) {
        for (IndexStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown index status: " + value);
    }
}

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public record SnapshotConfig(String apiKey, String bucket, ZoneId zone, Clock clock) {
    public static SnapshotConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        String bucket = System.getenv().getOrDefault("SNAPSHOT_BUCKET", "creator-learning-snapshots");
        ZoneId zone = ZoneId.of(System.getenv().getOrDefault("SNAPSHOT_ZONE", "UTC"));
        return new SnapshotConfig(key, bucket, zone, Clock.system(zone));
    }

    public String objectKey() {
        return "nightly/" + LocalDate.now(clock) + "/creator-commerce.json";
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}

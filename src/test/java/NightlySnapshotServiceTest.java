import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class NightlySnapshotServiceTest {
    public static void main(String[] args) {
        Instant cutoff = Instant.parse("2026-09-04T01:00:00Z");
        SnapshotConfig config = new SnapshotConfig("test-key", "test-bucket", ZoneOffset.UTC, Clock.fixed(cutoff, ZoneOffset.UTC));
        NightlySnapshotService service = new NightlySnapshotService(null, config);
        CreatorSnapshot result = service.selectAtCutoff(cutoff,
                List.of(
                        new CreatorSnapshot.DigitalDelivery("before", "learner-1", "asset-1", cutoff.minusSeconds(1)),
                        new CreatorSnapshot.DigitalDelivery("after", "learner-2", "asset-2", cutoff.plusSeconds(1))),
                List.of(new CreatorSnapshot.SubscriberUpdate("learner-1", "course-1", "active", cutoff)),
                List.of(
                        new CreatorSnapshot.ContentProcessing("ready", "completed", cutoff),
                        new CreatorSnapshot.ContentProcessing("busy", "processing", cutoff.minusSeconds(20))));

        check(result.digitalDeliveries().size() == 1, "delivery after cutoff must be excluded");
        check(result.subscriberUpdates().size() == 1, "subscriber update at cutoff must be included");
        check(result.completedContent().size() == 1, "only completed content belongs in the snapshot");
        check(result.completedContent().get(0).contentId().equals("ready"), "completed content identity changed");
        System.out.println("PASS: cutoff keeps 1 delivery, 1 subscriber update, and 1 completed lesson");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

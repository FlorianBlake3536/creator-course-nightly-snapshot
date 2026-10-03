import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class NightlySnapshotService {
    private final InfraiStorageClient storage;
    private final SnapshotConfig config;

    public NightlySnapshotService(InfraiStorageClient storage, SnapshotConfig config) {
        this.storage = storage;
        this.config = config;
    }

    public CreatorSnapshot selectAtCutoff(
            Instant cutoff,
            List<CreatorSnapshot.DigitalDelivery> deliveries,
            List<CreatorSnapshot.SubscriberUpdate> subscribers,
            List<CreatorSnapshot.ContentProcessing> content) {
        List<CreatorSnapshot.DigitalDelivery> selectedDeliveries = deliveries.stream()
                .filter(item -> !item.deliveredAt().isAfter(cutoff))
                .sorted(Comparator.comparing(CreatorSnapshot.DigitalDelivery::orderId))
                .toList();
        List<CreatorSnapshot.SubscriberUpdate> selectedSubscribers = subscribers.stream()
                .filter(item -> !item.changedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(CreatorSnapshot.SubscriberUpdate::learnerId))
                .toList();
        List<CreatorSnapshot.ContentProcessing> selectedContent = content.stream()
                .filter(item -> item.status().equals("completed"))
                .filter(item -> !item.updatedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(CreatorSnapshot.ContentProcessing::contentId))
                .toList();
        return new CreatorSnapshot(cutoff, selectedDeliveries, selectedSubscribers, selectedContent);
    }

    public SnapshotReceipt store(CreatorSnapshot snapshot) throws Exception {
        String objectKey = config.objectKey();
        Map<String, Object> head = storage.objectHead(config.bucket(), objectKey);
        if (Boolean.TRUE.equals(head.get("found"))) {
            return new SnapshotReceipt(objectKey, false, snapshot.completedContent().size());
        }

        String json = JsonCodec.stringify(Map.of(
                "cutoff", snapshot.cutoff().toString(),
                "digital_deliveries", snapshot.digitalDeliveries(),
                "subscriber_updates", snapshot.subscriberUpdates(),
                "completed_content", snapshot.completedContent()));
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        storage.objectPut(config.bucket(), objectKey, encoded, "application/json", "snapshot:" + objectKey);
        return new SnapshotReceipt(objectKey, true, snapshot.completedContent().size());
    }

    public record SnapshotReceipt(String objectKey, boolean uploaded, int completedContentCount) {}
}

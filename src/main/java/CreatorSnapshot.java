import java.time.Instant;
import java.util.List;

public record CreatorSnapshot(
        Instant cutoff,
        List<DigitalDelivery> digitalDeliveries,
        List<SubscriberUpdate> subscriberUpdates,
        List<ContentProcessing> completedContent) {

    public record DigitalDelivery(String orderId, String learnerId, String assetId, Instant deliveredAt) {}
    public record SubscriberUpdate(String learnerId, String courseId, String status, Instant changedAt) {}
    public record ContentProcessing(String contentId, String status, Instant updatedAt) {}
}

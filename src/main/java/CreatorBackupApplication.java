import java.time.Instant;
import java.util.List;

public final class CreatorBackupApplication {
    public static void main(String[] args) throws Exception {
        SnapshotConfig config = SnapshotConfig.fromEnvironment();
        InfraiStorageClient storage = new InfraiStorageClient(config.apiKey());
        if (args.length == 1 && args[0].equals("setup")) {
            storage.bucketCreate(config.bucket());
            System.out.println("Bucket ready: " + config.bucket());
            return;
        }

        Instant cutoff = Instant.now(config.clock());
        NightlySnapshotService service = new NightlySnapshotService(storage, config);
        CreatorSnapshot snapshot = service.selectAtCutoff(cutoff,
                List.of(new CreatorSnapshot.DigitalDelivery("order-1042", "learner-7", "course-java-1", cutoff.minusSeconds(90))),
                List.of(new CreatorSnapshot.SubscriberUpdate("learner-7", "course-java", "active", cutoff.minusSeconds(60))),
                List.of(
                        new CreatorSnapshot.ContentProcessing("lesson-12", "completed", cutoff.minusSeconds(30)),
                        new CreatorSnapshot.ContentProcessing("lesson-13", "processing", cutoff.minusSeconds(10))));
        NightlySnapshotService.SnapshotReceipt receipt = service.store(snapshot);
        System.out.println(JsonCodec.stringify(receipt));
    }
}

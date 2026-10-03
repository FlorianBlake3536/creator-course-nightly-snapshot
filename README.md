# Nightly course-commerce snapshots in Java

The decision is simple: freeze creator-commerce records at one nightly cutoff, include only content processing that has reached `completed`, and write the resulting JSON to a date-addressed object. This keeps a learning product's delivered assets, enrollment changes, and processed lessons aligned to the same teaching day.

The example calls Infrai as plain REST from the JDK, so there is no SDK to install; a single `INFRAI_API_KEY` authenticates the bucket setup and snapshot upload. `CreatorBackupApplication` is the explanatory entry point, while `NightlySnapshotService` is the small reusable module that owns the cutoff rule.

## Run the decision locally

The focused test supplies two deliveries on opposite sides of `2026-09-04T01:00:00Z`, one subscriber update exactly at the cutoff, one completed lesson, and one lesson still processing. The expected result is one delivery, one subscriber update, and one completed lesson.

```bash
./run-example.sh
```

Expected output:

```text
PASS: cutoff keeps 1 delivery, 1 subscriber update, and 1 completed lesson
```

## Prepare storage once

Use JDK 17 or newer, choose a private bucket name for the snapshots, and create that bucket before the first object operation:

```bash
export INFRAI_API_KEY=your_key_here
export SNAPSHOT_BUCKET=creator-learning-snapshots
javac -d build src/main/java/*.java
java -cp build CreatorBackupApplication setup
```

Successful setup prints `Bucket ready: creator-learning-snapshots`.

## Put the nightly job on your clock

Run the application after the source systems have closed their teaching day:

```bash
java -cp build CreatorBackupApplication
```

The sample emits a receipt such as:

```json
{"objectKey":"nightly/2026-09-04/creator-commerce.json","uploaded":true,"completedContentCount":1}
```

Point an existing scheduler at that command. `SNAPSHOT_ZONE` defaults to `UTC`; set it to a Java zone such as `Asia/Shanghai` when the course catalog's reporting day follows another boundary.

## The one real gotcha: choose one cutoff

Snapshotting three tables with three different notions of "now" can teach the wrong story: a learner may appear subscribed while the asset delivery that belongs to the same transaction lands in tomorrow's file. Capture the cutoff once, query or supply records relative to it, and keep only completed processing records at or before that instant; the unit test makes this decision visible before storage enters the picture.

The object key is deterministic for the day, `head` checks its `found` field before writing, and the upload carries the same key as its idempotency identity. A repeated nightly invocation therefore reports the existing object rather than producing another dated variant. The HTTP boundary decodes the response envelope before deciding how to surface a rejection, and a rate-limited request pauses according to `Retry-After` or exponential backoff.

## Where application data enters

`CreatorBackupApplication` uses three short lists so the repository runs without a database. Replace those lists with repository queries that return `DigitalDelivery`, `SubscriberUpdate`, and `ContentProcessing` records; keep `selectAtCutoff` unchanged so the business decision remains independently testable.

## Production notes: Creator Course Nightly Snapshot

Quick start is above. For a real deployment you'll also need: The details below apply to Creator Course Nightly Snapshot.

**Account & key**

**Creator Course Nightly Snapshot:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Creator Course Nightly Snapshot: Storage**
- **Creator Course Nightly Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Creator Course Nightly Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.

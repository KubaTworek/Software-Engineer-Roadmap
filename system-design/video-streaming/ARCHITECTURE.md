# Architecture — Phase 5 Live

## High-level live architecture

```text
Broadcaster / OBS / ffmpeg
        |
        | RTMP: rtmp://localhost:1935/live/{streamKey}
        v
NGINX RTMP ingest
        |
        | internal RTMP
        v
LiveTranscodingWorker
        |
        | FFmpeg -> HLS playlist + segments
        v
MinIO origin storage
        |
        | CDN-style URL
        v
Player / client
```

## Added components

### LiveStream entity

Stores live session metadata:

- title and description
- status lifecycle
- latency mode
- stream key
- public and internal ingest URLs
- HLS master object key
- DVR configuration
- recording object key
- optional VOD video reference

### LiveStreamService

Owns live session commands:

- create/update stream
- start stream
- stop stream
- playback response
- live-to-VOD conversion

### LiveTranscodingWorker

Consumes RabbitMQ messages:

- `LiveStartRequested`
- `LiveStopRequested`

For each started stream it launches FFmpeg, writes HLS files to a local working directory and syncs those files to object storage.

### Live-to-VOD bridge

After live ends, the recording source is uploaded to object storage. The `convert-to-vod` endpoint creates a standard `Video` row, creates a `TranscodingJob` and publishes the existing VOD transcoding event.

## Status lifecycle

```text
SCHEDULED -> STARTING -> LIVE -> STOPPING -> ENDED -> VOD_READY
                         |                     |
                         v                     v
                       FAILED              convert-to-vod
```

## Standard versus low latency

`STANDARD` uses longer HLS segments and a larger playback buffer.

`LOW_LATENCY` uses shorter segments and FFmpeg zerolatency tuning. This reduces glass-to-glass latency in local tests, but it is not a full LL-HLS implementation with partial segments and blocking playlist reloads.

## DVR

DVR uses a sliding HLS playlist. The approximate number of segments is:

```text
hls_list_size = dvrWindowSeconds / segmentSeconds
```

The worker keeps syncing current playlist and segments to object storage.

## Queues

Additional RabbitMQ resources:

```text
video.live.start
video.live.stop
video.live.dlq
```

Routing keys:

```text
live.start
live.stop
live.dlq
```

## Metrics

Additional Prometheus metrics:

```text
video_live_start_total
video_live_stop_total
video_live_failure_total
video_live_session_seconds
```

## Known MVP limitations

- No distributed lock for live ownership.
- Simplified RTMP ingest only.
- Simplified low-latency mode.
- HLS sync to object storage is simple polling, not an optimized live origin.
- Live recording and HLS output are produced by a simplified FFmpeg command.
- No adaptive multi-bitrate ladder for live yet.

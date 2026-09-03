# Mini-Redis (Java)

A custom in-memory key-value store built from scratch in Java, modeled on Redis's core mechanics: thread-safe storage, dual-strategy TTL eviction, crash-durable persistence via an append-only log, and a multi-threaded TCP server that speaks a simple text protocol.

Built to understand — not just use — the concurrency, persistence, and networking primitives that power real in-memory databases.

## Why this project

Most CRUD projects hide concurrency and I/O behind a framework. This one doesn't — every thread-safety guarantee, every disk write, and every socket accept is hand-rolled, which means every race condition and design tradeoff had to be reasoned through explicitly rather than assumed away.

## Features

- **O(1) thread-safe storage** using `ConcurrentHashMap`, with atomic compound operations (`computeIfPresent`) to eliminate check-then-act race conditions
- **Dual TTL eviction strategy** — lazy eviction on read + an active background sweeper (`ScheduledExecutorService`) that reclaims memory from keys that are never accessed again
- **Append-Only File (AOF) persistence** — every mutation is logged to disk with an *absolute* expiry timestamp (not a relative TTL), so recovery reconstructs exactly the state that existed at crash time, expired keys included
- **Multi-threaded TCP server** on port `6379`, using a bounded thread pool so one slow client can't starve the others
- **Simple text protocol** (`SET`, `GET`, `DEL`, `EXPIRE`) testable directly via `telnet`/`netcat`, no special client needed
- **Verified under load** — a 100-client concurrent socket test suite with zero errors, plus an independent two-process AOF rebuild proof

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     SocketServer                          │
│   ServerSocket.accept() loop → ExecutorService(64)         │
└───────────────────────┬─────────────────────────────────┘
                         │ one thread per connection
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    ClientHandler                           │
│   reads command lines → CommandParser → writes responses   │
└───────────────────────┬─────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    KeyValueStore                            │
│         ConcurrentHashMap<String, DataValue>                │
│    SET / GET / DEL / EXPIRE — atomic, thread-safe            │
└───────┬───────────────────────────────────┬───────────────┘
        │                                   │
        ▼                                   ▼
┌───────────────────┐             ┌───────────────────────┐
│ EvictionScheduler   │             │      AofLogger          │
│ background sweep    │             │ logs every mutation      │
│ every 100ms          │             │ to data/mini-redis.aof   │
└───────────────────┘             └───────────┬───────────────┘
                                               ▼
                                     ┌───────────────────┐
                                     │   AofRecovery        │
                                     │ replays log on startup│
                                     └───────────────────┘
```

## Getting started

**Requirements:** JDK 17+

```bash
git clone https://github.com/<your-username>/mini-redis.git
cd mini-redis

# compile
javac -d out $(find src -name "*.java")

# run the server (stays running, listens on port 6379)
java -cp out com.miniredis.server.Main
```

In a second terminal, connect with the interactive CLI:
```bash
java -cp out com.miniredis.server.CliClient
```
or with raw netcat/telnet:
```bash
nc localhost 6379
```

### Example session
```
mini-redis> SET user:1 Alice 100
-> +OK
mini-redis> GET user:1
-> Alice
mini-redis> DEL user:1
-> :1
mini-redis> GET user:1
-> (nil)
```

## Design decisions worth calling out

**Atomic check-then-act with `computeIfPresent`**
A naive lazy-expiry check (`get` → check expiry → `remove`) has a classic TOCTOU race: another thread could overwrite the key between the check and the delete, causing the delete to destroy fresh data instead of stale data. Every expiry-related mutation in this project (`GET`'s lazy eviction, the active sweeper, `EXPIRE`) uses `computeIfPresent` so the read-decide-write happens as one atomic unit under the map's internal lock.

**Absolute timestamps in the AOF, not relative TTLs**
Logging `SET key value 10` (meaning "10 seconds from now") would be wrong to replay later — on recovery, that key would get a fresh 10-second TTL from the moment of replay, silently reviving data that should have expired. Instead, the AOF stores the *absolute* epoch-millis expiry, so replaying a command at any point in the future still evaluates correctly against real elapsed time.

**Bounded sweep batches, not full-map scans**
The background eviction sweeper checks a capped number of entries per cycle (rather than scanning the entire map every 100ms), trading "some expired keys linger a little longer" for predictable, bounded CPU overhead — the same tradeoff real Redis makes with its active-expire cycle.

**TCP_NODELAY on all sockets**
Under load testing, small request/response round-trips were measurably slowed by Nagle's algorithm batching tiny packets. Disabling it (`setTcpNoDelay(true)`) is standard practice for low-latency, small-message protocols like this one.

## Testing

| Test | What it proves |
|---|---|
| `SocketConcurrencyTest` | 100 simultaneous real socket clients × 50 ops each, zero errors under contention |
| In-process concurrency stress test | 50 threads × 1000 ops directly against the store, verifying no map corruption |
| AOF rebuild proof (two-process) | A brand-new JVM, with zero shared memory, recovers exact prior state — including correct ordering through overwrites and deletes — purely from the on-disk log |

## Known limitations / honest tradeoffs

- Text protocol, not the binary RESP protocol real Redis uses — deliberate scope choice to focus on concurrency/persistence/networking fundamentals rather than protocol parsing
- No `fsync` durability guarantee — writes go through the OS page cache; a full power loss (not just process crash) could lose the last few writes
- Values containing spaces are not fully escaped in the AOF format — a known simplification
- Performance benchmarks were run on WSL2, whose virtualized networking adds overhead not present on native Linux

## Roadmap ideas

- [ ] RESP protocol support for compatibility with real `redis-cli`
- [ ] Snapshotting (RDB-style) to compact the AOF periodically
- [ ] Pub/Sub commands
- [ ] Configurable `fsync` policy (always / every second / never)

## License

MIT

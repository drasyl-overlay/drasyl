# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2021-XX-XX

### Added

-
-
-
-
-

### Changed

-
-
-
-
-

### Fixed

-
-
-
-
-

## [0.4.0] - 2021-02-27

### Added

- Hole Punching is now used to traverse NATs. This should dramatically increase the proportion of
  direct connections between peers.
- Messages that are too large for a single UDP datagram are now automatically split into multiple
  datagrams (and re-assembled on the receiving side).
- When changing the network, new port mappings are now automatically created on the new network.
- `DrasylNode`'s API has been enhanced with `@NotNull` and `@Nullable` annotations.
- Messages that can be delivered within the same JVM are now passed by reference (previously these
  messages have been unnecessarily serialized). 
  **So make sure you either send copies of your objects or it's 
  fine for other nodes to make changes to that object.**
- Static routes to other remote peers can now be defined. This allows discovery to be omitted in
  static environments (or test setups).
- **Messages with content `null` are now possible.**
- `perf` command has been added to the CLI. This allows you to run performance tests of connections.
- Each node can now have several super peers simultaneously. The fastest super peer will
  automatically be used. If one or more super peers fail (temporarily), one of the remaining super
  peers will automatically be used. By default, each node will use two of our super peers.
- New examples have been added:
  https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example
- Nodes can now be implemented as finite state machines (see our example of the philosopher
  problem: https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example/diningphilosophers).

### Changed

- By default, each node now listens on a port in the range 22528 and 65528, which is derived from 
  its identity. This means that the chance for a port collision is now reduced when
  multiple nodes are running on one computer.
- UDP is now used instead of TCP for communication with remote peers.
- The entire protocol for constructing the overlay network has been revised (it's far better now!).
- Messages are now serialized in binary. This is faster and creates smaller messages on the wire.
- If possible, messages are processed using zero-copy.
- Documentation has been revised (Javadoc and/or documentation at https://docs.drasyl.org).
- Messages can now be additionally serialized by protobuf or Java. Furthermore, own serializers can
  be implemented. Read more at https://docs.drasyl.org.
- The third-party portmapper library has been replaced with our own more lightweight 
  and more resilient implementation.
- All dependencies have been updated to the latest versions.

### Fixed

- `IntraVmDiscovery`: Other nodes running within the same JVM that belongs to a different overlay
  network are now ignored.
- `LocalHostDiscovery`: Other nodes running on the same local computer that belong to a different
  overlay network are now ignored.
- Content of received messages is now better verified before it is deserialized.
- Unchecked exceptions thrown by `DrasylNode.onEvent` no longer produce ridiculously long error
  messages.

## [0.3.0] - 2020-10-30

### Added

- Each node now belongs to a certain network ID. Connections between nodes will occur only if both.
  peers use the same network ID. The main network has ID 1 (see configuration `drasyl.network.id`).
- Added drasyl groups plugin for membership management.
- Added support for netty's Epoll channels and event loops.
- Plugins can now access the node's identity.

### Changed

- All `drasyl.marshalling` config properties will now distinct between allowed outgoing and ingoing
  types. Config must be updated according to our documentation.
- `drasyl-core` now only depends on slf4j and no longer on logback. As part of this, the methods.
  `DrasylNode.getLogLevel()` and `DrasylNode.setLogLevel()` have been removed and the configuration.
  `drasyl.loglevel` has been removed.
- Nodes now only accept children joins if they are configured as a
  super-peer (`drasyl.super-peer.enabled = false`).
- Removed grandchildren nodes. The hierarchy is now limited in depth.
- All-new plugin interface.
- Changed docker base image from `drasyl/drasyl-build-images:jre-11-curl-7.64.0`
  to `openjdk:11-jre-slim` and removed default HEALTHCHECK.
- Enhanced JavaDoc.
- Changed `TypeValidator` to distinguish between in- and outbound messages.
- EmbeddedPipeline was generalized to simplify handler testing.
- Node now checks if the permissions of the identity file are too open (POSIX file systems only).
- Changed `Pipeline.processInbound` to accept `RelayableMessage`.
- Endpoints defined in `drasyl.super-peer.endpoints` must now always include the super peer's public
  key.

### Fixed

- Fixed memory-leaks of certain immutable classes.
- Fixed `DefaultPipeline.addBefore` method.
- Fixed error in IntraVM discovery which has delivered events around the pipeline directly to the
  application.
- Several minor bug fixes.

## [0.2.0] - 2020-09-15

### Added

- Plugins can now be defined in the configuration `drasyl.plugins`. Plugins can be used to change
  the behavior of the node when interacting with the network.
- `DrasylNode.send()` can now send arbitrary objects (make sure that a suitable serialization codec
  is defined in the configuration `drasyl.marshalling`).
- `DrasylNode.identity()` returns own identity.
- Node will now try to expose itself via UPnP-IGD/NAT-PMP/PCP (can be disabled in the configuration
  via `drasyl.expose.enabled = false`).
- Local Host Discovery: Node can use the file system to discover other drasyl nodes running on the
  local computer (`drasyl.local-host-discovery`).
- CLI: `wormhole` sub-command added.

### Changed

- `wss://production.env.drasyl.org#025fff6f625f5dee816d9f8fe43895479aecfda187cb6a3330894a07e698bc5bd8`
  is now the default super peer (`drasyl.super-peer.endpoints`).
- Configuration `drasyl.super-peer.public-key` removed (public key can now be specified in
  configuration `drasyl.super-peer.endpoints`).
- `DrasylNode.send()` now returns a future, which complements when the message has been processed.
- `DrasylNode.send()` no longer throws exceptions, but writes them to the future.
- `DrasylNode.send()` is no longer synchronized.
- The own identity is now created in the `DrasylNode` constructor and not at node start.
- The `DrasylNode` constructor now throws an error if the own identity is invalid.
- Number of threads required by drasyl reduced by internal switch to `DrasylScheduler`.
- Chat example and CLI now stops the JVM after shutting down the node.
- Javadoc improved.
- Changed to a non-blocking logger.
- Chunking is now disabled by default in the configuration, because it is not fully implemented
  yet (`drasyl.message.max-content-length`).
- The drasyl thread pools are now lazily created when the first node is started.
- `MessageEvent.getMessage()` replaced by `MessageEvent.getSender()` and `MessageEvent.getPayload()`
  .
- Messages and Events objects are now immutable.
- Non-started node now throws a `NoPathToPublicKeyException` for all outgoing messages.
- Removed `PeerUnreachableEvent` and `NodeIdentityCollisionEvent`.

### Fixed

- Fixed concurrent modification error in `PeersManager`.
- Fixed concurrent modification error in `DirectConnectionsManager`.
- Fixed a bug that causes endless loops when a wanted handler was not the next handler in the
  pipeline.
- Fixed a bug that causes a `NullPointerException` when the node is started and stopped too quickly.
- Fixed a bug that results in a partially started node if it was started and stopped too quickly.
- Make sure that monitoring is also shut down in case of an error.
- Reduce time till sending ping messages to prevent problems with too aggressive reverse proxies (I
  mean you, nginx).

## [0.1.2] - 2020-07-14

### Added

- Release for Maven Central

## [0.1.1] - 2020-07-14

### Added

- Release for Maven Central

## [0.1.0] - 2020-07-10

### Added

- First release

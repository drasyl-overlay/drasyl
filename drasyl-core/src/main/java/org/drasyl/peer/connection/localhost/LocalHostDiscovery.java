package org.drasyl.peer.connection.localhost;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.util.DrasylScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * Uses the file system to discover other drasyl nodes running on the local computer.
 * <p>
 * To do this, all nodes regularly write their current endpoints to the file system. At the same
 * time the file system is monitored to detect new nodes. If the file system does not support
 * monitoring, a fallback to polling is used.
 * <p>
 * The discovery directory is scanned whenever communication with another peer occur.
 * <p>
 * This discovery mechanism does not itself establish connections to other peers. Only {@link
 * PeerInformation} are discovered and passed to the {@link PeersManager}. These information can
 * then be used by the {@link org.drasyl.peer.connection.direct.DirectConnectionsManager} to
 * establish connections.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/LocalHostAwarenessAgent.java
 */
@SuppressWarnings("java:S1192")
public class LocalHostDiscovery implements DrasylNodeComponent {
    private static final Logger LOG = LoggerFactory.getLogger(LocalHostDiscovery.class);
    private final Path discoveryPath;
    private final Duration leaseTime;
    private final CompressedPublicKey ownPublicKey;
    private final PeersManager peersManager;
    private final Set<Endpoint> endpoints;
    private final Observable<CompressedPublicKey> communicationOccurred;
    private final AtomicBoolean opened;
    private final AtomicBoolean doScan;
    private final Scheduler scheduler;
    private Disposable watchDisposable;
    private Disposable postDisposable;
    private WatchService watchService; // NOSONAR
    private Disposable communicationObserver;
    private PeerInformation postedPeerInformation;

    public LocalHostDiscovery(DrasylConfig config,
                              CompressedPublicKey ownPublicKey,
                              PeersManager peersManager,
                              Set<Endpoint> endpoints,
                              Observable<CompressedPublicKey> communicationOccurred) {
        this(
                config.getLocalHostDiscoveryPath(),
                config.getLocalHostDiscoveryLeaseTime(),
                ownPublicKey,
                peersManager,
                endpoints,
                communicationOccurred,
                new AtomicBoolean(),
                new AtomicBoolean(),
                DrasylScheduler.getInstanceLight(),
                null,
                null,
                null
        );
    }

    @SuppressWarnings({ "java:S107" })
    LocalHostDiscovery(Path discoveryPath,
                       Duration leaseTime,
                       CompressedPublicKey ownPublicKey,
                       PeersManager peersManager,
                       Set<Endpoint> endpoints,
                       Observable<CompressedPublicKey> communicationOccurred,
                       AtomicBoolean opened,
                       AtomicBoolean doScan,
                       Scheduler scheduler,
                       Disposable watchDisposable,
                       Disposable postDisposable,
                       Disposable communicationObserver) {
        this.discoveryPath = discoveryPath;
        this.leaseTime = leaseTime;
        this.ownPublicKey = ownPublicKey;
        this.peersManager = peersManager;
        this.endpoints = endpoints;
        this.communicationOccurred = communicationOccurred;
        this.opened = opened;
        this.doScan = doScan;
        this.scheduler = scheduler;
        this.watchDisposable = watchDisposable;
        this.postDisposable = postDisposable;
        this.communicationObserver = communicationObserver;
    }

    @Override
    @SuppressWarnings({ "java:S3776" })
    public void open() {
        if (opened.compareAndSet(false, true)) {
            LOG.debug("Start Local Host Discovery...");
            File directory = discoveryPath.toFile();
            if (!directory.exists() && !directory.mkdir()) {
                LOG.warn("Discovery directory '{}' could not be created.", discoveryPath.toAbsolutePath());
            }
            else if (!(directory.isDirectory() && directory.canRead() && directory.canWrite())) {
                LOG.warn("Discovery directory '{}' not accessible.", discoveryPath.toAbsolutePath());
            }
            else {
                scan();
                tryWatchDirectory();
                keepOwnInformationUpToDate();
                communicationObserver = communicationOccurred.subscribe(publicKey -> {
                    // A scan only happens if a change in the directory was monitored or the time of last poll is too old.
                    if (doScan.compareAndSet(true, false)) {
                        scan();
                    }
                });
            }
            LOG.debug("Local Host Discovery started.");
        }
    }

    /**
     * Tries to monitor {@link #discoveryPath} so that any changes are automatically reported. If
     * this is not possible, we have to fall back to periodical polling.
     */
    private void tryWatchDirectory() {
        try {
            File directory = discoveryPath.toFile();
            FileSystem fileSystem = discoveryPath.getFileSystem();
            watchService = fileSystem.newWatchService();
            discoveryPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            LOG.debug("Watch service for directory '{}' registered", directory);
            watchDisposable = scheduler.schedulePeriodicallyDirect(() -> {
                if (watchService.poll() != null) {
                    // directory has been changed
                    doScan.set(true);
                }
            }, 0, 5, SECONDS);
        }
        catch (IOException e) {
            LOG.debug("Unable to register watch service. Use polling as fallback: ", e);

            // use polling as fallback
            watchService = null;
        }
    }

    /**
     * Writes periodically the actual own information to {@link #discoveryPath}.
     */
    private void keepOwnInformationUpToDate() {
        Duration refreshInterval;
        if (leaseTime.toSeconds() > 5) {
            refreshInterval = leaseTime.minus(ofSeconds(5));
        }
        else {
            refreshInterval = ofSeconds(1);
        }
        postDisposable = scheduler.schedulePeriodicallyDirect(() -> {
            // only scan in polling mode when watchService does not work
            if (watchService == null) {
                doScan.set(true);
            }
            postInformation();
        }, 0, refreshInterval.toMillis(), MILLISECONDS);
    }

    /**
     * Scans in {@link #discoveryPath} for peer information of other drasyl nodes.
     */
    void scan() {
        LOG.debug("Scan directory {} for new peers.", discoveryPath);
        String ownPublicKeyString = this.ownPublicKey.toString();
        long maxAge = System.currentTimeMillis() - leaseTime.toMillis();
        File[] files = discoveryPath.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    String fileName = file.getName();
                    if (file.lastModified() >= maxAge && fileName.length() == 71 && fileName.endsWith(".json") && !fileName.startsWith(ownPublicKeyString)) {
                        CompressedPublicKey publicKey = CompressedPublicKey.of(fileName.replace(".json", ""));
                        PeerInformation peerInformation = JACKSON_READER.readValue(file, PeerInformation.class);
                        LOG.trace("Information for peer {} discovered by file '{}'", publicKey, fileName);
                        peersManager.setPeerInformation(publicKey, peerInformation);
                    }
                }
                catch (CryptoException | IOException e) {
                    LOG.warn("Unable to read peer information from '{}': ", file.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * Posts own {@link PeerInformation} to {@link #discoveryPath}.
     */
    private void postInformation() {
        PeerInformation peerInformation = PeerInformation.of(endpoints);

        Path filePath = discoveryPath.resolve(ownPublicKey.toString() + ".json");
        LOG.trace("Post own Peer Information to {}", filePath);
        File file = filePath.toFile();
        try {
            if (peerInformation.equals(postedPeerInformation)) {
                // information has not changed. Just touch file
                file.setLastModified(System.currentTimeMillis()); // NOSONAR
            }
            else {
                JACKSON_WRITER.writeValue(file, peerInformation);
                file.deleteOnExit();
            }

            postedPeerInformation = peerInformation;
        }
        catch (IOException e) {
            LOG.warn("Unable to write peer information to '{}': {}", filePath.toAbsolutePath(), e.getMessage());
        }
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            if (communicationObserver != null) {
                communicationObserver.dispose();
            }
            if (watchDisposable != null) {
                watchDisposable.dispose();
            }
            if (postDisposable != null) {
                postDisposable.dispose();
            }
        }
    }
}
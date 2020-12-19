/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.util.DrasylSupplier;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.util.Collections;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.drasyl.util.PathUtil.hasPosixSupport;

/**
 * This class holds the identity of the node. Messages to the node are addressed to the identity. In
 * a future release, messages will be signed and encrypted with public-private key pairs contained
 * in the identity.
 */
public class IdentityManager {
    public static final byte POW_DIFFICULTY = 6;
    private static final Logger LOG = LoggerFactory.getLogger(IdentityManager.class);
    private final DrasylSupplier<Identity, IdentityManagerException> identityGenerator;
    private final DrasylConfig config;
    private Identity identity;

    /**
     * Manages the identity at the specified file path. If there is no identity at this file path
     * yet, a new one is created.
     */
    public IdentityManager(final DrasylConfig config) {
        this(IdentityManager::generateIdentity, config, null);
    }

    IdentityManager(final DrasylSupplier<Identity, IdentityManagerException> identityGenerator,
                    final DrasylConfig config,
                    final Identity identity) {
        this.identityGenerator = identityGenerator;
        this.config = config;
        this.identity = identity;
    }

    /**
     * Attempts to load the identity defined in the configuration: First it tries to read the key
     * pair directly from the configuration. If no key pair is specified there, the identity is
     * loaded from the identity file path specified in the configuration. If the file does not
     * exist, a new identity is generated and written to the file. If all this fails and no identity
     * can be loaded, an {@link IdentityManagerException} is thrown.
     *
     * @throws IdentityManagerException if identity could not be loaded or created
     */
    public void loadOrCreateIdentity() throws IdentityManagerException {
        if (config.getIdentityProofOfWork() != null && config.getIdentityPublicKey() != null && config.getIdentityPrivateKey() != null) {
            LOG.debug("Load identity specified in config");
            try {
                this.identity = Identity.of(config.getIdentityProofOfWork(), config.getIdentityPublicKey(), config.getIdentityPrivateKey());
            }
            catch (final IllegalArgumentException e) {
                throw new IdentityManagerException("Identity read from configuration seems invalid: " + e.getMessage());
            }
        }
        else {
            final Path path = config.getIdentityPath();

            if (isIdentityFilePresent(path)) {
                LOG.debug("Read Identity from file '{}'", path);
                this.identity = readIdentityFile(path);
            }
            else {
                LOG.debug("No Identity present. Generate a new one and write to file '{}'.", path);
                final Identity myIdentity = identityGenerator.get();
                writeIdentityFile(path, myIdentity);
                this.identity = myIdentity;
            }
        }

        if (!this.identity.isValid()) {
            throw new IdentityManagerException("Loaded or Created Identity is invalid.");
        }
    }

    /**
     * Returns <code>true</code> if the identity file <code>path</code> exists. Otherwise
     * <code>false</code> is returned.
     *
     * @param path path to identity file
     * @return {@code true} if file exists
     */
    private static boolean isIdentityFilePresent(final Path path) {
        return path.toFile().exists() && path.toFile().isFile();
    }

    /**
     * Reads the identity from <code>path</code>. Throws <code>IdentityManagerException</code> if
     * file cannot be read or file has unexpected content.
     *
     * @param path path to identity file
     * @return The identity contained in the file
     * @throws IdentityManagerException if identity could not be read from file
     */
    private static Identity readIdentityFile(final Path path) throws IdentityManagerException {
        try {
            // check if file permissions are too open
            if (hasPosixSupport(path)) {
                final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
                if (!Collections.disjoint(permissions, Set.of(GROUP_EXECUTE, GROUP_WRITE, GROUP_READ, OTHERS_EXECUTE, OTHERS_WRITE, OTHERS_READ))) {
                    throw new IdentityManagerException("Unprotected private key: It is required that your identity file '" + path + "' is NOT accessible by others.'");
                }
            }

            return JACKSON_READER.readValue(path.toFile(), Identity.class);
        }
        catch (final JsonProcessingException e) {
            throw new IdentityManagerException("Unable to load identity from file '" + path + "': ", e);
        }
        catch (final IOException e) {
            throw new IdentityManagerException("Unable to access identity file '" + path + "': ", e);
        }
    }

    /**
     * Generates a new random identity.
     *
     * @return the generated identity
     * @throws IdentityManagerException if an identity could not be generated
     */
    public static Identity generateIdentity() throws IdentityManagerException {
        try {
            final KeyPair newKeyPair = Crypto.generateKeys();
            final CompressedPublicKey publicKey = CompressedPublicKey.of(newKeyPair.getPublic());
            final CompressedPrivateKey privateKey = CompressedPrivateKey.of(newKeyPair.getPrivate());
            final ProofOfWork proofOfWork = ProofOfWork.generateProofOfWork(publicKey, POW_DIFFICULTY);
            return Identity.of(proofOfWork, publicKey, privateKey);
        }
        catch (final CryptoException e) {
            throw new IdentityManagerException("Unable to generate new identity: " + e.getMessage());
        }
    }

    /**
     * Writes the identity {@code identity} to the file {@code path}. Attention: If {@code path}
     * already contains an identity, it will be overwritten without warning.
     *
     * @param path     path where the identity should be written to
     * @param identity this identity is written to the file
     * @throws IdentityManagerException if the identity could not be written to the file
     */
    private static void writeIdentityFile(final Path path,
                                          final Identity identity) throws IdentityManagerException {
        final File file = path.toFile();

        if (Files.isDirectory(path) || (file.getParentFile() != null && !file.getParentFile().exists())) {
            throw new IdentityManagerException("Identity path '" + path + "' is a directory or path does not exist");
        }
        else if (file.exists() && !file.canWrite()) {
            throw new IdentityManagerException("Identity path '" + path + "' is not writable");
        }
        else {
            try {
                // ensure that file permissions will not be too open
                if (hasPosixSupport(path) && file.createNewFile()) {
                    Files.setPosixFilePermissions(path, Set.of(OWNER_READ, OWNER_WRITE));
                }

                JACKSON_WRITER.with(new DefaultPrettyPrinter()).writeValue(file, identity);
            }
            catch (final IOException e) {
                throw new IdentityManagerException("Unable to write identity to file '" + path + "': " + e.getMessage());
            }
        }
    }

    public CompressedPublicKey getPublicKey() {
        return identity.getPublicKey();
    }

    public CompressedPrivateKey getPrivateKey() {
        return identity.getPrivateKey();
    }

    public ProofOfWork getProofOfWork() {
        return identity.getProofOfWork();
    }

    /**
     * @return returns the node identity.
     */
    public Identity getIdentity() {
        return requireNonNull(identity);
    }

    /**
     * Deletes the identity file specified in the configuration.
     * <p>
     * ATTENTION: Messages directed to the present identity can then no longer be decrypted and
     * read. This step is irreversible. Should only be used if the present identity should never be
     * used again!
     */
    public static void deleteIdentityFile(final Path path) throws IdentityManagerException {
        final File file = path.toFile();

        if (!file.exists()) {
            // nothing to do
            return;
        }

        try {
            Files.delete(path);
        }
        catch (final IOException e) {
            throw new IdentityManagerException("Unable to delete identity file '" + path + "': " + e.getMessage());
        }
    }
}
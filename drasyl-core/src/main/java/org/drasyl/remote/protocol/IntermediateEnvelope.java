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
package org.drasyl.remote.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCounted;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.MessageType;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedShort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.PublicKey;

import static org.drasyl.remote.protocol.MessageId.randomMessageId;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;

/**
 * This class allows to read a given {@link ByteBuf} encoded protobuf message in parts with only
 * decoding the requested parts of the given {@link ByteBuf}. If a part was request it will be
 * translated into a Java object.
 */
public class IntermediateEnvelope<T extends MessageLite> implements ReferenceCounted {
    private ByteBuf originalMessage;
    private ByteBuf message;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private T body;

    IntermediateEnvelope(final ByteBuf originalMessage,
                         final ByteBuf message,
                         final PublicHeader publicHeader,
                         final PrivateHeader privateHeader, final T body) {
        this.originalMessage = originalMessage;
        this.message = message;
        this.publicHeader = publicHeader;
        this.privateHeader = privateHeader;
        this.body = body;
    }

    private IntermediateEnvelope(final ByteBuf message) {
        if (!message.isReadable()) {
            throw new IllegalArgumentException("The given message has no readable data.");
        }

        this.originalMessage = message;
        this.message = originalMessage.duplicate();
        this.publicHeader = null;
        this.privateHeader = null;
        this.body = null;
    }

    private IntermediateEnvelope(final PublicHeader publicHeader,
                                 final PrivateHeader privateHeader,
                                 final T body) {
        this.originalMessage = PooledByteBufAllocator.DEFAULT.buffer();
        this.message = originalMessage.duplicate();
        this.publicHeader = publicHeader;
        this.privateHeader = privateHeader;
        this.body = body;
    }

    @Override
    public String toString() {
        return "IntermediateEnvelope{" +
                "originalMessage=" + originalMessage +
                ", publicHeader=" + publicHeader +
                ", privateHeader=" + privateHeader +
                ", body=" + body +
                '}';
    }

    /**
     * <b>Note: The given {@code message} {@link ByteBuf} will not be modified. This object
     * uses a duplicate of the given {@link ByteBuf}.</b>
     *
     * @param message the message that should be wrapped
     * @return an IntermediateEnvelope
     * @throws IllegalArgumentException if the given {@link ByteBuf} is not readable
     */
    public static <T extends MessageLite> IntermediateEnvelope<T> of(final ByteBuf message) {
        return new IntermediateEnvelope<>(message);
    }

    /**
     * Creates a message envelope from {@code publicHeader}, {@code privateHeader}, and {@code
     * body}.
     *
     * @param publicHeader  message's public header
     * @param privateHeader message's private header
     * @param body          the message
     * @return an IntermediateEnvelope
     */
    public static <T extends MessageLite> IntermediateEnvelope<T> of(final PublicHeader publicHeader,
                                                                     final PrivateHeader privateHeader,
                                                                     final T body) {
        return new IntermediateEnvelope<>(publicHeader, privateHeader, body);
    }

    /**
     * Creates a message envelope from {@code publicHeader} and {@code bytes}
     *
     * @param publicHeader message's public header
     * @param bytes        message's remainder as bytes (may be encrypted)
     * @return an IntermediateEnvelope
     * @throws IOException if {@code publicHeader} and {@code bytes} can not be serialized
     */
    public static <T extends MessageLite> IntermediateEnvelope<T> of(final PublicHeader publicHeader,
                                                                     final byte[] bytes) throws IOException {
        final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            publicHeader.writeDelimitedTo(outputStream);
            outputStream.write(bytes);

            return of(byteBuf);
        }
        catch (final IOException e) {
            ReferenceCountUtil.safeRelease(byteBuf);
            throw e;
        }
    }

    /**
     * Reads only the public header of the given message and retains the underlying byte
     * representation of the full message.
     *
     * @return header of the message
     * @throws IOException if the public header cannot be read read
     */
    public PublicHeader getPublicHeader() throws IOException {
        synchronized (this) {
            if (publicHeader == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    publicHeader = PublicHeader.parseDelimitedFrom(in);
                }
                catch (final IOException e) {
                    throw new IOException("Can't read public header of the given message do to the following exception: ", e);
                }
            }

            return publicHeader;
        }
    }

    /**
     * Reads only the (private) header of the given message and retains the underlying byte
     * representation of the full message.
     *
     * @return the private header
     * @throws IOException if the private header cannot be read read
     */
    public PrivateHeader getPrivateHeader() throws IOException {
        synchronized (this) {
            getPublicHeader();
            if (privateHeader == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    privateHeader = PrivateHeader.parseDelimitedFrom(in);
                }
                catch (final IOException e) {
                    throw new IOException("Can't read private header of the given message do to the following exception: ", e);
                }
            }

            return privateHeader;
        }
    }

    /**
     * Reads only the body of the given message  and retains the underlying byte representation of
     * the full message.
     *
     * @return the body
     * @throws IOException if the body cannot be read read
     */
    public T getBody() throws IOException {
        synchronized (this) {
            getPrivateHeader();
            if (body == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    body = bodyFromInputStream(privateHeader.getType(), in);
                }
                catch (final IOException e) {
                    throw new IOException("Can't read the given message do to the following exception: ", e);
                }
            }

            return body;
        }
    }

    /**
     * Reads only the body of the given message and releases the underlying byte representation of
     * the full message.
     *
     * @return the body
     * @throws IOException if the body cannot be read read
     */
    public T getBodyAndRelease() throws IOException {
        synchronized (this) {
            try {
                return getBody();
            }
            finally {
                releaseAll();
            }
        }
    }

    public ByteBuf getByteBuf() {
        synchronized (this) {
            return originalMessage;
        }
    }

    ByteBuf getInternalByteBuf() {
        synchronized (this) {
            return message;
        }
    }

    /**
     * The <b>external</b> {@link ByteBuf} or if {@code null} builds it first.
     *
     * @return the envelope as {@link ByteBuf}
     * @throws IOException if the envelope can't be build
     */
    public ByteBuf getOrBuildByteBuf() throws IOException {
        synchronized (this) {
            if (originalMessage == null || !originalMessage.isReadable()) {
                this.originalMessage = proto2ByteBuf(originalMessage);
                this.message = originalMessage.duplicate();
            }

            return originalMessage;
        }
    }

    /**
     * The <b>internal</b> {@link ByteBuf} or if {@code null} builds it first.
     *
     * @return the envelope as {@link ByteBuf}
     * @throws IOException if the envelope can't be build
     */
    ByteBuf getOrBuildInternalByteBuf() throws IOException {
        synchronized (this) {
            getOrBuildByteBuf();

            return message;
        }
    }

    @Override
    public int refCnt() {
        return originalMessage.refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        return originalMessage.retain();
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        return originalMessage.retain(increment);
    }

    @Override
    public ReferenceCounted touch() {
        return originalMessage.touch();
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        return originalMessage.touch(hint);
    }

    @Override
    public boolean release() {
        return originalMessage.release();
    }

    @Override
    public boolean release(final int decrement) {
        return originalMessage.release(decrement);
    }

    /**
     * This method does release all used {@link ByteBuf}s. In concrete {@link #message} and {@link
     * #originalMessage}. The corresponding elements where also set to {@code null}.
     */
    public void releaseAll() {
        ReferenceCountUtil.safeRelease(message);
        ReferenceCountUtil.safeRelease(originalMessage);

        message = null;
        originalMessage = null;
    }

    public MessageId getId() {
        try {
            return MessageId.of(getPublicHeader().getId().toByteArray());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public UserAgent getUserAgent() {
        try {
            return new UserAgent(getPublicHeader().getUserAgent().toByteArray());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public int getNetworkId() {
        try {
            return getPublicHeader().getNetworkId();
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public CompressedPublicKey getSender() {
        try {
            return CompressedPublicKey.of(getPublicHeader().getSender().toByteArray());
        }
        catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public ProofOfWork getProofOfWork() {
        try {
            return ProofOfWork.of(getPublicHeader().getProofOfWork());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public CompressedPublicKey getRecipient() {
        try {
            return CompressedPublicKey.of(getPublicHeader().getRecipient().toByteArray());
        }
        catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public byte getHopCount() {
        try {
            return getPublicHeader().getHopCount().byteAt(0);
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void incrementHopCount() {
        // FIXME: implement
    }

    public Signature getSignature() {
        try {
            return new Signature(getPublicHeader().getSignature().toByteArray());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns an armed version of this envelope for sending it through untrustworthy channels.
     * <p>
     * {@code privateKey} must match message's sender.
     * <p>
     * Arming includes the following steps:
     * <ul>
     * <li>encrypted {@link #privateHeader} and encrypted {@link #body} will be signed with {@code privateKey}</li>
     * <li>{@link #privateHeader} and {@link #body} will be encrypted with recipient's public key (not implemented!)</li>
     * </ul>
     * <p>
     * <b>Note: Do not forget to release the original {@link #message}</b>
     *
     * @param privateKey message is signed with this key
     * @return the armed version of this envelope
     * @throws IllegalStateException if arming was not possible
     */
    public IntermediateEnvelope<T> arm(final CompressedPrivateKey privateKey) {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getPrivateHeader().writeDelimitedTo(outputStream);
            getBody().writeDelimitedTo(outputStream);

            final byte[] bytes = outputStream.toByteArray();

            // First sign and then encrypt. See: Krawczyk, Hugo. (2001). The Order of Encryption and Authentication for Protecting Communications (or: How Secure Is SSL?). 2139. 10.1007/3-540-44647-8_19.
            final byte[] signature = Crypto.signMessage(privateKey.toUncompressedKey(), bytes);
            final PublicHeader newPublicHeader = PublicHeader.newBuilder(getPublicHeader()).setSignature(ByteString.copyFrom(signature)).build();

            // FIXME: encrypt payload (message's id is currently not covered by signature and can
            //  therefore be forged. maybe we can prevent this by using the id as an initialisation
            //  vector for our encryption?)
            reverse(bytes);

            return of(newPublicHeader, bytes);
        }
        catch (final IOException | CryptoException e) {
            throw new IllegalStateException("Unable to arm message", e);
        }
    }

    /**
     * Returns an armed version of this envelope for sending it through untrustworthy channels.
     * <p>
     * {@code privateKey} must match message's sender.
     * <p>
     * Arming includes the following steps:
     * <ul>
     * <li>encrypted {@link #privateHeader} and encrypted {@link #body} will be signed with {@code privateKey}</li>
     * <li>{@link #privateHeader} and {@link #body} will be encrypted with recipient's public key (not implemented!)</li>
     * </ul>
     *
     * @param privateKey message is signed with this key
     * @return the armed version of this envelope
     * @throws IllegalStateException if arming was not possible
     */
    public IntermediateEnvelope<T> armAndRelease(final CompressedPrivateKey privateKey) {
        try {
            return arm(privateKey);
        }
        finally {
            releaseAll();
        }
    }

    /**
     * Returns a disarmed version of this envelope.
     * <p>
     * {@code privateKey} must match message's recipient (not implemented yet!).
     * <p>
     * Disarming includes the following steps:
     * <ul>
     * <li>the encrypted {@link #privateHeader} and encrypted {@link #body} will be decrypted with {@code privateKey} (not implemented yet!)
     * <li>the signed portions of the message ({@link #privateHeader} and {@link #body}) are verified against sender's public key.
     * </ul>
     * <p>
     * <b>Note: Do not forget to release the original {@link #message}</b>
     *
     * @return the disarmed version of this envelope
     * @throws IllegalStateException if disarming was not possible
     */
    @SuppressWarnings({ "java:S1172" })
    public IntermediateEnvelope<T> disarm(final CompressedPrivateKey privateKey) {
        try {
            final PublicKey sender = getSender().toUncompressedKey();
            final byte[] signature = getPublicHeader().getSignature().toByteArray();
            try (final ByteBufInputStream in = new ByteBufInputStream(getOrBuildInternalByteBuf())) {
                final byte[] bytes = in.readAllBytes();

                // FIXME: decrypt payload
                reverse(bytes);

                // verify signature
                if (signature.length == 0) {
                    throw new IllegalStateException("No signature");
                }
                if (Crypto.verifySignature(sender, bytes, signature)) {
                    try (final ByteArrayInputStream decryptedIn = new ByteArrayInputStream(bytes)) {
                        final PrivateHeader decryptedPrivateHeader = PrivateHeader.parseDelimitedFrom(decryptedIn);
                        final T decryptedBody = bodyFromInputStream(decryptedPrivateHeader.getType(), decryptedIn);

                        return of(getPublicHeader(), decryptedPrivateHeader, decryptedBody);
                    }
                }
                else {
                    throw new IllegalStateException("Invalid signature");
                }
            }
        }
        catch (final IOException | CryptoException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to arm message", e);
        }
    }

    /**
     * Returns a disarmed version of this envelope.
     * <p>
     * {@code privateKey} must match message's recipient (not implemented yet!).
     * <p>
     * Disarming includes the following steps:
     * <ul>
     * <li>the encrypted {@link #privateHeader} and encrypted {@link #body} will be decrypted with {@code privateKey} (not implemented yet!)
     * <li>the signed portions of the message ({@link #privateHeader} and {@link #body}) are verified against sender's public key.
     * </ul>
     *
     * @return the disarmed version of this envelope
     * @throws IllegalStateException if disarming was not possible
     */
    public IntermediateEnvelope<T> disarmAndRelease(final CompressedPrivateKey privateKey) {
        try {
            return disarm(privateKey);
        }
        finally {
            releaseAll();
        }
    }

    @SuppressWarnings("unchecked")
    private T bodyFromInputStream(final MessageType type, final InputStream in) throws IOException {
        switch (type) {
            case ACKNOWLEDGEMENT:
                return (T) Acknowledgement.parseDelimitedFrom(in);
            case APPLICATION:
                return (T) Application.parseDelimitedFrom(in);
            case UNITE:
                return (T) Unite.parseDelimitedFrom(in);
            case DISCOVERY:
                return (T) Discovery.parseDelimitedFrom(in);
            default:
                throw new IOException("Message is not of any known type.");
        }
    }

    /**
     * This method is just a placeholder and only mimics the behavior of a real encryption simulate
     * (shift/replace bytes).
     *
     * @param bytes
     */
    private static void reverse(final byte[] bytes) {
        if (bytes == null) {
            return;
        }
        int i = 0;
        int j = bytes.length - 1;
        byte tmp;
        while (j > i) {
            tmp = bytes[j];
            bytes[j] = bytes[i];
            bytes[i] = tmp;
            j--;
            i++;
        }
    }

    private ByteBuf proto2ByteBuf(ByteBuf byteBuf) throws IOException {
        if (byteBuf == null) {
            byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
        }
        else {
            byteBuf.resetReaderIndex();
            byteBuf.resetWriterIndex();
        }
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            publicHeader.writeDelimitedTo(outputStream);
            privateHeader.writeDelimitedTo(outputStream);
            body.writeDelimitedTo(outputStream);

            return byteBuf;
        }
        catch (final Exception e) {
            ReferenceCountUtil.safeRelease(byteBuf);
            throw new IOException(e);
        }
    }

    /**
     * Creates new acknowledgement message.
     *
     * @param networkId       the network id of the node server
     * @param sender          the public key of the node server
     * @param proofOfWork     the proof of work of the node server
     * @param recipient       the public key of the recipient
     * @param correspondingId the corresponding id of the previous join message
     */
    public static IntermediateEnvelope<Acknowledgement> acknowledgement(final int networkId,
                                                                        final CompressedPublicKey sender,
                                                                        final ProofOfWork proofOfWork,
                                                                        final CompressedPublicKey recipient,
                                                                        final MessageId correspondingId) {
        return of(
                buildPublicHeader(networkId, sender, proofOfWork, recipient),
                PrivateHeader.newBuilder()
                        .setType(ACKNOWLEDGEMENT)
                        .build(), Acknowledgement.newBuilder()
                        .setCorrespondingId(ByteString.copyFrom(correspondingId.byteArrayValue()))
                        .build()
        );
    }

    private static Protocol.PublicHeader buildPublicHeader(final int networkId,
                                                           final CompressedPublicKey sender,
                                                           final ProofOfWork proofOfWork,
                                                           final CompressedPublicKey recipient) {
        return PublicHeader.newBuilder()
                .setId(ByteString.copyFrom(randomMessageId().byteArrayValue()))
                .setUserAgent(ByteString.copyFrom(UserAgent.generate().getVersion().toBytes()))
                .setNetworkId(networkId)
                .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                .setProofOfWork(proofOfWork.intValue())
                .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                .build();
    }

    /**
     * Creates new application message.
     *
     * @param networkId   the network the sender belongs to
     * @param sender      the sender
     * @param proofOfWork the sender's proof of work
     * @param recipient   the recipient
     * @param type        the payload type
     * @param payload     the data to be sent
     */
    public static IntermediateEnvelope<Application> application(final int networkId,
                                                                final CompressedPublicKey sender,
                                                                final ProofOfWork proofOfWork,
                                                                final CompressedPublicKey recipient,
                                                                final String type,
                                                                final byte[] payload) {
        return of(
                buildPublicHeader(networkId, sender, proofOfWork, recipient),
                PrivateHeader.newBuilder()
                        .setType(APPLICATION)
                        .build(), Application.newBuilder()
                        .setType(type)
                        .setPayload(ByteString.copyFrom(payload))
                        .build()
        );
    }

    /**
     * Creates a new join message.
     *
     * @param networkId   the network of the joining node
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @param recipient   the public key of the node to join
     * @param joinTime    if {@code 0} greater then 0, node will join a children.
     */
    public static IntermediateEnvelope<Discovery> discovery(final int networkId,
                                                            final CompressedPublicKey sender,
                                                            final ProofOfWork proofOfWork,
                                                            final CompressedPublicKey recipient,
                                                            final long joinTime) {
        return of(
                buildPublicHeader(networkId, sender, proofOfWork, recipient),
                PrivateHeader.newBuilder()
                        .setType(DISCOVERY)
                        .build(), Discovery.newBuilder()
                        .setChildrenTime(joinTime)
                        .build()
        );
    }

    public static IntermediateEnvelope<Unite> unite(final int networkId,
                                                    final CompressedPublicKey sender,
                                                    final ProofOfWork proofOfWork,
                                                    final CompressedPublicKey recipient,
                                                    final CompressedPublicKey publicKey,
                                                    final InetSocketAddress address) {
        return of(
                buildPublicHeader(networkId, sender, proofOfWork, recipient),
                PrivateHeader.newBuilder()
                        .setType(UNITE)
                        .build(), Unite.newBuilder()
                        .setPublicKey(ByteString.copyFrom(publicKey.byteArrayValue()))
                        .setAddress(address.getHostString())
                        .setPort(ByteString.copyFrom(UnsignedShort.of(address.getPort()).toBytes()))
                        .build()
        );
    }
}

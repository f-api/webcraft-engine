package com.gameexpert.qa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** One durable H12g terminal envelope, uniquely bound to a world, source and logical key. */
@Entity
@Table(name = "`final_scene_h12g_terminals`", uniqueConstraints =
        @UniqueConstraint(name = "uk_h12g_terminal_binding_digest",
                columnNames = "binding_digest"))
public class FinalSceneH12gTerminal {
    public static final int SOURCE_IDENTITY_BYTES = 64;
    public static final int MAX_TERMINAL_KEY_BYTES = 256;
    public static final int MAX_PAYLOAD_BYTES = 262_144;
    public static final int MAX_ENVELOPE_BYTES = 524_288;
    public static final int MAC_KEY_BYTES = 32;

    private static final Pattern SOURCE_IDENTITY = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "source_identity", nullable = false, length = SOURCE_IDENTITY_BYTES)
    private String sourceIdentity;

    @Column(name = "terminal_key", nullable = false, length = MAX_TERMINAL_KEY_BYTES)
    private String terminalKey;

    /** Binary-safe exact binding identity, independent of database text collation. */
    @Column(name = "binding_digest", nullable = false, length = 64)
    private String bindingDigest;

    @Column(name = "key_epoch", nullable = false)
    private long keyEpoch;

    @Column(name = "key_identity", nullable = false, length = 64)
    private String keyIdentity;

    @Lob
    @Column(name = "authenticated_envelope", nullable = false)
    private String authenticatedEnvelope;

    protected FinalSceneH12gTerminal() {
    }

    public FinalSceneH12gTerminal(Long worldId, String sourceIdentity, String terminalKey,
            long keyEpoch, String keyIdentity, String authenticatedEnvelope) {
        requireBinding(worldId, sourceIdentity, terminalKey);
        requireKeyBinding(keyEpoch, keyIdentity);
        requireEnvelope(authenticatedEnvelope);
        this.worldId = worldId;
        this.sourceIdentity = sourceIdentity;
        this.terminalKey = terminalKey;
        this.bindingDigest = bindingDigest(worldId, sourceIdentity, terminalKey);
        this.keyEpoch = keyEpoch;
        this.keyIdentity = keyIdentity;
        this.authenticatedEnvelope = authenticatedEnvelope;
    }

    public Long id() {
        return id;
    }

    public Long worldId() {
        return worldId;
    }

    public String sourceIdentity() {
        return sourceIdentity;
    }

    public String terminalKey() {
        return terminalKey;
    }

    public String bindingDigest() {
        return bindingDigest;
    }

    public long keyEpoch() {
        return keyEpoch;
    }

    public String keyIdentity() {
        return keyIdentity;
    }

    public String authenticatedEnvelope() {
        return authenticatedEnvelope;
    }

    public long version() {
        return version;
    }

    public void replaceEnvelope(String replacement) {
        requireEnvelope(replacement);
        authenticatedEnvelope = replacement;
    }

    @PostLoad
    private void validateLoadedState() {
        requireBinding(worldId, sourceIdentity, terminalKey);
        requireKeyBinding(keyEpoch, keyIdentity);
        requireEnvelope(authenticatedEnvelope);
        if (id == null || id <= 0 || id == Long.MAX_VALUE || version < 0
                || version == Long.MAX_VALUE || !bindingDigest(worldId, sourceIdentity, terminalKey)
                        .equals(bindingDigest)) {
            throw new IllegalStateException("stored H12g terminal identity is invalid");
        }
    }

    public static String bindingDigest(Long worldId, String sourceIdentity, String terminalKey) {
        requireBinding(worldId, sourceIdentity, terminalKey);
        byte[] source = sourceIdentity.getBytes(StandardCharsets.US_ASCII);
        byte[] key = terminalKey.getBytes(StandardCharsets.UTF_8);
        ByteBuffer material = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + source.length
                + Integer.BYTES + key.length);
        material.putLong(worldId).putInt(source.length).put(source).putInt(key.length).put(key);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.array()));
        } catch (NoSuchAlgorithmException unavailableAlgorithm) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailableAlgorithm);
        }
    }

    public static void requireBinding(Long worldId, String sourceIdentity, String terminalKey) {
        requireWorldId(worldId);
        if (sourceIdentity == null || !SOURCE_IDENTITY.matcher(sourceIdentity).matches()
                || !isCanonicalTerminalKey(terminalKey)) {
            throw new IllegalArgumentException("H12g terminal binding is invalid");
        }
    }

    public static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("H12g terminal world ID is invalid");
        }
    }

    public static void requireKeyBinding(long keyEpoch, String keyIdentity) {
        if (keyEpoch <= 0 || keyEpoch == Long.MAX_VALUE || keyIdentity == null
                || !SOURCE_IDENTITY.matcher(keyIdentity).matches()) {
            throw new IllegalArgumentException("H12g terminal key binding is invalid");
        }
    }

    public static void requireEnvelope(String envelope) {
        if (envelope == null || envelope.isEmpty()
                || strictUtf8Length(envelope) > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("H12g terminal envelope is invalid");
        }
    }

    public static int strictUtf8Length(String value) {
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            return encoded.remaining();
        } catch (CharacterCodingException invalidUnicode) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean isCanonicalTerminalKey(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_TERMINAL_KEY_BYTES
                || strictUtf8Length(value) > MAX_TERMINAL_KEY_BYTES
                || !Normalizer.isNormalized(value, Normalizer.Form.NFC)
                || !value.equals(value.strip())) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current <= 0x1f || current == 0x7f) {
                return false;
            }
        }
        return true;
    }
}

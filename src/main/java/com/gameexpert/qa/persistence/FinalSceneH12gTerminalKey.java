package com.gameexpert.qa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Database-resident HMAC authority; key bytes never leave this entity. */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "`final_scene_h12g_terminal_keys`")
public class FinalSceneH12gTerminalKey {
    public static final long SINGLETON_ID = 1L;
    private static final String MAC_ALGORITHM = "HmacSHA256";

    @Id
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "key_epoch", nullable = false)
    private long keyEpoch;

    @Column(name = "key_identity", nullable = false, length = 64)
    private String keyIdentity;

    @Lob
    @Column(name = "mac_key", nullable = false)
    private byte[] macKey;

    protected FinalSceneH12gTerminalKey() {
    }

    public FinalSceneH12gTerminalKey(long keyEpoch, byte[] macKey) {
        if (keyEpoch <= 0 || keyEpoch == Long.MAX_VALUE) {
            throw new IllegalArgumentException("H12g terminal MAC key epoch is invalid");
        }
        if (macKey == null || macKey.length != FinalSceneH12gTerminal.MAC_KEY_BYTES) {
            throw new IllegalArgumentException("H12g terminal MAC key is invalid");
        }
        this.id = SINGLETON_ID;
        this.keyEpoch = keyEpoch;
        this.keyIdentity = identity(keyEpoch, macKey);
        this.macKey = Arrays.copyOf(macKey, macKey.length);
    }

    public Long id() {
        return id;
    }

    public long version() {
        return version;
    }

    public long keyEpoch() {
        return keyEpoch;
    }

    public String keyIdentity() {
        return keyIdentity;
    }

    public byte[] sign(byte[] authenticatedMaterial) {
        if (authenticatedMaterial == null) {
            throw new IllegalArgumentException("H12g authenticated material is required");
        }
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(macKey, MAC_ALGORITHM));
            return mac.doFinal(authenticatedMaterial);
        } catch (GeneralSecurityException unavailableAlgorithm) {
            throw new IllegalStateException("H12g terminal MAC is unavailable", unavailableAlgorithm);
        }
    }

    @PostLoad
    private void validateLoadedState() {
        if (!Long.valueOf(SINGLETON_ID).equals(id) || macKey == null
                || macKey.length != FinalSceneH12gTerminal.MAC_KEY_BYTES || version < 0
                || version == Long.MAX_VALUE || keyEpoch <= 0 || keyEpoch == Long.MAX_VALUE
                || !identity(keyEpoch, macKey).equals(keyIdentity)) {
            throw new IllegalStateException("stored H12g terminal MAC key is invalid");
        }
    }

    public static String identity(long keyEpoch, byte[] macKey) {
        if (keyEpoch <= 0 || keyEpoch == Long.MAX_VALUE || macKey == null
                || macKey.length != FinalSceneH12gTerminal.MAC_KEY_BYTES) {
            throw new IllegalArgumentException("H12g terminal MAC key identity input is invalid");
        }
        ByteBuffer material = ByteBuffer.allocate(Long.BYTES + macKey.length);
        material.putLong(keyEpoch).put(macKey);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.array()));
        } catch (NoSuchAlgorithmException unavailableAlgorithm) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailableAlgorithm);
        }
    }
}

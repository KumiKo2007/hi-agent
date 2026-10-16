package org.xhy.infrastructure.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xhy.infrastructure.exception.BusinessException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用环境变量提供的 256 位密钥加密 GitHub 凭据，不使用仓库内的静态密钥。 */
@Component
public class GitHubTokenCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public GitHubTokenCipher(@Value("${HI_AGENT_GITHUB_TOKEN_KEY:}") String encodedKey) {
        byte[] decoded = null;
        if (encodedKey != null && !encodedKey.isBlank()) {
            try {
                decoded = Base64.getDecoder().decode(encodedKey);
            } catch (IllegalArgumentException ignored) {
                // 只有连接操作需要密钥；未配置或格式错误时操作失败关闭。
            }
        }
        this.key = decoded != null && decoded.length == 32 ? decoded.clone() : null;
    }

    public String encrypt(String ownerId, String token) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, ownerId);
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new BusinessException("GitHub 凭据加密失败");
        }
    }

    public String decrypt(String ownerId, String encodedCiphertext) {
        requireKey();
        try {
            byte[] payload = Base64.getDecoder().decode(encodedCiphertext);
            if (payload.length <= NONCE_BYTES + TAG_BITS / 8) {
                throw new BusinessException("GitHub 凭据无效，请重新连接");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(payload, 0, nonce, 0, nonce.length);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, nonce, ownerId);
            return new String(cipher.doFinal(payload, NONCE_BYTES, payload.length - NONCE_BYTES),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException("GitHub 凭据无效，请重新连接");
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String ownerId) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(ownerId.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void requireKey() {
        if (key == null) {
            throw new BusinessException("GitHub 凭据加密密钥未配置或长度错误");
        }
    }
}

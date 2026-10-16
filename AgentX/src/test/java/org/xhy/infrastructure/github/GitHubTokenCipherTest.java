package org.xhy.infrastructure.github;

import org.junit.jupiter.api.Test;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 凭据密文必须绑定使用者，篡改后不能解密。 */
class GitHubTokenCipherTest {
    @Test
    void should_encryptWithAuthenticatedOwnerBoundCiphertext() {
        GitHubTokenCipher cipher = new GitHubTokenCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        String credential = "github_pat_test_only_token_12345";
        String encrypted = cipher.encrypt("owner-a", credential);

        assertNotEquals(credential, encrypted);
        assertEquals(credential, cipher.decrypt("owner-a", encrypted));
        assertThrows(BusinessException.class, () -> cipher.decrypt("owner-b", encrypted));

        byte[] tampered = Base64.getDecoder().decode(encrypted);
        tampered[tampered.length - 1] ^= 1;
        assertThrows(BusinessException.class,
                () -> cipher.decrypt("owner-a", Base64.getEncoder().encodeToString(tampered)));
    }
}

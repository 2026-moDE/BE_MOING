package com.moing.backend.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * H2에 없는 PostgreSQL 함수를 테스트에서 대신 채운다.
 *
 * <p>네이티브 쿼리는 운영 DB(PostgreSQL) 기준으로 쓰여 있고, 테스트는 H2 PostgreSQL 모드로
 * 돌린다. 모드만으로 메워지지 않는 함수가 있어 별칭으로 끼워 넣는다.
 *
 * <p>H2가 리플렉션으로 호출하므로 클래스와 메서드가 모두 public이어야 한다
 * (테스트 클래스 안에 두면 접근하지 못한다).
 */
public final class H2Functions {

    private H2Functions() {
    }

    /** {@code CREATE ALIAS md5 FOR "..."}로 등록해 쓴다. PostgreSQL md5()와 같은 값이다 */
    public static String md5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

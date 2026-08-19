package com.moing.backend.domain.comment.util;

/**
 * 삭제된 댓글에 노출할 닉네임을 만든다.
 * 댓글 id로만 결정되므로 같은 댓글은 조회할 때마다 항상 같은 닉네임이 나온다.
 */
public final class DeletedCommentNickname {

    private static final String[] ADJECTIVES = {
            "조용한", "느긋한", "성실한", "다정한", "엉뚱한",
            "용감한", "새침한", "게으른", "부지런한", "수줍은",
            "명랑한", "차분한", "엉큼한", "무심한", "따뜻한",
            "재빠른", "낙천적인", "진지한", "울적한", "호기심많은"
    };

    private static final String[] NOUNS = {
            "너구리", "고양이", "판다", "수달", "해달",
            "부엉이", "펭귄", "여우", "다람쥐", "고슴도치",
            "코알라", "알파카", "라쿤", "물개", "토끼",
            "햄스터", "오리", "거북이", "돌고래", "카피바라"
    };

    private static final String FALLBACK = "알 수 없음";

    private DeletedCommentNickname() {
    }

    public static String of(Long commentId) {
        if (commentId == null) {
            return FALLBACK;
        }

        // id를 그대로 쓰면 1, 2, 3번 댓글이 "느긋한 너구리", "성실한 너구리"처럼
        // 명사가 붙어버린다. 한 리뷰의 댓글 id는 대체로 연속이라 섞어서 뽑는다.
        long hash = mix(commentId);
        String adjective = ADJECTIVES[(int) Math.floorMod(hash, ADJECTIVES.length)];
        String noun = NOUNS[(int) Math.floorMod(hash / ADJECTIVES.length, NOUNS.length)];
        return adjective + " " + noun;
    }

    // splitmix64 finalizer - 이웃한 id가 전혀 다른 값으로 흩어지게 한다
    private static long mix(long value) {
        long hash = value * 0x9E3779B97F4A7C15L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        return hash ^ (hash >>> 31);
    }
}

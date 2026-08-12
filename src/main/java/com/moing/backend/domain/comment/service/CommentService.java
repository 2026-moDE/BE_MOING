package com.moing.backend.domain.comment.service;

import com.moing.backend.domain.comment.dto.CommentCreateRequest;
import com.moing.backend.domain.comment.dto.CommentCreateResponse;
import com.moing.backend.domain.comment.dto.CommentListResponse;
import com.moing.backend.domain.comment.dto.ReplyCreateRequest;
import com.moing.backend.domain.comment.entity.Comment;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    // 댓글 작성
    @Transactional
    public CommentCreateResponse createComment(Long userId, Long reviewId, CommentCreateRequest request) {
        Review review = getReview(reviewId);
        validateAccess(userId, review);

        Comment comment = commentRepository.save(Comment.builder()
                .reviewId(reviewId)
                .userId(userId)
                .content(request.content())
                .isSecret(request.isSecret())
                .build());

        return CommentCreateResponse.from(comment);
    }

    // 답글 작성 (친구 공개 리뷰에서만 가능)
    @Transactional
    public CommentCreateResponse createReply(Long userId, Long reviewId, Long commentId, ReplyCreateRequest request) {
        Review review = getReview(reviewId);

        if (!isFriendsOnly(review)) {
            throw new CustomException(ErrorCode.REPLY_NOT_ALLOWED);
        }
        validateAccess(userId, review);

        Comment parent = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        // 다른 리뷰의 댓글 id로 답글을 달 수 없다
        if (!parent.getReviewId().equals(reviewId)) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (parent.isReply()) {
            throw new CustomException(ErrorCode.NESTED_REPLY_NOT_ALLOWED);
        }

        Comment reply = commentRepository.save(Comment.builder()
                .reviewId(reviewId)
                .userId(userId)
                .parentId(parent.getId())
                .content(request.content())
                .build());

        return CommentCreateResponse.from(reply);
    }

    // 댓글 삭제 (본인 댓글만)
    @Transactional
    public void deleteComment(Long userId, Long reviewId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getReviewId().equals(reviewId) || comment.isDeleted()) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 답글이 달린 댓글은 parent_id FK 때문에 지울 수 없으므로 내용만 치환한다
        if (commentRepository.existsByParentId(comment.getId())) {
            comment.softDelete();
        } else {
            commentRepository.delete(comment);
        }
    }

    // 댓글 목록 조회 (커서 기반)
    @Transactional(readOnly = true)
    public CommentListResponse getComments(Long userId, Long reviewId, Long cursor, int limit) {
        Review review = getReview(reviewId);
        boolean friendsOnly = isFriendsOnly(review);

        // 친구 공개 리뷰의 댓글은 친구에게만 보여준다
        if (friendsOnly) {
            validateAccess(userId, review);
        }

        List<Comment> fetched = commentRepository.findTopLevelComments(
                reviewId, cursor, PageRequest.of(0, limit + 1));
        boolean hasNext = fetched.size() > limit;
        List<Comment> page = hasNext ? fetched.subList(0, limit) : fetched;

        // 답글은 친구 공개 리뷰에서만 함께 내려준다
        Map<Long, List<Comment>> repliesByParent = Map.of();
        if (friendsOnly && !page.isEmpty()) {
            List<Long> parentIds = page.stream().map(Comment::getId).toList();
            repliesByParent = commentRepository.findRepliesByParentIds(parentIds).stream()
                    .collect(Collectors.groupingBy(Comment::getParentId));
        }

        Map<Long, User> userMap = loadAuthors(page, repliesByParent);

        final Map<Long, List<Comment>> replies = repliesByParent;
        List<CommentListResponse.CommentItem> items = page.stream()
                .map(c -> toItem(c, userId, review.getUserId(), userMap,
                        friendsOnly
                                ? replies.getOrDefault(c.getId(), List.of()).stream()
                                        .map(r -> toItem(r, userId, review.getUserId(), userMap, null))
                                        .toList()
                                : null))
                .toList();

        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new CommentListResponse(items, nextCursor);
    }

    // 댓글과 답글 작성자를 한 번에 조회
    private Map<Long, User> loadAuthors(List<Comment> page, Map<Long, List<Comment>> repliesByParent) {
        List<Comment> all = new ArrayList<>(page);
        repliesByParent.values().forEach(all::addAll);

        List<Long> authorIds = all.stream().map(Comment::getUserId).distinct().toList();
        return userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private CommentListResponse.CommentItem toItem(Comment comment, Long viewerId, Long reviewAuthorId,
                                                   Map<Long, User> userMap,
                                                   List<CommentListResponse.CommentItem> replies) {
        User author = userMap.get(comment.getUserId());
        CommentListResponse.UserInfo userInfo = author != null
                ? new CommentListResponse.UserInfo(author.getNickname(), author.getProfileImageUrl(), author.getProfileUrl())
                : new CommentListResponse.UserInfo("알 수 없음", null, null);

        boolean isMine = viewerId.equals(comment.getUserId());

        // 비밀 댓글 내용은 리뷰 작성자와 댓글 작성자에게만 노출한다
        String content;
        if (comment.isDeleted()) {
            content = comment.getContent();
        } else if (comment.isSecret() && !isMine && !viewerId.equals(reviewAuthorId)) {
            content = null;
        } else {
            content = comment.getContent();
        }

        return new CommentListResponse.CommentItem(
                comment.getId(),
                comment.getParentId(),
                content,
                comment.isSecret(),
                isMine,
                comment.isDeleted(),
                userInfo,
                comment.getCreatedAt(),
                replies
        );
    }

    // 친구 공개 리뷰는 리뷰 작성자 본인과 친구만 접근할 수 있다
    private void validateAccess(Long userId, Review review) {
        if (!isFriendsOnly(review) || userId.equals(review.getUserId())) {
            return;
        }

        boolean isFriend = followRepository.existsByFollowerIdAndFollowingIdAndStatusIn(
                userId, review.getUserId(), List.of(FollowStatus.ACCEPTED));
        if (!isFriend) {
            throw new CustomException(ErrorCode.NOT_FRIEND_REVIEW);
        }
    }

    // visibility가 없는 기존 리뷰는 전체 공개로 취급한다
    private boolean isFriendsOnly(Review review) {
        return review.getVisibility() == Visibility.FRIENDS;
    }

    private Review getReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}

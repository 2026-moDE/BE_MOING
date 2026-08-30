package com.moing.backend.domain.comment.service;

import com.moing.backend.domain.comment.dto.CommentCreateRequest;
import com.moing.backend.domain.comment.dto.CommentCreateResponse;
import com.moing.backend.domain.comment.dto.CommentListResponse;
import com.moing.backend.domain.comment.dto.ReplyCreateRequest;
import com.moing.backend.domain.comment.entity.Comment;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.notification.entity.NotificationType;
import com.moing.backend.domain.notification.service.NotificationService;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
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
    private final PlaceRepository placeRepository;
    private final NotificationService notificationService;

    // 댓글 작성 (비밀 댓글은 친구 공개 리뷰에서만 가능)
    @Transactional
    public CommentCreateResponse createComment(Long userId, Long reviewId, CommentCreateRequest request) {
        Review review = getReview(reviewId);
        validateAccess(userId, review);

        boolean isSecret = request.isSecret() != null && request.isSecret();
        if (isSecret && !isFriendsOnly(review)) {
            throw new CustomException(ErrorCode.SECRET_COMMENT_NOT_ALLOWED);
        }

        Comment comment = commentRepository.save(Comment.builder()
                .reviewId(reviewId)
                .userId(userId)
                .content(request.content())
                .isSecret(request.isSecret())
                .build());

        // 내 리뷰에 내가 단 댓글은 알리지 않는다
        if (!userId.equals(review.getUserId())) {
            notifyComment(review, review.getUserId());
        }

        return CommentCreateResponse.from(comment);
    }

    // 답글 작성 (공개 범위와 무관하게 가능)
    @Transactional
    public CommentCreateResponse createReply(Long userId, Long reviewId, Long commentId, ReplyCreateRequest request) {
        Review review = getReview(reviewId);
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
        // 자리표시만 남은 댓글에는 답글을 이어 달 수 없다
        if (parent.isDeleted()) {
            throw new CustomException(ErrorCode.DELETED_COMMENT_REPLY_NOT_ALLOWED);
        }

        Comment reply = commentRepository.save(Comment.builder()
                .reviewId(reviewId)
                .userId(userId)
                .parentId(parent.getId())
                .content(request.content())
                .build());

        // 댓글 작성자에게 답글 알림 (내 댓글에 내가 단 답글은 제외)
        if (!userId.equals(parent.getUserId())) {
            notifyReply(review, parent.getUserId());
        }
        // 리뷰 작성자에게도 알리되, 댓글 작성자와 같은 사람이면 두 번 보내지 않는다
        if (!userId.equals(review.getUserId()) && !review.getUserId().equals(parent.getUserId())) {
            notifyComment(review, review.getUserId());
        }

        return CommentCreateResponse.from(reply);
    }

    // 리뷰에 댓글이 달렸음을 리뷰 작성자에게 알린다
    private void notifyComment(Review review, Long targetUserId) {
        String placeName = getPlaceName(review);
        if (placeName == null) return;

        notificationService.send(targetUserId, NotificationType.COMMENT,
                review.getPlaceId(), review.getId(),
                "작성하신 리뷰에 새로운 댓글이 달렸어요",
                "'" + placeName + "'에 작성하신 리뷰에 새로운 댓글이 달렸어요.");
    }

    // 댓글에 답글이 달렸음을 댓글 작성자에게 알린다 (타입은 COMMENT로 같고 문구만 다르다)
    private void notifyReply(Review review, Long targetUserId) {
        String placeName = getPlaceName(review);
        if (placeName == null) return;

        notificationService.send(targetUserId, NotificationType.COMMENT,
                review.getPlaceId(), review.getId(),
                "작성하신 댓글에 새로운 답글이 달렸어요",
                "'" + placeName + "'에 작성하신 댓글에 새로운 답글이 달렸어요.");
    }

    private String getPlaceName(Review review) {
        return placeRepository.findById(review.getPlaceId())
                .map(Place::getName)
                .orElse(null);
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

        // 답글이 달린 댓글을 실제로 지우면 그 답글의 parent_id가 없는 id를 가리키게 되고,
        // 최상위 조회에도 답글 조회에도 걸리지 않아 아무 화면에 안 나오는 유령으로 남는다.
        // 그래서 자리표시만 남긴다. (DB에 FK가 걸려 있지는 않아 지우는 것 자체는 막히지 않는다)
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

        // 친구 공개 리뷰의 댓글은 친구에게만 보여준다 (전체 공개면 그대로 통과)
        validateAccess(userId, review);

        List<Comment> fetched = commentRepository.findTopLevelComments(
                reviewId, cursor, PageRequest.of(0, limit + 1));
        boolean hasNext = fetched.size() > limit;
        List<Comment> page = hasNext ? fetched.subList(0, limit) : fetched;

        // 답글은 공개 범위와 무관하게 함께 내려준다
        Map<Long, List<Comment>> repliesByParent = Map.of();
        if (!page.isEmpty()) {
            List<Long> parentIds = page.stream().map(Comment::getId).toList();
            repliesByParent = commentRepository.findRepliesByParentIds(parentIds).stream()
                    .collect(Collectors.groupingBy(Comment::getParentId));
        }

        Map<Long, User> userMap = loadAuthors(page, repliesByParent);

        final Map<Long, List<Comment>> replies = repliesByParent;
        List<CommentListResponse.CommentItem> items = page.stream()
                .map(c -> toItem(c, userId, review.getUserId(), userMap,
                        replies.getOrDefault(c.getId(), List.of()).stream()
                                .map(r -> toItem(r, userId, review.getUserId(), userMap, null))
                                .toList()))
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
        // 삭제된 댓글은 작성자를 감추고 고정 문구 + 기본 이미지(null)로 대체한다.
        // 자리표시용이므로 is_secret / is_mine 도 내려 삭제 버튼이나 비밀 댓글 표시가 뜨지 않게 한다.
        if (comment.isDeleted()) {
            return new CommentListResponse.CommentItem(
                    comment.getId(),
                    comment.getParentId(),
                    Comment.DELETED_CONTENT,
                    false,
                    false,
                    true,
                    new CommentListResponse.UserInfo(Comment.DELETED_NICKNAME, null, null),
                    comment.getCreatedAt(),
                    replies
            );
        }

        User author = userMap.get(comment.getUserId());
        CommentListResponse.UserInfo userInfo = author != null
                ? new CommentListResponse.UserInfo(author.getNickname(), author.getProfileImageUrl(), author.getProfileUrl())
                : new CommentListResponse.UserInfo("알 수 없음", null, null);

        boolean isMine = viewerId.equals(comment.getUserId());

        // 비밀 댓글 내용은 리뷰 작성자와 댓글 작성자에게만 노출한다
        String content = comment.isSecret() && !isMine && !viewerId.equals(reviewAuthorId)
                ? null
                : comment.getContent();

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

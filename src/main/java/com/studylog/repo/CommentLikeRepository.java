package com.studylog.repo;

import com.studylog.model.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByCommentIdAndUsername(Long commentId, String username);

    long countByCommentId(Long commentId);

    // 派生 deleteBy* 默认不带事务，必须显式声明
    @Transactional
    void deleteByCommentId(Long commentId);

    @Transactional
    void deleteByCommentIdIn(Collection<Long> commentIds);

    /** 批量统计多条留言的点赞数，返回 [commentId, count]。 */
    @Query("SELECT l.commentId, COUNT(l) FROM CommentLike l WHERE l.commentId IN :ids GROUP BY l.commentId")
    List<Object[]> countByCommentIds(@Param("ids") Collection<Long> ids);

    /** 批量查某用户在多条留言中已赞的留言 id。 */
    @Query("SELECT l.commentId FROM CommentLike l WHERE l.commentId IN :ids AND l.username = :username")
    List<Long> findLikedCommentIds(@Param("ids") Collection<Long> ids, @Param("username") String username);
}

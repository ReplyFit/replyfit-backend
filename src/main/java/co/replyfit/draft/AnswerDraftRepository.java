package co.replyfit.draft;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswerDraftRepository extends JpaRepository<AnswerDraft, Long> {

    Optional<AnswerDraft> findByInquiryId(Long inquiryId);

    @Query("select count(d) from AnswerDraft d where d.inquiry.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            delete from AnswerDraft d where d.inquiry.id in
            (select i.id from Inquiry i where i.createdAt < :threshold)
            """)
    int deleteByInquiryOlderThan(@Param("threshold") LocalDateTime threshold);
}

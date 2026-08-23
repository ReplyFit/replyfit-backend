package co.replyfit.inquiry;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    @Query("""
            select i from Inquiry i
            where i.user.id = :userId
              and (:category is null or i.category = :category)
              and (:status is null or i.status = :status)
              and (:q is null or i.content like concat('%', :q, '%') or i.productName like concat('%', :q, '%'))
            order by i.receivedAt desc
            """)
    Page<Inquiry> search(@Param("userId") Long userId,
                         @Param("category") InquiryCategory category,
                         @Param("status") InquiryStatus status,
                         @Param("q") String q,
                         Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatusIn(Long userId, List<InquiryStatus> statuses);

    long countByUserIdAndReceivedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

    List<Inquiry> findByUserIdAndReceivedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

    List<Inquiry> findTop5ByUserIdOrderByReceivedAtDesc(Long userId);

    @Query("""
            select i.category as category, count(i) as cnt from Inquiry i
            where i.user.id = :userId and i.category is not null
            group by i.category order by cnt desc
            """)
    List<CategoryCount> countByCategory(@Param("userId") Long userId);

    interface CategoryCount {
        InquiryCategory getCategory();

        long getCnt();
    }

    @Modifying
    @Query("delete from Inquiry i where i.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

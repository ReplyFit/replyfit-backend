package co.replyfit.review;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("""
            select r from Review r
            where r.user.id = :userId
              and (:sentiment is null or r.sentiment = :sentiment)
              and (:q is null
                   or r.content like concat('%', cast(:q as string), '%')
                   or r.productName like concat('%', cast(:q as string), '%'))
            order by r.writtenAt desc
            """)
    Page<Review> search(@Param("userId") Long userId,
                        @Param("sentiment") Sentiment sentiment,
                        @Param("q") String q,
                        Pageable pageable);

    long countByUserId(Long userId);

    List<Review> findByUserIdAndWrittenAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

    @Query("select avg(r.rating) from Review r where r.user.id = :userId")
    Double averageRating(@Param("userId") Long userId);

    long countByUserIdAndSentiment(Long userId, Sentiment sentiment);

    @Modifying
    @Query("delete from Review r where r.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

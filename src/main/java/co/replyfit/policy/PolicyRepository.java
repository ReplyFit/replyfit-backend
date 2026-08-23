package co.replyfit.policy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    List<Policy> findByUserIdOrderByTypeAscUpdatedAtDesc(Long userId);

    List<Policy> findByUserIdAndTypeIn(Long userId, List<PolicyType> types);

    long countByUserId(Long userId);
}

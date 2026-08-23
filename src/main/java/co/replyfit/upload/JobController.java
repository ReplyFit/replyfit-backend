package co.replyfit.upload;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.common.ApiException;
import co.replyfit.upload.JobProgressService.JobState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "04. 작업 진행률", description = "업로드·재생성 작업의 실시간 진행률 (Redis, 6시간 보관)")
public class JobController {

    private final JobProgressService jobProgressService;

    public JobController(JobProgressService jobProgressService) {
        this.jobProgressService = jobProgressService;
    }

    /** 프론트엔드가 폴링해 업로드·리포트 작업의 실시간 진행률을 표시한다. */
    @Operation(summary = "작업 진행률 조회",
            description = "status: PROCESSING → COMPLETED/FAILED. processed+failed가 total에 도달하면 완료입니다. 1~2초 간격 폴링을 권장합니다.")
    @GetMapping("/{jobId}")
    public ResponseEntity<JobState> get(@PathVariable String jobId) {
        JobState state = jobProgressService.get(jobId);
        if (state == null) {
            throw ApiException.notFound("작업을 찾을 수 없습니다: " + jobId);
        }
        return ResponseEntity.ok(state);
    }
}

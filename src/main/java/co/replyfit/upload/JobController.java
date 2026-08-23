package co.replyfit.upload;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.common.ApiException;
import co.replyfit.upload.JobProgressService.JobState;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobProgressService jobProgressService;

    public JobController(JobProgressService jobProgressService) {
        this.jobProgressService = jobProgressService;
    }

    /** 프론트엔드가 폴링해 업로드·리포트 작업의 실시간 진행률을 표시한다. */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobState> get(@PathVariable String jobId) {
        JobState state = jobProgressService.get(jobId);
        if (state == null) {
            throw ApiException.notFound("작업을 찾을 수 없습니다: " + jobId);
        }
        return ResponseEntity.ok(state);
    }
}

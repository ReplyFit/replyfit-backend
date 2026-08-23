package co.replyfit.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.auth.AuthUser;
import co.replyfit.dashboard.DashboardService.DashboardStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "08. 대시보드", description = "셀러 대시보드 통계 (Redis 캐시 60초 — 워커가 초안 생성을 마치면 즉시 무효화)")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "대시보드 통계 조회",
            description = "문의·초안·리뷰 집계, 8주 추이, 카테고리 분포, 최근 문의 5건, 절감 시간 추정을 한 번에 반환합니다.")
    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> stats(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(dashboardService.getStats(me.id()));
    }
}

package co.replyfit.common;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외 → HTTP 응답 변환을 한곳에서 담당한다.
 *
 * <p><b>주의 — 표준 예외를 여기서 직접 처리해야 하는 이유(#54)</b><br>
 * {@code @ExceptionHandler(Exception.class)} 포괄 핸들러가 붙은 순간, 이 어드바이스는
 * Spring의 {@code DefaultHandlerExceptionResolver}보다 <b>먼저</b> 실행된다. 즉 Spring이
 * 기본으로 400/404/405/415로 변환해 주던 표준 MVC 예외들도 아래에 명시적으로 적지 않으면
 * 전부 포괄 핸들러에 걸려 500으로 나간다. 실제로 잘못된 URL·깨진 JSON·누락된 업로드 파일이
 * 모두 "서버 오류"로 응답되고 있었다.
 *
 * <p>새로운 표준 예외가 500으로 새는 것을 발견하면 이 목록에 계속 추가할 것.
 * 포괄 핸들러는 <b>진짜 예상 못 한 예외 전용</b>으로 남겨 둔다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(int status, String message, OffsetDateTime timestamp) {
        static ErrorResponse of(HttpStatus status, String message) {
            return new ErrorResponse(status.value(), message, OffsetDateTime.now());
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return respond(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 파일이 최대 크기(20MB)를 초과했습니다.");
    }

    // ── 표준 MVC 예외: 클라이언트 잘못이므로 4xx로 응답한다 ──────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return clientError(HttpStatus.NOT_FOUND, "요청하신 경로를 찾을 수 없습니다.", e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return clientError(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다.", e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return clientError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다.", e);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return clientError(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다: " + e.getName(), e);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return clientError(HttpStatus.BAD_REQUEST, "필수 파라미터가 누락되었습니다: " + e.getParameterName(), e);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return clientError(HttpStatus.BAD_REQUEST, "필수 파일이 누락되었습니다: " + e.getRequestPartName(), e);
    }

    /**
     * 요청 본문 파싱 실패. 예외 메시지에 본문 일부(마스킹 전 문의 원문일 수 있다)가
     * 섞이므로 그대로 응답에 싣지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return clientError(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다. JSON 형식을 확인해 주세요.", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    }

    /** 클라이언트 잘못은 장애가 아니므로 스택트레이스 없이 debug로만 남긴다. */
    private static ResponseEntity<ErrorResponse> clientError(HttpStatus status, String message, Exception e) {
        log.debug("{} — {}", status, e.getMessage());
        return respond(status, message);
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}

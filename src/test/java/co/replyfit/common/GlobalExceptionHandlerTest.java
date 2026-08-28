package co.replyfit.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.replyfit.auth.JwtService;

/**
 * 클라이언트 잘못이 500으로 응답되던 회귀(#54) 방지.
 *
 * <p>포괄 핸들러({@code @ExceptionHandler(Exception.class)})가 Spring 기본 예외 해석기보다
 * 먼저 실행되기 때문에, 표준 MVC 예외를 명시적으로 처리하지 않으면 조용히 500으로 되돌아간다.
 * 실제 컨트롤러 대신 최소 프로브 컨트롤러를 두어 어드바이스 동작만 검증한다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GlobalExceptionHandler — 클라이언트 오류 상태 코드")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    /** @WebMvcTest는 Filter 빈까지 올린다. addFilters=false라 실행되진 않으므로 생성만 되면 된다. */
    @MockitoBean
    private JwtService jwtService;

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/{id}")
        String byId(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("예상 못 한 실패");
        }

        @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        String json(@RequestBody Payload body) {
            return "ok";
        }

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String upload(@RequestPart("file") MultipartFile file) {
            return "ok";
        }

        record Payload(String content) {
        }
    }

    @Test
    @DisplayName("없는 경로는 404")
    void unmappedPathIsNotFound() throws Exception {
        mvc.perform(get("/definitely/not/mapped"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(delete("/probe/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    @DisplayName("Content-Type이 맞지 않으면 415")
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        mvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("abc"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("경로 변수 타입이 맞지 않으면 400 — 어느 값인지 알려준다")
    void pathVariableTypeMismatchIsBadRequest() throws Exception {
        mvc.perform(get("/probe/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다: id"));
    }

    @Test
    @DisplayName("깨진 JSON 본문은 400")
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/probe/json").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("업로드 파일이 누락되면 400 — 어느 파트인지 알려준다")
    void missingUploadPartIsBadRequest() throws Exception {
        mvc.perform(multipart("/probe/upload").file(new MockMultipartFile("other", new byte[] { 1 })))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 파일이 누락되었습니다: file"));
    }

    @Test
    @DisplayName("본문 파싱 실패 응답에 요청 원문이 새지 않는다")
    void malformedJsonDoesNotEchoRequestBody() throws Exception {
        String body = mvc.perform(post("/probe/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"010-1234-5678 김철수\""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("010-1234-5678").doesNotContain("김철수");
    }

    @Test
    @DisplayName("예상 못 한 예외는 그대로 500 — 포괄 핸들러 유지")
    void unexpectedExceptionStaysServerError() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("오류 응답 형태는 status·message·timestamp 유지 — 프론트 계약")
    void errorResponseShapeIsUnchanged() throws Exception {
        mvc.perform(get("/definitely/not/mapped"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

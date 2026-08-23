package co.replyfit.upload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import co.replyfit.common.ApiException;
import co.replyfit.common.PiiMasker;
import co.replyfit.inquiry.Inquiry;
import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.review.Review;
import co.replyfit.review.ReviewRepository;
import co.replyfit.review.Sentiment;
import co.replyfit.user.User;

/**
 * 문의·리뷰 CSV 업로드 파서 (MVP 핵심 기능 ①).
 *
 * 처리 흐름: CSV 파싱 → 개인정보 즉시 마스킹 → 저장.
 * 원본(마스킹 전) 데이터는 어디에도 저장하지 않는다.
 */
@Service
public class CsvIngestService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
    private static final List<DateTimeFormatter> DATE_ONLY_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"));

    private final InquiryRepository inquiryRepository;
    private final ReviewRepository reviewRepository;

    public CsvIngestService(InquiryRepository inquiryRepository, ReviewRepository reviewRepository) {
        this.inquiryRepository = inquiryRepository;
        this.reviewRepository = reviewRepository;
    }

    /** @return 저장된 문의 ID 목록 */
    @Transactional
    public List<Long> ingestInquiries(User user, MultipartFile file) {
        List<Long> ids = new ArrayList<>();
        for (CSVRecord row : parse(file)) {
            String content = pick(row, "문의내용", "내용", "문의");
            if (content == null || content.isBlank()) {
                continue;
            }
            String rawName = pick(row, "고객명", "구매자명", "이름", "작성자");
            String rawOrderNo = pick(row, "주문번호", "주문 번호");
            PiiMasker.MaskResult masked = PiiMasker.maskContent(content, rawName);
            int maskedCount = masked.maskedCount()
                    + (rawName != null && !rawName.isBlank() ? 1 : 0)
                    + (rawOrderNo != null && !rawOrderNo.isBlank() ? 1 : 0);

            Inquiry inquiry = new Inquiry(
                    user,
                    orDefault(pick(row, "채널", "판매채널"), "기타"),
                    PiiMasker.maskName(rawName),
                    PiiMasker.maskOrderNo(rawOrderNo),
                    pick(row, "상품명", "제품명", "상품"),
                    masked.masked(),
                    maskedCount,
                    parseDateTime(pick(row, "문의일시", "일시", "날짜", "작성일시")));
            ids.add(inquiryRepository.save(inquiry).getId());
        }
        if (ids.isEmpty()) {
            throw ApiException.badRequest(
                    "처리할 문의가 없습니다. CSV에 '문의내용' 열이 포함되어 있는지 확인해 주세요.");
        }
        return ids;
    }

    /** @return 저장된 리뷰 건수 */
    @Transactional
    public int ingestReviews(User user, MultipartFile file) {
        int count = 0;
        for (CSVRecord row : parse(file)) {
            String content = pick(row, "리뷰내용", "내용", "리뷰");
            if (content == null || content.isBlank()) {
                continue;
            }
            int rating = parseRating(pick(row, "평점", "별점"));
            PiiMasker.MaskResult masked = PiiMasker.maskContent(content, pick(row, "작성자", "고객명"));
            reviewRepository.save(new Review(
                    user,
                    pick(row, "상품명", "제품명", "상품"),
                    rating,
                    masked.masked(),
                    sentimentOf(rating, content),
                    extractIssueKeywords(content),
                    parseDateTime(pick(row, "작성일시", "일시", "날짜"))));
            count++;
        }
        if (count == 0) {
            throw ApiException.badRequest(
                    "처리할 리뷰가 없습니다. CSV에 '리뷰내용' 열이 포함되어 있는지 확인해 주세요.");
        }
        return count;
    }

    private List<CSVRecord> parse(MultipartFile file) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .get();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVParser.parse(reader, format)) {
            return parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            throw ApiException.badRequest("CSV 파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private static String pick(CSVRecord row, String... headers) {
        Map<String, String> map = row.toMap();
        for (String header : headers) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (entry.getKey() != null
                        && entry.getKey().replace("﻿", "").trim().equalsIgnoreCase(header)
                        && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue().trim();
                }
            }
        }
        return null;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (Exception ignored) {
                // 다음 포맷 시도
            }
        }
        for (DateTimeFormatter formatter : DATE_ONLY_FORMATS) {
            try {
                return java.time.LocalDate.parse(trimmed, formatter).atStartOfDay();
            } catch (Exception ignored) {
                // 다음 포맷 시도
            }
        }
        return LocalDateTime.now();
    }

    private static int parseRating(String value) {
        if (value == null) {
            return 3;
        }
        try {
            int rating = (int) Math.round(Double.parseDouble(value.trim()));
            return Math.max(1, Math.min(5, rating));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    private static Sentiment sentimentOf(int rating, String content) {
        String lower = content.toLowerCase(Locale.KOREAN);
        boolean negativeWords = lower.contains("별로") || lower.contains("실망") || lower.contains("최악")
                || lower.contains("환불") || lower.contains("불만");
        if (rating <= 2 || (rating == 3 && negativeWords)) {
            return Sentiment.NEGATIVE;
        }
        if (rating >= 4 && !negativeWords) {
            return Sentiment.POSITIVE;
        }
        return Sentiment.NEUTRAL;
    }

    /** 리뷰에서 의류·잡화 이슈 키워드를 추출한다 (VOC 리포트 재료). */
    static String extractIssueKeywords(String content) {
        List<String> keywords = new ArrayList<>();
        if (content.contains("사이즈") || content.contains("크") || content.contains("작") || content.contains("핏")) {
            keywords.add("사이즈");
        }
        if (content.contains("색") || content.contains("컬러")) {
            keywords.add("색상");
        }
        if (content.contains("배송") || content.contains("늦") || content.contains("느리")) {
            keywords.add("배송");
        }
        if (content.contains("재질") || content.contains("원단") || content.contains("퀄리티") || content.contains("품질")) {
            keywords.add("품질");
        }
        if (content.contains("보풀") || content.contains("올") || content.contains("박음질") || content.contains("불량")) {
            keywords.add("불량");
        }
        return keywords.isEmpty() ? null : String.join(",", keywords);
    }
}

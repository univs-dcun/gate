package ai.univs.gate.shared.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateTimeUtil 테스트")
class DateTimeUtilTest {

    /**
     * 검증 전제: DB는 모두 UTC 저장.
     * toStartOfDay / toEndOfDay 는 사용자 timezone 의 하루 경계를 UTC 로 변환해서 반환해야 한다.
     *
     * 예) Asia/Seoul (UTC+9), 2024-06-15 조회 요청
     *   → 시작: 2024-06-15T00:00:00+09:00  →  UTC  2024-06-14T15:00:00
     *   → 종료: 2024-06-15T23:59:59+09:00  →  UTC  2024-06-15T14:59:59
     */

    @Nested
    @DisplayName("toStartOfDay(dateStr, timezone) — 하루 시작을 UTC 로 변환")
    class ToStartOfDay {

        @Test
        @DisplayName("[Asia/Seoul, UTC+9] 날짜 시작 → UTC 로 9시간 당겨진다")
        void seoulTimezone_returnsUtcStartOfDay() {
            // 2024-06-15T00:00:00+09:00 → 2024-06-14T15:00:00 UTC
            LocalDateTime result = DateTimeUtil.toStartOfDay("2024-06-15", "Asia/Seoul");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 14, 15, 0, 0));
        }

        @Test
        @DisplayName("[UTC] 날짜 시작 → 변환 없이 00:00:00 반환")
        void utcTimezone_returnsUtcMidnight() {
            LocalDateTime result = DateTimeUtil.toStartOfDay("2024-06-15", "UTC");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 0, 0, 0));
        }

        @Test
        @DisplayName("[America/New_York, UTC-4 EDT] 날짜 시작 → UTC 로 4시간 더해진다")
        void newYorkTimezone_returnsUtcStartOfDay() {
            // 2024-06-15T00:00:00-04:00 → 2024-06-15T04:00:00 UTC (EDT)
            LocalDateTime result = DateTimeUtil.toStartOfDay("2024-06-15", "America/New_York");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 4, 0, 0));
        }

        @ParameterizedTest(name = "timezone=\"{0}\" → UTC 폴백 → 00:00:00")
        @NullAndEmptySource
        @DisplayName("timezone 이 null 또는 빈 문자열이면 UTC 폴백 → 00:00:00")
        void nullOrEmptyTimezone_fallbackToUtc(String timezone) {
            LocalDateTime result = DateTimeUtil.toStartOfDay("2024-06-15", timezone);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 0, 0, 0));
        }

        @ParameterizedTest(name = "timezone=\"{0}\" → UTC 폴백 → 00:00:00")
        @ValueSource(strings = {"INVALID_ZONE", "KST", "한국", "GMT+99"})
        @DisplayName("파싱 불가능한 timezone 이면 UTC 폴백 → 00:00:00")
        void invalidTimezone_fallbackToUtc(String timezone) {
            LocalDateTime result = DateTimeUtil.toStartOfDay("2024-06-15", timezone);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 0, 0, 0));
        }

        @Test
        @DisplayName("연초 경계(1월 1일) 정상 처리 — Asia/Seoul")
        void startOfYear_seoulTimezone() {
            // 2024-01-01T00:00:00+09:00 → 2023-12-31T15:00:00 UTC
            LocalDateTime result = DateTimeUtil.toStartOfDay("2024-01-01", "Asia/Seoul");

            assertThat(result).isEqualTo(LocalDateTime.of(2023, 12, 31, 15, 0, 0));
        }

        @Test
        @DisplayName("연말 경계(12월 31일) 정상 처리 — Asia/Seoul")
        void endOfYear_seoulTimezone() {
            // 2024-12-31T00:00:00+09:00 → 2024-12-30T15:00:00 UTC
            LocalDateTime result = DateTimeUtil.toStartOfDay("2024-12-31", "Asia/Seoul");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 12, 30, 15, 0, 0));
        }
    }

    @Nested
    @DisplayName("toEndOfDay(dateStr, timezone) — 하루 끝(23:59:59)을 UTC 로 변환")
    class ToEndOfDay {

        @Test
        @DisplayName("[Asia/Seoul, UTC+9] 날짜 끝 → UTC 로 9시간 당겨진다")
        void seoulTimezone_returnsUtcEndOfDay() {
            // 2024-06-15T23:59:59+09:00 → 2024-06-15T14:59:59 UTC
            LocalDateTime result = DateTimeUtil.toEndOfDay("2024-06-15", "Asia/Seoul");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 14, 59, 59));
            assertThat(result.getNano()).isEqualTo(0);
        }

        @Test
        @DisplayName("[UTC] 날짜 끝 → 변환 없이 23:59:59 반환 (나노초=0)")
        void utcTimezone_returnsEndOfDayAtMidnight() {
            LocalDateTime result = DateTimeUtil.toEndOfDay("2024-06-15", "UTC");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 23, 59, 59));
            assertThat(result.getNano()).isEqualTo(0);
        }

        @Test
        @DisplayName("[America/New_York, UTC-4 EDT] 날짜 끝 → UTC 로 4시간 더해진다")
        void newYorkTimezone_returnsUtcEndOfDay() {
            // 2024-06-15T23:59:59-04:00 → 2024-06-16T03:59:59 UTC (EDT)
            LocalDateTime result = DateTimeUtil.toEndOfDay("2024-06-15", "America/New_York");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 16, 3, 59, 59));
            assertThat(result.getNano()).isEqualTo(0);
        }

        @ParameterizedTest(name = "timezone=\"{0}\" → UTC 폴백 → 23:59:59")
        @NullAndEmptySource
        @DisplayName("timezone 이 null 또는 빈 문자열이면 UTC 폴백 → 23:59:59")
        void nullOrEmptyTimezone_fallbackToUtc(String timezone) {
            LocalDateTime result = DateTimeUtil.toEndOfDay("2024-06-15", timezone);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 23, 59, 59));
            assertThat(result.getNano()).isEqualTo(0);
        }

        @ParameterizedTest(name = "timezone=\"{0}\" → UTC 폴백 → 23:59:59")
        @ValueSource(strings = {"INVALID_ZONE", "KST", "한국", "GMT+99"})
        @DisplayName("파싱 불가능한 timezone 이면 UTC 폴백 → 23:59:59")
        void invalidTimezone_fallbackToUtc(String timezone) {
            LocalDateTime result = DateTimeUtil.toEndOfDay("2024-06-15", timezone);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 23, 59, 59));
            assertThat(result.getNano()).isEqualTo(0);
        }

        @Test
        @DisplayName("연초 경계(1월 1일) 정상 처리 — Asia/Seoul")
        void startOfYear_seoulTimezone() {
            // 2024-01-01T23:59:59+09:00 → 2024-01-01T14:59:59 UTC
            LocalDateTime result = DateTimeUtil.toEndOfDay("2024-01-01", "Asia/Seoul");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 1, 14, 59, 59));
        }

        @Test
        @DisplayName("연말 경계(12월 31일) 정상 처리 — Asia/Seoul")
        void endOfYear_seoulTimezone() {
            // 2024-12-31T23:59:59+09:00 → 2024-12-31T14:59:59 UTC
            LocalDateTime result = DateTimeUtil.toEndOfDay("2024-12-31", "Asia/Seoul");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 12, 31, 14, 59, 59));
        }
    }

    @Nested
    @DisplayName("toStartOfDay / toEndOfDay 쌍 검증")
    class PairValidation {

        @Test
        @DisplayName("같은 날짜·타임존에서 StartOfDay < EndOfDay (UTC 기준)")
        void startOfDay_isBeforeEndOfDay() {
            String date = "2024-06-15";
            String timezone = "Asia/Seoul";

            LocalDateTime start = DateTimeUtil.toStartOfDay(date, timezone);
            LocalDateTime end = DateTimeUtil.toEndOfDay(date, timezone);

            assertThat(start).isBefore(end);
        }

        @Test
        @DisplayName("UTC 기준 End - Start 는 정확히 23시간 59분 59초 차이")
        void endMinusStart_equals23Hours59Minutes59Seconds() {
            String date = "2024-06-15";
            String timezone = "Asia/Seoul";

            LocalDateTime start = DateTimeUtil.toStartOfDay(date, timezone);
            LocalDateTime end = DateTimeUtil.toEndOfDay(date, timezone);

            long diffSeconds = java.time.Duration.between(start, end).getSeconds();
            assertThat(diffSeconds).isEqualTo(23 * 3600 + 59 * 60 + 59);
        }

        @Test
        @DisplayName("UTC timezone 에서는 StartOfDay 와 EndOfDay 가 같은 날짜(년/월/일)")
        void utcTimezone_startAndEnd_haveSameDate() {
            String date = "2024-06-15";

            LocalDateTime start = DateTimeUtil.toStartOfDay(date, "UTC");
            LocalDateTime end = DateTimeUtil.toEndOfDay(date, "UTC");

            assertThat(start.toLocalDate()).isEqualTo(end.toLocalDate());
        }
    }
}

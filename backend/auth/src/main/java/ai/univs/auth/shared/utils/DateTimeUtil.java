package ai.univs.auth.shared.utils;

import org.springframework.util.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;

    private static ZoneId parseZoneId(String timezone) {
        if (!StringUtils.hasText(timezone)) return DEFAULT_ZONE;
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return DEFAULT_ZONE;
        }
    }

    /**
     * 클라이언트의 로컬 시간을 UTC로 변환 (DB 저장용)
     */
    public static LocalDateTime toUtc(LocalDateTime localDateTime, String clientTimeZone) {
        if (localDateTime == null) return null;
        ZoneId clientZone = parseZoneId(clientTimeZone);
        return localDateTime.atZone(clientZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * DB의 UTC 시간을 클라이언트 타임존으로 변환 (응답용)
     */
    public static LocalDateTime fromUtc(LocalDateTime utcDateTime, String clientTimeZone) {
        if (utcDateTime == null) return null;
        ZoneId clientZone = parseZoneId(clientTimeZone);
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(clientZone)
                .toLocalDateTime();
    }

    /**
     * 현재 시간을 UTC로 반환
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 날짜 문자열("yyyy-MM-dd")을 해당 타임존 기준 하루의 시작 시각으로 변환
     */
    public static LocalDateTime toStartOfDay(String dateStr, String timezone) {
        ZoneId zoneId = parseZoneId(timezone);
        LocalDate localDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return localDate.atStartOfDay(zoneId)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * 날짜 문자열("yyyy-MM-dd")을 해당 타임존 기준 하루의 끝 시각(23:59:59)으로 변환
     */
    public static LocalDateTime toEndOfDay(String dateStr, String timezone) {
        ZoneId zoneId = parseZoneId(timezone);
        LocalDate localDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return localDate.atTime(LocalTime.MAX.withNano(0))
                .atZone(zoneId)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}

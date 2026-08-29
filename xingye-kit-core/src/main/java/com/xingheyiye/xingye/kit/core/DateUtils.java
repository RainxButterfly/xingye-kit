/*
 * Copyright (c) 2026 星河一叶 (RainxButterfly)
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-19
 */
package com.xingheyiye.xingye.kit.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * 日期时间工具类，基于 java.time（JSR-310）提供常用格式化、解析、新旧日期互转与相对时间描述。
 *
 * <p>适用场景：接口出入参的时间格式化与解析、遗留 {@code java.util.Date} 与
 * {@code LocalDateTime} 的相互转换、月首月末计算、面向用户的相对时间展示等。
 *
 * <p>线程安全性：全部为无状态静态方法；{@link DateTimeFormatter} 本身不可变、线程安全，
 * 因此 {@link #FORMATTER_DATE} 与 {@link #FORMATTER_DATETIME} 可被并发共享。
 *
 * <p>使用示例：
 * <pre>{@code
 * DateUtils.formatNow(DateUtils.PATTERN_DATETIME);          // "2026-08-19 12:00:00"
 * DateUtils.toLocalDateTime(new Date());                    // Date -> LocalDateTime
 * DateUtils.relativeTime(LocalDateTime.now().minusHours(2)); // "2小时前"
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-19
 */
public final class DateUtils {

    /** 日期格式：yyyy-MM-dd */
    public static final String PATTERN_DATE = "yyyy-MM-dd";

    /** 日期时间格式：yyyy-MM-dd HH:mm:ss */
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";

    /** 日期格式化器，不可变对象，线程安全 */
    public static final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern(PATTERN_DATE);

    /** 日期时间格式化器，不可变对象，线程安全 */
    public static final DateTimeFormatter FORMATTER_DATETIME = DateTimeFormatter.ofPattern(PATTERN_DATETIME);

    /** 分钟内的秒数上限，达到该值后进位为“N 分钟” */
    private static final long SECONDS_PER_MINUTE = 60L;

    /** 小时内的分钟数上限，达到该值后进位为“N 小时” */
    private static final long MINUTES_PER_HOUR = 60L;

    /** 天内的小时数上限，达到该值后进位为“N 天” */
    private static final long HOURS_PER_DAY = 24L;

    /** 相对时间展示的天数阈值：超过 30 天回退为 yyyy-MM-dd 的绝对日期 */
    private static final long DAYS_THRESHOLD_FOR_ABSOLUTE = 30L;

    /**
     * 私有构造器，防止工具类被实例化。
     */
    private DateUtils() {
    }

    /**
     * 获取当前系统时间。
     *
     * @return 当前系统默认时区的 {@code LocalDateTime}，恒不为 null
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 按指定格式格式化时间。
     *
     * @param time 待格式化的时间，可为 null
     * @param pattern 格式串，如 {@link #PATTERN_DATETIME}，不能为 null 或空串
     * @return 格式化结果；{@code time} 为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException 当 {@code pattern} 为 {@code null} 或空串时抛出
     */
    public static String format(LocalDateTime time, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern 不能为空");
        }
        if (time == null) {
            return null;
        }
        return DateTimeFormatter.ofPattern(pattern).format(time);
    }

    /**
     * 按指定格式格式化当前时间。
     *
     * @param pattern 格式串，如 {@link #PATTERN_DATETIME}，不能为 null 或空串
     * @return 当前时间的格式化结果，恒不为 null
     * @throws IllegalArgumentException 当 {@code pattern} 为 {@code null} 或空串时抛出
     */
    public static String formatNow(String pattern) {
        return format(LocalDateTime.now(), pattern);
    }

    /**
     * 按指定格式将文本解析为 {@link LocalDateTime}。
     *
     * <p>注意：当文本与格式不匹配时，底层抛出的
     * {@code java.time.format.DateTimeParseException}（RuntimeException）
     * 会被原样向上透传，由调用方自行捕获处理。
     *
     * @param text 时间文本，如 "2026-08-19 12:00:00"，可为 null
     * @param pattern 格式串，如 {@link #PATTERN_DATETIME}，不能为 null 或空串
     * @return 解析结果；{@code text} 为 {@code null} 或空串时返回 {@code null}
     * @throws IllegalArgumentException 当 {@code pattern} 为 {@code null} 或空串时抛出
     * @throws java.time.format.DateTimeParseException 当 {@code text} 与 {@code pattern} 不匹配时原样透传
     */
    public static LocalDateTime parse(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern 不能为空");
        }
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 将 {@link LocalDateTime} 转换为 {@link Date}，使用系统默认时区。
     *
     * @param time 待转换时间，可为 null
     * @return 转换结果；{@code time} 为 {@code null} 时返回 {@code null}
     */
    public static Date toDate(LocalDateTime time) {
        return toDate(time, ZoneId.systemDefault());
    }

    /**
     * 将 {@link LocalDateTime} 按指定时区转换为 {@link Date}。
     *
     * @param time 待转换时间，可为 null
     * @param zoneId 目标时区，不能为 null
     * @return 转换结果；{@code time} 为 {@code null} 时返回 {@code null}
     * @throws NullPointerException 当 {@code zoneId} 为 {@code null} 时抛出
     */
    public static Date toDate(LocalDateTime time, ZoneId zoneId) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(zoneId).toInstant());
    }

    /**
     * 将 {@link Date} 转换为 {@link LocalDateTime}，使用系统默认时区。
     *
     * @param date 待转换时间，可为 null
     * @return 转换结果；{@code date} 为 {@code null} 时返回 {@code null}
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        return toLocalDateTime(date, ZoneId.systemDefault());
    }

    /**
     * 将 {@link Date} 按指定时区转换为 {@link LocalDateTime}。
     *
     * @param date 待转换时间，可为 null
     * @param zoneId 目标时区，不能为 null
     * @return 转换结果；{@code date} 为 {@code null} 时返回 {@code null}
     * @throws NullPointerException 当 {@code zoneId} 为 {@code null} 时抛出
     */
    public static LocalDateTime toLocalDateTime(Date date, ZoneId zoneId) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(zoneId).toLocalDateTime();
    }

    /**
     * 获取当月第一天 00:00:00 的时间。
     *
     * @param time 参考时间，可为 null
     * @return 当月首日零点；{@code time} 为 {@code null} 时返回 {@code null}
     */
    public static LocalDateTime beginOfMonth(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.withDayOfMonth(1).toLocalDate().atStartOfDay();
    }

    /**
     * 获取当月最后一天 23:59:59.999999999 的时间。
     *
     * @param time 参考时间，可为 null
     * @return 当月末日的 {@code LocalTime.MAX} 时刻；{@code time} 为 {@code null} 时返回 {@code null}
     */
    public static LocalDateTime endOfMonth(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(LocalTime.MAX);
    }

    /**
     * 将时间转换为面向用户的相对时间描述。
     *
     * <p>过去：刚刚 / N秒前 / N分钟前 / N小时前 / N天前，超过 30 天回退为 yyyy-MM-dd 格式；
     * 未来：N秒后 / N分钟后 / N小时后 / N天后，超过 30 天同样回退为 yyyy-MM-dd 格式。
     *
     * @param time 目标时间，可为 null
     * @return 相对时间描述；{@code time} 为 {@code null} 时返回 {@code null}
     */
    public static String relativeTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        long diffSeconds = Duration.between(time, LocalDateTime.now()).getSeconds();
        if (diffSeconds >= 0L) {
            return describePast(diffSeconds, time);
        }
        return describeFuture(-diffSeconds, time);
    }

    /**
     * 描述过去的相对时间。
     *
     * @param seconds 距今已过去的秒数，恒为非负数
     * @param time 目标时间，用于超过阈值后的绝对日期回退
     * @return 相对时间描述，恒不为 null
     */
    private static String describePast(long seconds, LocalDateTime time) {
        if (seconds < 1L) {
            return "刚刚";
        }
        if (seconds < SECONDS_PER_MINUTE) {
            return seconds + "秒前";
        }
        long minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes < MINUTES_PER_HOUR) {
            return minutes + "分钟前";
        }
        long hours = minutes / MINUTES_PER_HOUR;
        if (hours < HOURS_PER_DAY) {
            return hours + "小时前";
        }
        long days = hours / HOURS_PER_DAY;
        if (days <= DAYS_THRESHOLD_FOR_ABSOLUTE) {
            return days + "天前";
        }
        return FORMATTER_DATE.format(time.toLocalDate());
    }

    /**
     * 描述未来的相对时间。
     *
     * @param seconds 距今未来的秒数，恒为正数
     * @param time 目标时间，用于超过阈值后的绝对日期回退
     * @return 相对时间描述，恒不为 null
     */
    private static String describeFuture(long seconds, LocalDateTime time) {
        if (seconds < SECONDS_PER_MINUTE) {
            return seconds + "秒后";
        }
        long minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes < MINUTES_PER_HOUR) {
            return minutes + "分钟后";
        }
        long hours = minutes / MINUTES_PER_HOUR;
        if (hours < HOURS_PER_DAY) {
            return hours + "小时后";
        }
        long days = hours / HOURS_PER_DAY;
        if (days <= DAYS_THRESHOLD_FOR_ABSOLUTE) {
            return days + "天后";
        }
        return FORMATTER_DATE.format(time.toLocalDate());
    }
}

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
 * @since 2026-08-20
 */
package com.xingheyiye.xingye.kit.id;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UUID 与随机标识工具类，提供标准 UUID、无连字符 UUID、高性能随机串与趋势有序标识四种生成策略。
 *
 * <p>适用场景：全局唯一标识（traceId、幂等键）、数据库主键（推荐 {@link #ordered()}）、
 * 临时令牌、低碰撞要求的随机串等。
 *
 * <p>线程安全性：全部为无状态静态方法，且底层使用 {@link UUID#randomUUID()} 与
 * {@link ThreadLocalRandom}（线程本地实例），线程安全。
 *
 * <p>使用示例：
 * <pre>{@code
 * UuidUtils.random();   // "550e8400-e29b-41d4-a716-446655440000"
 * UuidUtils.simple();   // "550e8400e29b41d4a716446655440000"
 * UuidUtils.fast();     // "9f8e7d6c5b4a39281706f5e4d3c2b1a0"
 * UuidUtils.ordered();  // "19cf6b1e0a9f3d2c1b4a5e6f7a8b9c0d1e"
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-20
 */
public final class UuidUtils {

    /** ordered() 时间前缀的十六进制位数：12 位 hex = 48 bit，可表示约 2.8e14 毫秒（覆盖至公元 10889 年） */
    private static final int ORDERED_TIME_HEX_LENGTH = 12;

    /** ordered() 随机后缀的十六进制位数：20 位，与前缀合计 32 位，与 simple() 长度一致 */
    private static final int ORDERED_RANDOM_HEX_LENGTH = 20;

    /** UUID 标准格式中的连字符 */
    private static final String UUID_SEPARATOR = "-";

    /**
     * 私有构造器，防止工具类被实例化。
     */
    private UuidUtils() {
    }

    /**
     * 生成标准 UUID V4 字符串（带连字符，36 位）。
     *
     * @return 形如 {@code 550e8400-e29b-41d4-a716-446655440000} 的字符串，恒不为 null
     */
    public static String random() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成无连字符的 UUID V4 字符串（32 位十六进制）。
     *
     * @return 形如 {@code 550e8400e29b41d4a716446655440000} 的 32 位字符串，恒不为 null
     */
    public static String simple() {
        return UUID.randomUUID().toString().replace(UUID_SEPARATOR, "");
    }

    /**
     * 生成 32 位十六进制随机串。
     *
     * <p><b>注意：非加密安全！</b>本方法基于 {@link ThreadLocalRandom} 拼接随机 long，
     * 仅具备极低的碰撞概率，适用于对安全性无要求、追求生成速度的场景
     * （如日志追踪号、临时缓存键）；任何涉及安全语义（令牌、密钥、验证码）的场景
     * 请改用 {@link #random()}（底层为加密安全的 SecureRandom）。
     *
     * @return 32 位十六进制随机串，恒不为 null
     */
    public static String fast() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format("%016x%016x", random.nextLong(), random.nextLong());
    }

    /**
     * 生成 32 位“趋势有序”的十六进制标识：当前毫秒时间戳的 12 位小写十六进制前缀
     * 加 20 位随机十六进制后缀。
     *
     * <p>时间戳位于高位，使字符串前缀随时间单调递增，整体近似按时间有序；
     * 该特性可显著减少 InnoDB 聚簇索引的页分裂与随机写，适合直接作为 MySQL 主键。
     * 同一毫秒内及跨毫秒均可能存在乱序（由随机后缀决定），不保证严格递增。
     *
     * @return 32 位十六进制字符串（小写），恒不为 null
     */
    public static String ordered() {
        String timePart = String.format("%0" + ORDERED_TIME_HEX_LENGTH + "x", System.currentTimeMillis());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String randomPart = String.format("%016x%016x", random.nextLong(), random.nextLong())
                .substring(0, ORDERED_RANDOM_HEX_LENGTH);
        return timePart + randomPart;
    }
}

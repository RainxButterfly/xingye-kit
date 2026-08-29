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

import com.xingheyiye.xingye.kit.core.IdGenerator;

/**
 * 雪花算法（Snowflake）ID 生成器，生成趋势有序的 64 位纯数字 ID，并以字符串形式对外提供。
 *
 * <p>适用场景：分布式系统主键生成（如 MySQL 主键，趋势有序可减少页分裂）、
 * 订单号/流水号等需要全局唯一且大体递增的场景。
 *
 * <p>64 位布局（以默认 5/5/12 位配置为例）：
 * <pre>
 *  位：  63        62.....................22  21.....17  16.....12  11........ 0
 *       +---------+------------------------+----------+----------+------------+
 *       | 符号位 0 |  时间戳(41 bit, 毫秒)  | 数据中心  |  机器 ID  |   序列号    |
 *       |  1 bit  |  相对 TW_EPOCH 的偏移   |  5 bit   |  5 bit   |  12 bit    |
 *       +---------+------------------------+----------+----------+------------+
 * </pre>
 * 三段位数（datacenterIdBits + workerIdBits + sequenceBits）之和不超过 22，
 * 为时间戳保留至少 41 位（自 2025-01-01 UTC 起约可用 69 年）。
 *
 * <p>时钟回拨处理：回拨幅度不超过 {@code maxBackwardsMillis} 时自旋等待时钟追平后继续发号；
 * 超过阈值则抛出 {@code IllegalStateException} 拒绝发号，避免产生重复 ID。
 *
 * <p>线程安全性：发号方法以 {@code synchronized} 修饰，单实例内线程安全；
 * 多实例部署时需保证各实例的 workerId + datacenterId 组合全局唯一。
 *
 * <p>使用示例：
 * <pre>{@code
 * Snowflake snowflake = new Snowflake(1L, 1L);
 * String id = snowflake.nextId();                             // 例如 "5985039247872001"
 * long workerId = snowflake.extractWorkerId(Long.parseLong(id));
 * long createTime = snowflake.extractTimestamp(Long.parseLong(id));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-20
 */
public class Snowflake implements IdGenerator {

    /**
     * 起始纪元：2025-01-01T00:00:00Z（UTC）对应的毫秒时间戳。
     * 所有生成 ID 中的时间戳均为相对该时刻的偏移量，
     * 选择较近的纪元可在 41 位时间戳内获得更长的可用年限（约至 2094 年）。
     */
    public static final long TW_EPOCH = 1735689600000L;

    /** 便捷构造使用的默认数据中心 ID 位数：5 位，可表示 0~31 */
    private static final long DEFAULT_DATACENTER_ID_BITS = 5L;

    /** 便捷构造使用的默认机器 ID 位数：5 位，可表示 0~31 */
    private static final long DEFAULT_WORKER_ID_BITS = 5L;

    /** 便捷构造使用的默认序列号位数：12 位，单机单毫秒最多可生成 4096 个 ID */
    private static final long DEFAULT_SEQUENCE_BITS = 12L;

    /** 便捷构造使用的默认最大可容忍时钟回拨毫秒数：5 毫秒 */
    private static final long DEFAULT_MAX_BACKWARDS_MILLIS = 5L;

    /** 三段位数之和的上限：1(符号) + 41(时间戳) + 22 = 64，保证时间戳至少有 41 位 */
    private static final long MAX_ID_FIELD_BITS = 22L;

    /** 机器 ID：同一数据中心内唯一标识一台机器 */
    private final long workerId;

    /** 数据中心 ID：跨机房/跨区域部署时用于区分不同数据中心 */
    private final long datacenterId;

    /** 数据中心 ID 占用的位数，构造后不可变 */
    private final long datacenterIdBits;

    /** 机器 ID 占用的位数，构造后不可变 */
    private final long workerIdBits;

    /** 序列号占用的位数，构造后不可变 */
    private final long sequenceBits;

    /** 最大可容忍的时钟回拨毫秒数，超出即拒绝发号 */
    private final long maxBackwardsMillis;

    /** 机器 ID 的左移位数（序列号占据低位） */
    private final long workerIdShift;

    /** 数据中心 ID 的左移位数（序列号 + 机器 ID 之后） */
    private final long datacenterIdShift;

    /** 时间戳的左移位数（序列号 + 机器 ID + 数据中心 ID 之后） */
    private final long timestampShift;

    /** 序列号掩码，用于序列号回绕 */
    private final long sequenceMask;

    /** 机器 ID 掩码，用于从 ID 中反解机器 ID */
    private final long workerIdMask;

    /** 数据中心 ID 掩码，用于从 ID 中反解数据中心 ID */
    private final long datacenterIdMask;

    /** 当前毫秒内已使用的序列号 */
    private long sequence = 0L;

    /** 最近一次发号使用的时间戳（毫秒），-1 表示尚未发过号 */
    private long lastTimestamp = -1L;

    /**
     * 便捷构造器：使用默认位宽 5/5/12 与默认回拨容忍 5 毫秒。
     *
     * @param workerId 机器 ID，范围 [0, 31]
     * @param datacenterId 数据中心 ID，范围 [0, 31]
     * @throws IllegalArgumentException 当任一 ID 超出对应范围时抛出
     */
    public Snowflake(long workerId, long datacenterId) {
        this(workerId, datacenterId, DEFAULT_DATACENTER_ID_BITS, DEFAULT_WORKER_ID_BITS,
                DEFAULT_SEQUENCE_BITS, DEFAULT_MAX_BACKWARDS_MILLIS);
    }

    /**
     * 全参构造器，允许自定义各段位宽与时钟回拨容忍度。
     *
     * @param workerId 机器 ID，范围 [0, 2^workerIdBits - 1]
     * @param datacenterId 数据中心 ID，范围 [0, 2^datacenterIdBits - 1]
     * @param datacenterIdBits 数据中心 ID 位数，必须 &gt;= 1
     * @param workerIdBits 机器 ID 位数，必须 &gt;= 1
     * @param sequenceBits 序列号位数，必须 &gt;= 1
     * @param maxBackwardsMillis 最大可容忍时钟回拨毫秒数，必须 &gt;= 0
     * @throws IllegalArgumentException 当任一参数不满足上述约束
     *                                （含三段位数之和超过 22）时抛出
     */
    public Snowflake(long workerId, long datacenterId, long datacenterIdBits,
                     long workerIdBits, long sequenceBits, long maxBackwardsMillis) {
        if (datacenterIdBits < 1L || workerIdBits < 1L || sequenceBits < 1L) {
            throw new IllegalArgumentException("各段位数必须 >= 1：datacenterIdBits=" + datacenterIdBits
                    + ", workerIdBits=" + workerIdBits + ", sequenceBits=" + sequenceBits);
        }
        long totalBits = datacenterIdBits + workerIdBits + sequenceBits;
        if (totalBits > MAX_ID_FIELD_BITS) {
            throw new IllegalArgumentException("datacenterIdBits + workerIdBits + sequenceBits 不能超过 "
                    + MAX_ID_FIELD_BITS + "（需为时间戳保留至少 41 位），当前: " + totalBits);
        }
        long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
        if (datacenterId < 0L || datacenterId > maxDatacenterId) {
            throw new IllegalArgumentException("datacenterId 超出 [0, " + maxDatacenterId + "] 范围，当前: " + datacenterId);
        }
        long maxWorkerId = -1L ^ (-1L << workerIdBits);
        if (workerId < 0L || workerId > maxWorkerId) {
            throw new IllegalArgumentException("workerId 超出 [0, " + maxWorkerId + "] 范围，当前: " + workerId);
        }
        if (maxBackwardsMillis < 0L) {
            throw new IllegalArgumentException("maxBackwardsMillis 不能为负数，当前: " + maxBackwardsMillis);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.datacenterIdBits = datacenterIdBits;
        this.workerIdBits = workerIdBits;
        this.sequenceBits = sequenceBits;
        this.maxBackwardsMillis = maxBackwardsMillis;
        this.workerIdShift = sequenceBits;
        this.datacenterIdShift = sequenceBits + workerIdBits;
        this.timestampShift = sequenceBits + workerIdBits + datacenterIdBits;
        this.sequenceMask = -1L ^ (-1L << sequenceBits);
        this.workerIdMask = -1L ^ (-1L << workerIdBits);
        this.datacenterIdMask = -1L ^ (-1L << datacenterIdBits);
    }

    /**
     * 生成下一个 64 位雪花 ID。
     *
     * <p>说明：Java 不允许仅返回值类型不同的同名同参方法，
     * 因此核心发号方法命名为 {@code nextLongId()}，
     * 而 {@link #nextId()} 作为 {@link IdGenerator} 的字符串实现委托本方法。
     *
     * <p>时钟回拨幅度不超过 {@code maxBackwardsMillis} 时自旋等待时钟追平；
     * 序列号在同一毫秒内用尽时自旋等待下一毫秒。
     *
     * @return 全局唯一且趋势递增的 64 位 ID，恒不为 {@code null}（原始类型）
     * @throws IllegalStateException 当时钟回拨幅度超过 {@code maxBackwardsMillis} 时抛出，
     *                               消息中包含回拨毫秒数
     */
    public synchronized long nextLongId() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= maxBackwardsMillis) {
                // 小幅回拨：自旋等待时钟追平最后一次发号时间
                timestamp = tilNextMillis(lastTimestamp);
            } else {
                throw new IllegalStateException("检测到时钟回拨 " + offset
                        + " ms，超出最大容忍阈值 " + maxBackwardsMillis + " ms，拒绝生成 ID");
            }
        }
        if (timestamp == lastTimestamp) {
            // 同一毫秒内序列号递增并在用尽时回绕，回绕后等待下一毫秒
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0L) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - TW_EPOCH) << timestampShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    /**
     * 生成下一个雪花 ID 并以字符串形式返回（{@link IdGenerator} 的实现）。
     *
     * @return 十进制字符串形式的雪花 ID，恒不为 null
     */
    @Override
    public String nextId() {
        return String.valueOf(nextLongId());
    }

    /**
     * 按当前实例位宽从 ID 中反解生成时刻的毫秒时间戳。
     *
     * @param id 雪花 ID（应为本实例或相同位宽实例生成的 ID）
     * @return 生成时刻的系统毫秒时间戳（已加上起始纪元 TW_EPOCH）
     */
    public long extractTimestamp(long id) {
        return (id >> timestampShift) + TW_EPOCH;
    }

    /**
     * 按当前实例位宽从 ID 中反解机器 ID。
     *
     * @param id 雪花 ID（应为本实例或相同位宽实例生成的 ID）
     * @return 生成该 ID 的机器 ID
     */
    public long extractWorkerId(long id) {
        return (id >> workerIdShift) & workerIdMask;
    }

    /**
     * 按当前实例位宽从 ID 中反解数据中心 ID。
     *
     * @param id 雪花 ID（应为本实例或相同位宽实例生成的 ID）
     * @return 生成该 ID 的数据中心 ID
     */
    public long extractDatacenterId(long id) {
        return (id >> datacenterIdShift) & datacenterIdMask;
    }

    /**
     * 自旋等待到指定时间戳之后的第一个毫秒。
     *
     * @param lastTimestamp 最后一次发号的时间戳（毫秒）
     * @return 严格大于 {@code lastTimestamp} 的当前毫秒时间戳
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前系统毫秒时间戳。
     *
     * @return 当前毫秒时间戳
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }
}

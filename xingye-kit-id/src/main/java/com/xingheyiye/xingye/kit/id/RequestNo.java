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
 * @since 2026-08-21
 */
package com.xingheyiye.xingye.kit.id;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求号生成器，按“时间戳 + 节点号 + 回绕序列”三段拼接生成业务请求编号。
 *
 * <p>适用场景：网关/服务入口的请求流水号、对账单号、工单号等
 * 需要可读性强、含时间信息且单节点内不重复的场景。
 *
 * <p>唯一性说明：默认配置下（时间戳 17 位 + 节点 2 位 + 序列 4 位）
 * 单节点同一毫秒内可生成 10000 个不重复请求号；不同节点需配置不同 node 以保证全局唯一。
 *
 * <p>线程安全性：配置字段均为 {@code final}，序列使用 {@link AtomicLong} 原子递增，
 * {@link DateTimeFormatter} 不可变，因此实例可被多线程并发共享调用。
 *
 * <p>使用示例：
 * <pre>{@code
 * RequestNo generator = RequestNo.builder()
 *         .node("01")
 *         .seqLength(4)
 *         .build();
 * String requestNo = generator.next(); // 例如 "260830123456789010001"
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-21
 */
public final class RequestNo {

    /** 默认节点号 */
    public static final String DEFAULT_NODE = "00";

    /** 默认序列长度 */
    public static final int DEFAULT_SEQ_LENGTH = 4;

    /** 默认时间戳格式：年(2)月(2)日(2)时(2)分(2)秒(2)毫秒(3)，共 17 位 */
    public static final String DEFAULT_TIMESTAMP_PATTERN = "yyMMddHHmmssSSS";

    /** 序列长度下限 */
    private static final int MIN_SEQ_LENGTH = 1;

    /** 序列长度上限：超过 6 位时单毫秒容量已达百万级，继续加长意义有限且使编号过长 */
    private static final int MAX_SEQ_LENGTH = 6;

    /** 节点号，构建后不可变 */
    private final String node;

    /** 序列位数，构建后不可变 */
    private final int seqLength;

    /** 序列回绕模数：10^seqLength */
    private final long sequenceModulo;

    /** 时间戳格式化器，构建后不可变 */
    private final DateTimeFormatter formatter;

    /** 自增序列号，AtomicLong 保证并发递增；floorMod 实现自然回绕 */
    private final AtomicLong sequence = new AtomicLong(0L);

    /**
     * 私有构造器，请通过 {@link #builder()} 创建实例。
     *
     * @param node 节点号，非空
     * @param seqLength 序列位数，范围 [1, 6]
     * @param formatter 时间戳格式化器，非 null
     */
    private RequestNo(String node, int seqLength, DateTimeFormatter formatter) {
        this.node = node;
        this.seqLength = seqLength;
        long modulo = 1L;
        // 循环乘法计算 10^seqLength，避免浮点误差
        for (int i = 0; i < seqLength; i++) {
            modulo *= 10L;
        }
        this.sequenceModulo = modulo;
        this.formatter = formatter;
    }

    /**
     * 创建构建器。
     *
     * @return 全新的 {@link Builder} 实例，恒不为 null
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 生成下一个请求号：按时间戳格式化当前时间 + 节点号 + 序列（左补零，满模回绕）。
     *
     * @return 请求号字符串，恒不为 null
     */
    public String next() {
        long seq = Math.floorMod(sequence.getAndIncrement(), sequenceModulo);
        String timestamp = formatter.format(LocalDateTime.now());
        String seqPart = String.format(Locale.ROOT, "%0" + seqLength + "d", seq);
        return timestamp + node + seqPart;
    }

    /**
     * RequestNo 构建器，支持链式配置节点号、序列长度与时间戳格式。
     */
    public static final class Builder {

        /** 节点号 */
        private String node = DEFAULT_NODE;

        /** 序列位数 */
        private int seqLength = DEFAULT_SEQ_LENGTH;

        /** 时间戳格式串 */
        private String timestampPattern = DEFAULT_TIMESTAMP_PATTERN;

        /**
         * 私有构造器，请通过 {@link RequestNo#builder()} 获取实例。
         */
        private Builder() {
        }

        /**
         * 设置节点号，用于多实例部署时区分发号节点。
         *
         * @param node 节点号，不能为 null 或空串（建议为固定长度的数字串，如 "00"、"01"）
         * @return 当前构建器，便于链式调用，恒不为 null
         * @throws IllegalArgumentException 当 {@code node} 为 {@code null} 或空串时抛出
         */
        public Builder node(String node) {
            if (node == null || node.isEmpty()) {
                throw new IllegalArgumentException("node 不能为空");
            }
            this.node = node;
            return this;
        }

        /**
         * 设置序列位数，决定同一毫秒内的发号容量（10^seqLength 个）。
         *
         * @param seqLength 序列位数，范围 [1, 6]
         * @return 当前构建器，便于链式调用，恒不为 null
         * @throws IllegalArgumentException 当 {@code seqLength} 不在 [1, 6] 范围内时抛出
         */
        public Builder seqLength(int seqLength) {
            if (seqLength < MIN_SEQ_LENGTH || seqLength > MAX_SEQ_LENGTH) {
                throw new IllegalArgumentException("seqLength 必须在 [" + MIN_SEQ_LENGTH + ", "
                        + MAX_SEQ_LENGTH + "] 范围内，当前: " + seqLength);
            }
            this.seqLength = seqLength;
            return this;
        }

        /**
         * 设置时间戳格式串（java.time 格式）。
         *
         * @param timestampPattern 格式串，不能为 null 或空串，如 {@link #DEFAULT_TIMESTAMP_PATTERN}
         * @return 当前构建器，便于链式调用，恒不为 null
         * @throws IllegalArgumentException 当 {@code timestampPattern} 为 {@code null} 或空串时抛出
         */
        public Builder timestampPattern(String timestampPattern) {
            if (timestampPattern == null || timestampPattern.isEmpty()) {
                throw new IllegalArgumentException("timestampPattern 不能为空");
            }
            this.timestampPattern = timestampPattern;
            return this;
        }

        /**
         * 构建不可变的 {@link RequestNo} 实例。
         *
         * @return 请求号生成器实例，恒不为 null
         * @throws IllegalArgumentException 当 {@code timestampPattern} 不是合法的
         *                                  java.time 格式串时抛出（cause 保留原始异常）
         */
        public RequestNo build() {
            DateTimeFormatter compiled;
            try {
                compiled = DateTimeFormatter.ofPattern(timestampPattern);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("非法的时间戳格式: " + timestampPattern, ex);
            }
            return new RequestNo(node, seqLength, compiled);
        }
    }
}

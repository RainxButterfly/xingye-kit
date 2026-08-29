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
 * @since 2026-08-30
 */

package com.xingheyiye.xingye.kit.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 星叶工具集的统一配置属性，绑定 {@code xingye-kit} 前缀下的全部配置项。
 *
 * <p>适用场景：Spring Boot 项目引入 {@code xingye-kit-boot} 依赖后，
 * 在 {@code application.yml} / {@code application.properties} 中按前缀配置即可调整
 * 各自动装配 Bean 的默认行为；不配置则全部使用下方字段默认值。</p>
 *
 * <p>线程安全性：属性对象在容器启动阶段完成绑定，运行期只读，视为线程安全。</p>
 *
 * <p>使用示例（application.yml，属性名支持驼峰/中划线两种风格）：</p>
 * <pre>{@code
 * xingye-kit:
 *   http:
 *     connect-timeout: 5000      # 连接超时（毫秒），默认 5000
 *     read-timeout: 10000        # 读取超时（毫秒），默认 10000
 *     max-retry: 1               # 网络/连接错误自动重试次数，默认 1
 *   id:
 *     worker-id: 1               # 雪花算法机器位（0..31），默认 1
 *     datacenter-id: 1           # 雪花算法数据中心位（0..31），默认 1
 *   cache:
 *     maximum-size: 10000        # 本地缓存最大条目数，默认 10000
 *     expire-after-write-millis: 600000   # 写入后过期时长（毫秒），默认 10 分钟
 *     cleanup-interval-seconds: 60        # 后台清理周期（秒），默认 60
 *   notify:
 *     default-channel: log       # 默认通知渠道标识，默认 "log"
 *     sms:
 *       provider: aliyun         # 短信厂商（仅承载配置，由使用方实现的 SmsClient 读取）
 *       access-key: ${SMS_ACCESS_KEY}     # 从环境变量注入，禁止写死
 *       access-secret: ${SMS_ACCESS_SECRET}
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 * @see XingyeKitAutoConfiguration
 */
@ConfigurationProperties(prefix = "xingye-kit")
public class XingyeKitProperties {

    /** HTTP 客户端（HttpTool）相关配置 */
    private final Http http = new Http();

    /** ID 生成器（雪花算法）相关配置 */
    private final Id id = new Id();

    /** 本地缓存（LocalCache）相关配置 */
    private final Cache cache = new Cache();

    /** 通知（Notifier/SmsClient）相关配置 */
    private final Notify notify = new Notify();

    /**
     * 获取 HTTP 客户端配置节点。
     *
     * @return HTTP 配置，永不为 {@code null}
     */
    public Http getHttp() {
        return http;
    }

    /**
     * 获取 ID 生成器配置节点。
     *
     * @return ID 配置，永不为 {@code null}
     */
    public Id getId() {
        return id;
    }

    /**
     * 获取本地缓存配置节点。
     *
     * @return 缓存配置，永不为 {@code null}
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * 获取通知配置节点。
     *
     * @return 通知配置，永不为 {@code null}
     */
    public Notify getNotify() {
        return notify;
    }

    /**
     * HTTP 客户端配置节点（前缀 {@code xingye-kit.http}）。
     */
    public static class Http {

        /** 连接超时（毫秒），必须大于 0，默认 5000 */
        private int connectTimeout = 5000;

        /** 读取超时（毫秒），必须大于 0，默认 10000 */
        private int readTimeout = 10000;

        /** 网络/连接类错误的最大自动重试次数，0 表示不重试，默认 1 */
        private int maxRetry = 1;

        /**
         * 获取连接超时时间。
         *
         * @return 连接超时（毫秒）
         */
        public int getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * 设置连接超时时间。
         *
         * @param connectTimeout 连接超时（毫秒），应大于 0
         */
        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * 获取读取超时时间。
         *
         * @return 读取超时（毫秒）
         */
        public int getReadTimeout() {
            return readTimeout;
        }

        /**
         * 设置读取超时时间。
         *
         * @param readTimeout 读取超时（毫秒），应大于 0
         */
        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }

        /**
         * 获取自动重试次数。
         *
         * @return 最大重试次数（0 表示不重试）
         */
        public int getMaxRetry() {
            return maxRetry;
        }

        /**
         * 设置自动重试次数。
         *
         * @param maxRetry 最大重试次数，0 表示不重试
         */
        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }
    }

    /**
     * ID 生成器配置节点（前缀 {@code xingye-kit.id}）。
     */
    public static class Id {

        /** 雪花算法机器位编号，取值范围由默认 5 位机器位决定（0..31），默认 1 */
        private long workerId = 1;

        /** 雪花算法数据中心位编号，取值范围由默认 5 位数据中心位决定（0..31），默认 1 */
        private long datacenterId = 1;

        /**
         * 获取机器位编号。
         *
         * @return 机器位编号（默认布局下 0..31）
         */
        public long getWorkerId() {
            return workerId;
        }

        /**
         * 设置机器位编号。
         *
         * @param workerId 机器位编号，默认布局下应为 0..31
         */
        public void setWorkerId(long workerId) {
            this.workerId = workerId;
        }

        /**
         * 获取数据中心位编号。
         *
         * @return 数据中心位编号（默认布局下 0..31）
         */
        public long getDatacenterId() {
            return datacenterId;
        }

        /**
         * 设置数据中心位编号。
         *
         * @param datacenterId 数据中心位编号，默认布局下应为 0..31
         */
        public void setDatacenterId(long datacenterId) {
            this.datacenterId = datacenterId;
        }
    }

    /**
     * 本地缓存配置节点（前缀 {@code xingye-kit.cache}）。
     */
    public static class Cache {

        /** 缓存最大条目数，超过后按近似 LRU 淘汰，默认 10000 */
        private long maximumSize = 10000;

        /** 条目写入后的过期时长（毫秒），默认 600000（10 分钟） */
        private long expireAfterWriteMillis = 600000;

        /** 后台过期清理线程的执行周期（秒），默认 60 */
        private long cleanupIntervalSeconds = 60;

        /**
         * 获取缓存最大条目数。
         *
         * @return 最大条目数（条）
         */
        public long getMaximumSize() {
            return maximumSize;
        }

        /**
         * 设置缓存最大条目数。
         *
         * @param maximumSize 最大条目数（条），应大于 0
         */
        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        /**
         * 获取写入后过期时长。
         *
         * @return 过期时长（毫秒）
         */
        public long getExpireAfterWriteMillis() {
            return expireAfterWriteMillis;
        }

        /**
         * 设置写入后过期时长。
         *
         * @param expireAfterWriteMillis 过期时长（毫秒），应大于 0
         */
        public void setExpireAfterWriteMillis(long expireAfterWriteMillis) {
            this.expireAfterWriteMillis = expireAfterWriteMillis;
        }

        /**
         * 获取后台清理周期。
         *
         * @return 清理周期（秒）
         */
        public long getCleanupIntervalSeconds() {
            return cleanupIntervalSeconds;
        }

        /**
         * 设置后台清理周期。
         *
         * @param cleanupIntervalSeconds 清理周期（秒），应大于 0
         */
        public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
            this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        }
    }

    /**
     * 通知配置节点（前缀 {@code xingye-kit.notify}）。
     */
    public static class Notify {

        /** 默认通知渠道标识（仅承载语义，由使用方实现解释），默认 "log" */
        private String defaultChannel = "log";

        /** 短信厂商接入配置（仅承载配置值，由使用方实现的 SmsClient 读取） */
        private final Sms sms = new Sms();

        /**
         * 获取默认通知渠道标识。
         *
         * @return 渠道标识，如 "log"、"sms"，永不为 {@code null}（未配置时为默认值）
         */
        public String getDefaultChannel() {
            return defaultChannel;
        }

        /**
         * 设置默认通知渠道标识。
         *
         * @param defaultChannel 渠道标识，如 "sms"、"webhook"
         */
        public void setDefaultChannel(String defaultChannel) {
            this.defaultChannel = defaultChannel;
        }

        /**
         * 获取短信厂商配置节点。
         *
         * @return 短信配置，永不为 {@code null}
         */
        public Sms getSms() {
            return sms;
        }
    }

    /**
     * 短信厂商配置节点（前缀 {@code xingye-kit.notify.sms}）。
     *
     * <p>安全说明：本模块不内置任何厂商 SDK，accessKey/accessSecret 仅集中存放于
     * Spring 环境配置（推荐由环境变量/Nacos 等注入），由使用方自行实现的
     * {@code com.xingheyiye.xingye.kit.notify.SmsClient} 读取后调用厂商 OpenAPI。</p>
     */
    public static class Sms {

        /** 短信厂商标识，如 "aliyun"、"tencent"，默认 {@code null} 表示未配置 */
        private String provider;

        /** 厂商 AccessKey ID，从外部环境注入，默认 {@code null} 表示未配置 */
        private String accessKey;

        /** 厂商 AccessKey Secret，从外部环境注入，默认 {@code null} 表示未配置 */
        private String accessSecret;

        /**
         * 获取短信厂商标识。
         *
         * @return 厂商标识，未配置时为 {@code null}
         */
        public String getProvider() {
            return provider;
        }

        /**
         * 设置短信厂商标识。
         *
         * @param provider 厂商标识，如 "aliyun"
         */
        public void setProvider(String provider) {
            this.provider = provider;
        }

        /**
         * 获取 AccessKey ID。
         *
         * @return AccessKey ID，未配置时为 {@code null}
         */
        public String getAccessKey() {
            return accessKey;
        }

        /**
         * 设置 AccessKey ID（推荐用占位符引用环境变量，如 {@code ${SMS_ACCESS_KEY}}）。
         *
         * @param accessKey AccessKey ID
         */
        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        /**
         * 获取 AccessKey Secret。
         *
         * @return AccessKey Secret，未配置时为 {@code null}
         */
        public String getAccessSecret() {
            return accessSecret;
        }

        /**
         * 设置 AccessKey Secret（推荐用占位符引用环境变量）。
         *
         * @param accessSecret AccessKey Secret
         */
        public void setAccessSecret(String accessSecret) {
            this.accessSecret = accessSecret;
        }
    }
}

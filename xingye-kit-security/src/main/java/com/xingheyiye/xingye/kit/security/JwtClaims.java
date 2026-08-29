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
 * @since 2026-08-29
 */
package com.xingheyiye.xingye.kit.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 声明集（payload）的不可变视图：提供按类型读取声明与过期判断。
 *
 * <p>一句话职责：包装已通过签名校验的 payload Map，以类型安全的便捷方法读取标准与自定义声明。</p>
 *
 * <p>适用场景：{@link JwtWrapper#verify(String, String)} 校验成功后的结果读取，
 * 如解析 sub/iss/exp、自定义业务角色等。</p>
 *
 * <p>线程安全性：构造时对入参 Map 做防御性拷贝并冻结，之后不可变，线程安全。</p>
 *
 * <p>类型语义说明：{@link #getStringClaim(String)}/{@link #getLongClaim(String)}/{@link #getListClaim(String)}
 * 在声明存在但类型不符时抛出 {@link ClassCastException}（快速暴露脏数据，而非静默返回 null）；
 * 声明缺失时统一返回 null。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * JwtClaims claims = JwtWrapper.verify(token, secret);
 * String subject = claims.getStringClaim("sub");                          // 缺失为 null
 * Long expiration = claims.getLongClaim("exp");                           // 秒级时间戳
 * List<?> roles = claims.getListClaim("roles");
 * boolean expired = claims.isExpired(System.currentTimeMillis());         // exp 缺失视为不过期
 * Map<String, Object> copy = claims.asMap();                              // 可修改副本
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public final class JwtClaims {

    /** 标准过期时间声明名（值约定为秒级 Unix 时间戳） */
    private static final String CLAIM_EXPIRATION = "exp";

    /** 秒与毫秒的换算系数 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 声明集（不可变视图，持有入参的防御性拷贝） */
    private final Map<String, Object> claims;

    /**
     * 以声明 Map 构造不可变声明集。
     *
     * @param claims 声明集，不能为 null；内部做防御性拷贝，外部后续修改不影响本实例
     * @throws IllegalArgumentException claims 为 null 时抛出
     */
    public JwtClaims(Map<String, Object> claims) {
        if (claims == null) {
            throw new IllegalArgumentException("claims 不能为 null");
        }
        this.claims = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(claims));
    }

    /**
     * 读取原始声明值。
     *
     * @param name 声明名，如 "sub"、"exp"；为 null 时直接返回 null
     * @return 声明值（可能是 String/Long/Double/Boolean/List/Map/null）；声明缺失返回 null
     */
    public Object getClaim(String name) {
        if (name == null) {
            return null;
        }
        return claims.get(name);
    }

    /**
     * 读取字符串声明。
     *
     * @param name 声明名；为 null 时返回 null
     * @return 字符串值；声明缺失返回 null
     * @throws ClassCastException 声明存在但不是 String 类型时抛出（消息含声明名与实际类型）
     */
    public String getStringClaim(String name) {
        Object value = getClaim(name);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        // 类型不符直接抛出而非返回 null：脏数据应尽早暴露，避免调用方误当缺失处理
        throw new ClassCastException("claim '" + name + "' 期望 String，实际为 " + value.getClass().getName());
    }

    /**
     * 读取数值声明并转换为 Long。
     *
     * @param name 声明名；为 null 时返回 null
     * @return Long 值（Long/Integer/Double 等任意 Number 一律按 longValue 转换，小数会截断）；
     *         声明缺失返回 null
     * @throws ClassCastException 声明存在但不是 Number 类型时抛出
     */
    public Long getLongClaim(String name) {
        Object value = getClaim(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        throw new ClassCastException("claim '" + name + "' 期望 Number，实际为 " + value.getClass().getName());
    }

    /**
     * 读取列表声明。
     *
     * @param name 声明名；为 null 时返回 null
     * @return 列表值（元素类型由调用方自行判断）；声明缺失返回 null
     * @throws ClassCastException 声明存在但不是 List 类型时抛出
     */
    public List<?> getListClaim(String name) {
        Object value = getClaim(name);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return (List<?>) value;
        }
        throw new ClassCastException("claim '" + name + "' 期望 List，实际为 " + value.getClass().getName());
    }

    /**
     * 判断令牌是否已过期。
     *
     * <p>时间语义：exp 按 JWT 标准以秒级 Unix 时间戳存储，本方法入参为毫秒，
     * 内部将 exp 换算为毫秒后与当前时间比较；exp 声明缺失时视为不过期（返回 false），
     * 是否接受无过期时间的令牌由调用方决定。</p>
     *
     * @param nowMillis 当前时间（毫秒级 Unix 时间戳，如 System.currentTimeMillis()）
     * @return true 表示已到/超过过期时刻；false 表示未过期或 exp 缺失
     */
    public boolean isExpired(long nowMillis) {
        Long expiration = getLongClaim(CLAIM_EXPIRATION);
        if (expiration == null) {
            return false;
        }
        return nowMillis >= expiration.longValue() * MILLIS_PER_SECOND;
    }

    /**
     * 返回声明集的可修改副本。
     *
     * @return 以 LinkedHashMap 承载的浅拷贝副本（修改不影响本实例），不会为 null；
     *         无声明时返回空 Map
     */
    public Map<String, Object> asMap() {
        return new LinkedHashMap<String, Object>(claims);
    }
}

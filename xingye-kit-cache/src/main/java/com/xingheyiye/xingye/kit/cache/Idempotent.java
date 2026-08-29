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
 * @since 2026-08-28
 */
package com.xingheyiye.xingye.kit.cache;

/**
 * 接口幂等控制：以请求号（requestNo）为粒度防止同一请求被重复处理。
 *
 * <p>一句话职责：将"请求是否已处理"压缩为存储中的一个带 TTL 标记，提供开始/完成/取消三个动作。</p>
 *
 * <p>标准用法：请求处理前先 {@link #tryBegin(String, long)}，返回 false 说明是重复请求，直接拒绝；
 * 业务处理成功后调用 {@link #complete(String)} 清除标记；处理失败则调用 {@link #cancel(String)}
 * 清除标记以允许客户端重试。</p>
 *
 * <p>适用场景：支付、下单、发券等写接口的防重复提交。</p>
 *
 * <p>线程安全性：本类仅持有不可变的存储引用，线程安全性取决于传入的 {@link IdempotentStore} 实现。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Idempotent idempotent = new Idempotent(new MemoryIdempotentStore());
 * String requestNo = request.getHeader("X-Request-No");
 *
 * if (!idempotent.tryBegin(requestNo, 30000)) {
 *     throw new BusinessException("请勿重复提交");
 * }
 * try {
 *     doBusiness();
 *     idempotent.complete(requestNo);   // 成功：清除标记
 * } catch (Exception e) {
 *     idempotent.cancel(requestNo);     // 失败：清除标记，允许重试
 *     throw e;
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public class Idempotent {

    /** 幂等键在存储中的统一前缀，避免与其它业务键冲突 */
    private static final String KEY_PREFIX = "idem:";

    /** 幂等标记存储 */
    private final IdempotentStore store;

    /**
     * 构造幂等控制器。
     *
     * @param store 幂等标记存储，不能为 null
     * @throws IllegalArgumentException store 为 null 时抛出
     */
    public Idempotent(IdempotentStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store 不能为 null");
        }
        this.store = store;
    }

    /**
     * 尝试开始处理请求：写入幂等标记。
     *
     * <p>必须在处理业务逻辑之前调用；返回 false 表示同号请求正在处理或已处理完成，应直接拒绝。</p>
     *
     * @param requestNo 请求唯一编号，不能为 null 或空白串；通常由客户端生成并随请求携带
     * @param ttlMillis 标记存活时长（毫秒），必须大于 0；应覆盖业务最长处理时间，
     *                  到期后标记自动失效（视为允许再次处理）
     * @return true 表示标记写入成功，可继续处理业务；false 表示重复请求，应拒绝
     * @throws IllegalArgumentException requestNo 为 null 或空白串、ttlMillis 小于等于 0 时抛出
     */
    public boolean tryBegin(String requestNo, long ttlMillis) {
        checkRequestNo(requestNo);
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis 必须大于 0，单位毫秒");
        }
        return store.putIfAbsent(KEY_PREFIX + requestNo, ttlMillis);
    }

    /**
     * 完成请求：清除幂等标记。
     *
     * <p>业务处理成功后调用。标记被清除后，同号请求将再次被允许（tryBegin 返回 true），
     * 因此仅在确定不再需要防重（如业务已成功落库）时调用。</p>
     *
     * @param requestNo 请求唯一编号，与 tryBegin 时一致，不能为 null 或空白串
     * @throws IllegalArgumentException requestNo 为 null 或空白串时抛出
     */
    public void complete(String requestNo) {
        checkRequestNo(requestNo);
        store.remove(KEY_PREFIX + requestNo);
    }

    /**
     * 取消请求：清除幂等标记，允许客户端重试。
     *
     * <p>业务处理失败后调用。与 {@link #complete(String)} 底层动作相同，语义上用于失败回滚场景。</p>
     *
     * @param requestNo 请求唯一编号，与 tryBegin 时一致，不能为 null 或空白串
     * @throws IllegalArgumentException requestNo 为 null 或空白串时抛出
     */
    public void cancel(String requestNo) {
        checkRequestNo(requestNo);
        store.remove(KEY_PREFIX + requestNo);
    }

    /**
     * 校验请求编号非 null 且非空白串。
     *
     * @param requestNo 请求编号
     */
    private void checkRequestNo(String requestNo) {
        if (requestNo == null || requestNo.trim().length() == 0) {
            throw new IllegalArgumentException("requestNo 不能为 null 或空白串");
        }
    }
}

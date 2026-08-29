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
 * @since 2026-08-23
 */
package com.xingheyiye.xingye.kit.notify;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码服务：生成、防刷发送间隔控制、一次性校验与失败次数限制。
 *
 * <p>一句话职责：以 {@link CodeStore} 为存储后端，完成"生成验证码 → 发送（防刷）→ 校验（防爆破）"闭环。</p>
 *
 * <p>适用场景：手机号/邮箱注册登录验证码、敏感操作二次确认码。
 * 规则：</p>
 * <ul>
 *     <li>generate：SecureRandom 生成；同一 target 距上次生成不足 resendIntervalMillis
 *         抛 IllegalStateException（防刷）；生成成功会重置该 target 的失败计数；</li>
 *     <li>verify：命中即删除记录（一次性使用）；记录不存在/已过期/不匹配均返回 false 并累计
 *         失败次数，达到 maxVerifyAttempts 后删除记录并清零计数。</li>
 * </ul>
 *
 * <p>线程安全性：验证码本体经 {@link CodeStore} 存取（其线程安全性由实现保证）；
 * 发送间隔时间戳与失败计数保存在类内 {@link ConcurrentHashMap}，防刷判断在锁内完成
 * check-then-act，可安全并发调用。注意：计数与时间戳仅在本实例内可见，多实例部署时
 * 防刷与失败计数为"单实例粒度"，如需全局粒度应由使用方在外部存储中另行实现。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * VerificationCode service = new VerificationCode(new InMemoryCodeStore());
 * String code = service.generate("13800138000");          // 60s 内重复调用会抛 IllegalStateException
 * smsClient.send("13800138000", sign, tpl, Collections.singletonMap("code", code));
 * ...
 * boolean ok = service.verify("13800138000", userInput);  // 命中一次即失效
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class VerificationCode {

    /** 安全随机数发生器（SecureRandom 自身线程安全，作为静态常量共享） */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 数字字符集：numericOnly=true 时使用 */
    private static final char[] DIGITS = "0123456789".toCharArray();

    /** 数字 + 小写字母 + 大写字母字符集：numericOnly=false 时使用 */
    private static final char[] ALPHANUMERIC =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /** 验证码在 CodeStore 中的键前缀，避免与其它业务键冲突 */
    private static final String KEY_PREFIX = "vcode:";

    /** 验证码存储后端 */
    private final CodeStore store;

    /** 验证码长度（字符个数） */
    private final int codeLength;

    /** true 表示仅生成数字验证码，false 表示数字 + 大小写字母混合 */
    private final boolean numericOnly;

    /** 验证码有效期（毫秒），写入 CodeStore 的 TTL */
    private final long expireMillis;

    /** 同一目标两次生成之间的最小间隔（毫秒），用于防刷 */
    private final long resendIntervalMillis;

    /** 同一目标允许的最大校验失败次数，达到后删除记录要求重新生成 */
    private final int maxVerifyAttempts;

    /** 各目标最近一次成功生成的时间戳（毫秒），防刷判断依据 */
    private final ConcurrentHashMap<String, Long> lastSendTimes = new ConcurrentHashMap<String, Long>();

    /** 各目标校验失败累计次数（防爆破），成功或达上限后清零 */
    private final ConcurrentHashMap<String, Integer> verifyFailures = new ConcurrentHashMap<String, Integer>();

    /**
     * 便捷构造：使用默认参数——6 位纯数字、有效期 60 秒、发送间隔 60 秒、最多校验失败 5 次。
     *
     * @param store 验证码存储后端，不能为 null
     * @throws IllegalArgumentException store 为 null 时抛出
     */
    public VerificationCode(CodeStore store) {
        this(store, 6, true, 60000L, 60000L, 5);
    }

    /**
     * 完整构造。
     *
     * @param store 验证码存储后端，不能为 null
     * @param codeLength 验证码长度，必须大于 0
     * @param numericOnly true 表示仅数字验证码；false 表示数字 + 大小写字母混合验证码
     * @param expireMillis 验证码有效期（毫秒），必须大于 0
     * @param resendIntervalMillis 同一目标两次生成的最小间隔（毫秒），不能为负数；0 表示不做间隔限制
     * @param maxVerifyAttempts 同一目标最大校验失败次数，必须大于 0；达到后删除记录要求重新生成
     * @throws IllegalArgumentException 任一参数不满足上述约束时抛出
     */
    public VerificationCode(CodeStore store, int codeLength, boolean numericOnly, long expireMillis,
                            long resendIntervalMillis, int maxVerifyAttempts) {
        if (store == null) {
            throw new IllegalArgumentException("store 不能为 null");
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("codeLength 必须大于 0");
        }
        if (expireMillis <= 0) {
            throw new IllegalArgumentException("expireMillis 必须大于 0，单位毫秒");
        }
        if (resendIntervalMillis < 0) {
            throw new IllegalArgumentException("resendIntervalMillis 不能为负数，单位毫秒");
        }
        if (maxVerifyAttempts <= 0) {
            throw new IllegalArgumentException("maxVerifyAttempts 必须大于 0");
        }
        this.store = store;
        this.codeLength = codeLength;
        this.numericOnly = numericOnly;
        this.expireMillis = expireMillis;
        this.resendIntervalMillis = resendIntervalMillis;
        this.maxVerifyAttempts = maxVerifyAttempts;
    }

    /**
     * 为目标（手机号/邮箱等）生成验证码并写入存储。
     *
     * <p>同一 target 距上次成功生成不足 {@code resendIntervalMillis} 时抛出
     * IllegalStateException（防刷）；生成成功会重置该 target 的失败计数。
     * 调用方拿到验证码后应立即经 {@link SmsClient}/{@link MailClient} 发送，
     * 切勿将验证码明文写入日志。</p>
     *
     * @param target 验证码接收目标（如手机号、邮箱），不能为 null 或空白串
     * @return 生成的验证码明文，不会为 null；numericOnly=true 时为纯数字串，
     *         false 时为数字 + 大小写字母混合串，长度等于构造时的 codeLength
     * @throws IllegalArgumentException target 为 null 或空白串时抛出
     * @throws IllegalStateException 同一 target 距上次生成小于 resendIntervalMillis 时抛出（防刷）
     * @throws RuntimeException CodeStore 写入失败等存储故障时抛出
     */
    public String generate(String target) {
        checkTarget(target);
        long now = System.currentTimeMillis();
        synchronized (lastSendTimes) {
            Long last = lastSendTimes.get(target);
            if (last != null && now - last < resendIntervalMillis) {
                throw new IllegalStateException("验证码发送过于频繁，同一目标 [" + target + "] 需间隔 "
                        + resendIntervalMillis + "ms（剩余 " + (resendIntervalMillis - (now - last)) + "ms）");
            }
            lastSendTimes.put(target, now);
        }
        String code = randomCode();
        store.put(KEY_PREFIX + target, code, expireMillis);
        verifyFailures.remove(target);
        return code;
    }

    /**
     * 校验目标提交的验证码。
     *
     * <p>命中则删除存储中的验证码并清空失败计数（一次性使用）；
     * 记录不存在/已过期/不匹配均返回 false 并累计失败次数，达到 maxVerifyAttempts 后
     * 删除记录并清零计数（此后必须重新 generate）。code 为 null 视为不匹配。</p>
     *
     * @param target 验证码接收目标，与 {@link #generate(String)} 时一致，不能为 null 或空白串
     * @param code 用户提交的验证码，可为 null（视为不匹配）
     * @return true 表示校验成功（记录已删除）；false 表示记录不存在/已过期/不匹配
     * @throws IllegalArgumentException target 为 null 或空白串时抛出
     * @throws RuntimeException CodeStore 读取失败等存储故障时抛出
     */
    public boolean verify(String target, String code) {
        checkTarget(target);
        String stored = store.get(KEY_PREFIX + target);
        if (stored != null && stored.equals(code)) {
            store.remove(KEY_PREFIX + target);
            verifyFailures.remove(target);
            return true;
        }
        Integer failures = verifyFailures.merge(target, 1, Integer::sum);
        if (failures != null && failures >= maxVerifyAttempts) {
            store.remove(KEY_PREFIX + target);
            verifyFailures.remove(target);
        }
        return false;
    }

    /**
     * 生成随机验证码。
     *
     * @return 长度为 codeLength 的验证码串，不会为 null
     */
    private String randomCode() {
        char[] alphabet = numericOnly ? DIGITS : ALPHANUMERIC;
        char[] code = new char[codeLength];
        for (int i = 0; i < codeLength; i++) {
            code[i] = alphabet[RANDOM.nextInt(alphabet.length)];
        }
        return new String(code);
    }

    /**
     * 校验目标非 null 且非空白串。
     *
     * @param target 接收目标
     * @throws IllegalArgumentException target 为 null 或空白串时抛出
     */
    private void checkTarget(String target) {
        if (target == null || target.trim().length() == 0) {
            throw new IllegalArgumentException("target 不能为 null 或空白串");
        }
    }
}

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
package com.xingheyiye.xingye.kit.notify.impl;

import com.xingheyiye.xingye.kit.notify.Notifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {@link Notifier} 的本地日志实现：把通知打印到 System.out 并恒定返回成功。
 *
 * <p>一句话职责：以"时间戳 + 渠道 + 消息"一行日志替代真实发送。</p>
 *
 * <p>适用场景：本地联调与单元测试，验证业务流程中的通知触发点，不产生任何外部副作用。</p>
 *
 * <p>线程安全性：无状态（仅持有线程安全的 DateTimeFormatter 常量），线程安全，
 * 可在多线程间共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Notifier notifier = new LoggingNotifier();
 * notifier.send("订单已发货", "email");
 * // 控制台输出：[2026-08-23 10:15:30.123] [notify] channel=email, message=订单已发货
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class LoggingNotifier implements Notifier {

    /** 日志时间戳格式（DateTimeFormatter 不可变且线程安全） */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 将通知打印到 System.out：一行"时间戳 + channel + message"，恒定返回 true。
     *
     * @param message 通知内容；为 null 时打印 "null"，不抛异常
     * @param channel 渠道标识；为 null 时打印 "null"，不抛异常
     * @return 恒定为 true（本地联调语义下视为发送成功）
     */
    @Override
    public boolean send(String message, String channel) {
        String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
        System.out.println("[" + timestamp + "] [notify] channel=" + channel + ", message=" + message);
        return true;
    }
}

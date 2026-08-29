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
 * @since 2026-08-17
 */

/**
 * xingye-kit 核心基础包，沉淀服务端开发最常用的通用能力：错误码契约、业务异常、统一返回体、断言与基础工具类。
 *
 * <p>适用场景：REST 接口统一出参包装、参数校验快速失败、敏感信息脱敏、
 * 日期时间格式化与换算、代码段计时、失败重试编排、ID 生成器接入与反射类工具等。
 *
 * <p>主要工具清单：
 * <ul>
 *     <li>{@link com.xingheyiye.xingye.kit.core.ErrorCode}：错误码契约接口</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.BizException}：携带错误码的业务异常</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.Result}：泛型统一返回体</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.Assert}：断言工具，失败抛 {@code BizException}</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.StringUtils}：字符串判空、脱敏与格式化</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.DateUtils}：日期时间格式化、互转与相对时间</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.StopWatch}：纳秒级简易计时器</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.RetryTemplate}：不可变重试模板</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.IdGenerator}：ID 生成器契约接口</li>
 *     <li>{@link com.xingheyiye.xingye.kit.core.ClassUtils}：类加载、实例化与代理还原工具</li>
 * </ul>
 *
 * <p>本包零第三方依赖，仅依赖 JDK 8 标准库；除特别声明外，工具类均为无状态静态方法，线程安全。
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-17
 */
package com.xingheyiye.xingye.kit.core;

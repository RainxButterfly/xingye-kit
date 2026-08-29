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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段标记注解：标注在 String 字段上，声明其脱敏规则。
 *
 * <p>一句话职责：以注解元数据描述"该字段按哪种策略脱敏"，由 {@link SensitiveMask#maskObject(Object)}
 * 在运行时反射读取并执行脱敏。</p>
 *
 * <p>适用场景：对外输出的 VO/DTO 字段（日志、报表、客服工单），
 * 仅对 String 类型字段生效，非 String 字段标注后会被忽略。</p>
 *
 * <p>线程安全性：注解声明本身线程安全；处理动作由 {@link SensitiveMask} 保证。</p>
 *
 * <p>实体标注示例：</p>
 * <pre>{@code
 * public class UserVO {
 *
 *     private String userName;
 *
 *     @Sensitive(SensitiveType.PHONE)
 *     private String phone;
 *
 *     @Sensitive(SensitiveType.ID_CARD)
 *     private String idCard;
 *
 *     @Sensitive(SensitiveType.ADDRESS)
 *     private String address;
 * }
 *
 * UserVO safe = SensitiveMask.maskObject(userVO);  // phone/idCard/address 已按规则脱敏
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 脱敏策略类型。
     *
     * @return 策略枚举值，必填
     */
    SensitiveType value();
}

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
 * @since 2026-08-26
 */

/**
 * 通用 IO 工具包：文件与目录操作、ZIP 压缩解压、CSV 读写，以及二维码/图片的轻量处理。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>零第三方依赖：文件、ZIP、CSV 均基于 JDK 原生流式 API 实现（ZIP 与 CSV 显式使用 UTF-8，
 *       流复制统一使用 8192 字节缓冲）；</li>
 *   <li>二维码能力通过反射调用 ZXing 实现——编译期完全不依赖 ZXing，运行时按需提供
 *       （见 {@link com.xingheyiye.xingye.kit.io.QrCodeUtils#available()}）；</li>
 *   <li>图片处理使用纯 {@code java.awt.image} 软件渲染（双线性插值 + 质量优先渲染提示），
 *       无显示器/headless 服务器可直接运行；</li>
 *   <li>错误约定：IO 失败统一以 {@code java.io.IOException} 透传；参数问题以
 *       {@code IllegalArgumentException}/{@code NullPointerException} 快速失败；
 *       ZIP 解压内置 Zip Slip（路径穿越）防护，CSV 解析内置 BOM 剥离与 RFC 4180 转义。</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * FileUtils.writeString(new File("data/hello.txt"), "你好，xingye-kit", StandardCharsets.UTF_8, false);
 * ZipUtils.zip(new File("data"), new File("data.zip"));
 * try (CsvReader reader = new CsvReader(new File("data/users.csv"), StandardCharsets.UTF_8)) {
 *     List<String[]> rows = reader.readAll();
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-26
 */
package com.xingheyiye.xingye.kit.io;

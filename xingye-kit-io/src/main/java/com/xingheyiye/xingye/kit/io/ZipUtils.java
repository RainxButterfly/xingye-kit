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

package com.xingheyiye.xingye.kit.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ZIP 压缩与解压的静态工具集：目录递归打包保留相对路径，解压内置 Zip Slip（路径穿越）防护。
 *
 * <p>适用场景：部署包打包/展开、日志归档、用户上传 zip 的安全解压（对外部来源压缩包尤其重要）。</p>
 *
 * <p>线程安全性：无状态静态工具类，所有方法可被多线程并发调用；
 * 同一目标文件/目录的并发操作仍需调用方自行串行化。</p>
 *
 * <p>实现约定：条目名与内容字符集固定为 UTF-8；流缓冲统一 8192 字节；
 * 目录压缩时以相对源目录的路径作为条目名（分隔符固定为 {@code /}，空目录同样写入条目以保证解压后结构完整）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ZipUtils.zip(new File("logs"), new File("logs-2026.zip")); // 目录整体打包
 * ZipUtils.unzip(new File("logs-2026.zip"), new File("restore")); // 安全解压
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-26
 */
public final class ZipUtils {

    /** 流缓冲大小：8192 字节。 */
    private static final int BUFFER_SIZE = 8192;
    /** ZIP 条目名使用的路径分隔符（ZIP 规范固定为斜杠，与平台无关）。 */
    private static final char ZIP_SEPARATOR = '/';
    /** 目录条目必须以斜杠结尾的约定。 */
    private static final String DIR_ENTRY_SUFFIX = "/";

    /**
     * 工具类禁止实例化。
     */
    private ZipUtils() {
    }

    /**
     * 把文件或目录压缩为 ZIP（目录递归，条目名为相对源目录的路径；自动跳过压缩包自身，避免“自我嵌套”）。
     *
     * @param src 待压缩的文件或目录，不可为 null 且必须已存在
     * @param destZip 输出 ZIP 文件，不可为 null（父目录不存在时自动创建；已存在则覆盖）
     * @throws IOException src 不存在、目标不可写或读写失败
     * @throws NullPointerException 任一参数为 null
     * @throws IllegalArgumentException src 与 destZip 指向同一路径
     */
    public static void zip(File src, File destZip) throws IOException {
        Objects.requireNonNull(src, "src 不能为 null");
        Objects.requireNonNull(destZip, "destZip 不能为 null");
        if (!src.exists()) {
            throw new FileNotFoundException("待压缩路径不存在: " + src);
        }
        if (src.getCanonicalPath().equals(destZip.getCanonicalPath())) {
            throw new IllegalArgumentException("压缩包不能是它自身: " + src);
        }
        createParentIfAbsent(destZip);
        // 压缩包自身的规范路径，遍历时用于跳过（例如把 zip 输出到被压缩目录内）
        String destCanonicalPath = destZip.getCanonicalPath();
        try (ZipOutputStream zipOut = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(destZip), BUFFER_SIZE), StandardCharsets.UTF_8)) {
            if (src.isDirectory()) {
                zipDirectory(src, src, destCanonicalPath, zipOut);
            } else {
                zipFile(src, src.getName(), zipOut);
            }
        }
    }

    /**
     * 把 ZIP 解压到目标目录（自动创建目标目录；每个条目都做 Zip Slip 路径穿越校验）。
     *
     * @param zipFile ZIP 文件，不可为 null 且必须是已存在的文件
     * @param destDir 解压目标目录，不可为 null（不存在时自动创建，含多级父目录）
     * @throws IOException 压缩包不存在、损坏、条目名非法（试图逃逸目标目录）或写出失败；
     *                     逃逸条目的异常消息形如 {@code "zip entry escapes target dir: ../../evil.txt"}
     * @throws NullPointerException 任一参数为 null
     */
    public static void unzip(File zipFile, File destDir) throws IOException {
        Objects.requireNonNull(zipFile, "zipFile 不能为 null");
        Objects.requireNonNull(destDir, "destDir 不能为 null");
        if (!zipFile.isFile()) {
            throw new FileNotFoundException("压缩包不存在或不是文件: " + zipFile);
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("无法创建目标目录: " + destDir);
        }
        // 防护基准：目标目录的规范路径 + 平台分隔符（保证匹配到的是目录内部条目而非同名兄弟路径）
        String destCanonicalRoot = destDir.getCanonicalPath() + File.separator;
        try (ZipInputStream zipIn = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile), BUFFER_SIZE), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                File target = new File(destDir, entry.getName());
                // Zip Slip（路径穿越）防护：
                // 恶意压缩包可以把条目命名为 "../../etc/passwd" 之类，new File(destDir, name) 拼接后
                // 目标会逃逸出 destDir，进而覆盖任意系统文件；getCanonicalPath() 会先解析 ".."、"." 与
                // 符号链接得到真实落盘路径，因此要求真实路径必须位于 destDir 真实路径之下，否则拒绝解压。
                if (!target.getCanonicalPath().startsWith(destCanonicalRoot)) {
                    throw new IOException("zip entry escapes target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) {
                        throw new IOException("无法创建目录: " + target);
                    }
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("无法创建目录: " + parent);
                    }
                    try (OutputStream out = new BufferedOutputStream(
                            new FileOutputStream(target), BUFFER_SIZE)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = zipIn.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
    }

    // ------------------------------------------------------------------
    // 内部辅助：压缩
    // ------------------------------------------------------------------

    /**
     * 递归压缩目录：条目名为相对 root 的路径，跳过与压缩包自身相同的文件。
     */
    private static void zipDirectory(File root, File dir, String destCanonicalPath, ZipOutputStream zipOut)
            throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            throw new IOException("无法列出目录内容: " + dir);
        }
        if (children.length == 0) {
            // 空目录也写入条目，否则解压后该目录会丢失
            zipOut.putNextEntry(new ZipEntry(relativeEntryName(root, dir) + DIR_ENTRY_SUFFIX));
            zipOut.closeEntry();
            return;
        }
        for (File child : children) {
            // 跳过压缩包自身：常见于把 zip 输出到被压缩目录内，否则边写边读会读到半成品
            if (child.getCanonicalPath().equals(destCanonicalPath)) {
                continue;
            }
            if (child.isDirectory()) {
                zipDirectory(root, child, destCanonicalPath, zipOut);
            } else {
                zipFile(child, relativeEntryName(root, child), zipOut);
            }
        }
    }

    /**
     * 把单个文件写入一个 ZIP 条目（保留源文件修改时间）。
     */
    private static void zipFile(File file, String entryName, ZipOutputStream zipOut) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zipOut.putNextEntry(entry);
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                zipOut.write(buffer, 0, read);
            }
        }
        zipOut.closeEntry();
    }

    /**
     * 计算相对 root 的条目名，并把平台分隔符统一为 ZIP 规范的斜杠。
     */
    private static String relativeEntryName(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        String relative = filePath.substring(rootPath.length());
        if (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return relative.replace(File.separatorChar, ZIP_SEPARATOR);
    }

    /**
     * 父目录不存在时创建（含多级）。
     */
    private static void createParentIfAbsent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建父目录: " + parent);
        }
    }
}

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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 文件与目录操作的静态工具集，覆盖读写文本、复制、递归删除/统计、临时文件、按扩展名列目录等常见需求。
 *
 * <p>适用场景：配置/小文件读写、构建产物整理、批处理脚本、临时工作目录管理；
 * 大文件读写为简化 API 仍按整体载入（readString/readLines），超大文件请自行使用流式处理。</p>
 *
 * <p>线程安全性：类为无状态静态工具类，所有方法可被多线程并发调用；
 * 各 File 参数本身不受并发保护，同一文件的同时写操作仍需调用方自行协调。</p>
 *
 * <p>实现约定：所有流复制/缓冲统一使用 8192 字节缓冲；IO 失败统一以
 * {@link IOException} 原样透传（不吞异常、不包装），参数非法以运行时异常快速失败。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * File file = new File("conf/app.properties");
 * FileUtils.writeString(file, "mode=prod", StandardCharsets.UTF_8, false);
 * String content = FileUtils.readString(file);
 * List<File> yamls = FileUtils.listFiles(new File("conf"), "yaml");
 * long bytes = FileUtils.sizeOf(new File("logs"));
 * FileUtils.deleteRecursive(new File("tmp-build"));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-26
 */
public final class FileUtils {

    /** 流复制与缓冲读取的缓冲大小：8192 字节（8KB，兼顾吞吐与内存占用）。 */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 工具类禁止实例化。
     */
    private FileUtils() {
    }

    /**
     * 以指定字符集读取整个文件为字符串。
     *
     * @param file 目标文件，不可为 null；不存在时抛出 FileNotFoundException（IOException 子类）
     * @param charset 解码字符集，不可为 null（如 StandardCharsets.UTF_8）
     * @return 文件完整内容，永不为 null（文件为空时为空串）
     * @throws IOException 文件不存在、不可读或读取失败
     * @throws NullPointerException file 或 charset 为 null
     */
    public static String readString(File file, Charset charset) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        Objects.requireNonNull(charset, "charset 不能为 null");
        return new String(readBytes(file), charset);
    }

    /**
     * 以 UTF-8 读取整个文件为字符串。
     *
     * @param file 目标文件，不可为 null；不存在时抛出 FileNotFoundException（IOException 子类）
     * @return 文件完整内容，永不为 null（文件为空时为空串）
     * @throws IOException 文件不存在、不可读或读取失败
     * @throws NullPointerException file 为 null
     */
    public static String readString(File file) throws IOException {
        return readString(file, StandardCharsets.UTF_8);
    }

    /**
     * 把字符串写入文件（整体编码为字节后一次性写出）。
     *
     * @param file 目标文件，不可为 null；父目录不存在时自动创建
     * @param content 写入内容，不可为 null
     * @param charset 编码字符集，不可为 null（如 StandardCharsets.UTF_8）
     * @param append true 表示追加到文件末尾，false 表示覆盖已有内容
     * @throws IOException 父目录创建失败或写入失败
     * @throws NullPointerException 任一参数为 null
     */
    public static void writeString(File file, String content, Charset charset, boolean append) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        Objects.requireNonNull(content, "content 不能为 null");
        Objects.requireNonNull(charset, "charset 不能为 null");
        createParentIfAbsent(file);
        try (OutputStream out = new FileOutputStream(file, append)) {
            out.write(content.getBytes(charset));
        }
    }

    /**
     * 以指定字符集逐行读取文件。
     *
     * @param file 目标文件，不可为 null；不存在时抛出 FileNotFoundException（IOException 子类）
     * @param charset 解码字符集，不可为 null
     * @return 行列表（不含行结束符，末尾无换行的最后一行也会包含），永不为 null；文件为空时为空列表
     * @throws IOException 文件不存在或读取失败
     * @throws NullPointerException file 或 charset 为 null
     */
    public static List<String> readLines(File file, Charset charset) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        Objects.requireNonNull(charset, "charset 不能为 null");
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset), BUFFER_SIZE)) {
            String line = reader.readLine(); // readLine 兼容 \n、\r\n、\r 三种行结束符
            while (line != null) {
                lines.add(line);
                line = reader.readLine();
            }
        }
        return lines;
    }

    /**
     * 复制单个文件（目标父目录不存在时自动创建；目标已存在则覆盖）。
     *
     * @param src 源文件，不可为 null；不存在时抛出 FileNotFoundException（IOException 子类）
     * @param dest 目标文件，不可为 null
     * @throws IOException 源不存在、不可读，父目录创建失败或写入失败
     * @throws NullPointerException src 或 dest 为 null
     */
    public static void copyFile(File src, File dest) throws IOException {
        Objects.requireNonNull(src, "src 不能为 null");
        Objects.requireNonNull(dest, "dest 不能为 null");
        createParentIfAbsent(dest);
        try (InputStream in = new FileInputStream(src);
                OutputStream out = new FileOutputStream(dest)) {
            copyStreams(in, out);
        }
    }

    /**
     * 递归复制整个目录（目标目录不存在时自动创建，结构原样保持）。
     *
     * @param src 源目录，不可为 null 且必须是已存在的目录
     * @param dest 目标目录，不可为 null；不存在则创建（含多级父目录）
     * @throws IOException 源不是目录、目标目录创建失败或复制过程中任一文件失败
     * @throws NullPointerException src 或 dest 为 null
     * @throws IllegalArgumentException src 不是已存在的目录
     */
    public static void copyDir(File src, File dest) throws IOException {
        Objects.requireNonNull(src, "src 不能为 null");
        Objects.requireNonNull(dest, "dest 不能为 null");
        if (!src.isDirectory()) {
            throw new IllegalArgumentException("源路径不是目录: " + src);
        }
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("无法创建目标目录: " + dest);
        }
        File[] children = listChildren(src);
        for (File child : children) {
            File target = new File(dest, child.getName());
            if (child.isDirectory()) {
                copyDir(child, target);
            } else {
                copyFile(child, target);
            }
        }
    }

    /**
     * 递归删除文件或目录（目录先删子项再删自身）；目标不存在时静默返回（幂等）。
     *
     * @param file 待删除的文件或目录，不可为 null
     * @throws IOException 无法列出目录内容，或任一文件/目录删除失败（此时可能残留部分文件）
     * @throws NullPointerException file 为 null
     */
    public static void deleteRecursive(File file) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        if (!file.exists()) {
            return; // 已不存在视为成功，保证幂等
        }
        if (file.isDirectory()) {
            for (File child : listChildren(file)) {
                deleteRecursive(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("删除失败: " + file.getAbsolutePath());
        }
    }

    /**
     * 在系统默认临时目录创建临时文件。
     *
     * @param prefix 文件名前缀，可为 null（非 null 时至少 3 个字符，否则 File.createTempFile 会拒绝）
     * @param suffix 文件名后缀，可为 null（null 时自动使用 ".tmp"）
     * @return 创建好的临时文件（空文件），永不为 null
     * @throws IOException 临时目录不可写或创建失败
     */
    public static File createTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix);
    }

    /**
     * 在系统默认临时目录创建临时目录。
     *
     * @param prefix 目录名前缀，可为 null（非 null 时至少 3 个字符）
     * @return 创建好的空临时目录，永不为 null
     * @throws IOException 临时目录不可写或创建失败
     */
    public static File createTempDir(String prefix) throws IOException {
        // Files.createTempDirectory 原子创建，避免“先建文件再删除再建目录”方案的竞态窗口
        return Files.createTempDirectory(prefix).toFile();
    }

    /**
     * 递归统计文件或目录的字节大小（目录为其所有后代文件大小之和）。
     *
     * @param file 文件或目录，不可为 null 且必须已存在
     * @return 字节数，不小于 0（目录统计不含目录条目本身占用的块大小）
     * @throws NullPointerException file 为 null
     * @throws IllegalArgumentException file 不存在
     */
    public static long sizeOf(File file) {
        Objects.requireNonNull(file, "file 不能为 null");
        if (!file.exists()) {
            throw new IllegalArgumentException("文件或目录不存在: " + file);
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                total += sizeOf(child);
            }
        }
        return total;
    }

    /**
     * 列出目录下的普通文件（不含子目录及其内容，不递归）。
     *
     * @param dir 目标目录，不可为 null 且必须是已存在的目录
     * @param extension 扩展名过滤（不含点，忽略大小写，如 "csv"）；传 null 表示不过滤、返回全部普通文件
     * @return 匹配的文件列表（顺序按文件系统返回顺序），永不为 null（可为空列表）
     * @throws IOException 无法列出目录内容（IO 层失败）
     * @throws NullPointerException dir 为 null
     * @throws IllegalArgumentException dir 不是已存在的目录
     */
    public static List<File> listFiles(File dir, String extension) throws IOException {
        Objects.requireNonNull(dir, "dir 不能为 null");
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("目标路径不是目录: " + dir);
        }
        File[] children = listChildren(dir);
        List<File> result = new ArrayList<File>();
        for (File child : children) {
            if (child.isFile() && (extension == null || hasExtension(child, extension))) {
                result.add(child);
            }
        }
        return result;
    }

    /**
     * 创建空文件或把已存在文件的最后修改时间刷新为当前时间（类似 Unix touch）。
     *
     * @param file 目标文件，不可为 null；父目录不存在时自动创建
     * @throws IOException 父目录创建失败、文件创建失败或修改时间更新失败
     * @throws NullPointerException file 为 null
     */
    public static void touch(File file) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        if (!file.exists()) {
            createParentIfAbsent(file);
            if (!file.createNewFile()) {
                throw new IOException("创建文件失败: " + file.getAbsolutePath());
            }
            return;
        }
        if (!file.setLastModified(System.currentTimeMillis())) {
            throw new IOException("更新修改时间失败: " + file.getAbsolutePath());
        }
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    /**
     * 读取整个文件为字节数组（供文本解码使用）。
     */
    private static byte[] readBytes(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            copyStreams(in, out);
        }
        return out.toByteArray();
    }

    /**
     * 以 8192 字节缓冲做流复制。
     */
    private static void copyStreams(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * 列出目录子项；返回 null（IO 层失败）时统一转为 IOException。
     */
    private static File[] listChildren(File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            throw new IOException("无法列出目录内容: " + dir);
        }
        return children;
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

    /**
     * 判断文件名是否以指定扩展名结尾（忽略大小写，扩展名不含点）。
     */
    private static boolean hasExtension(File file, String extension) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        String dotExtension = "." + extension.toLowerCase(Locale.ROOT);
        // 长度约束排除文件名恰好就是 ".txt" 这类无主名的隐藏文件
        return lowerName.length() > dotExtension.length() && lowerName.endsWith(dotExtension);
    }
}

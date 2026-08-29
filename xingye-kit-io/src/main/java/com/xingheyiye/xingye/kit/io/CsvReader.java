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
 * @since 2026-08-27
 */

package com.xingheyiye.xingye.kit.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 按 RFC 4180 规范解析 CSV 的读取器：手写状态机，正确处理引号内逗号、引号内换行与双引号转义。
 *
 * <p>解析规则（RFC 4180 摘要）：</p>
 * <ul>
 *   <li>字段以逗号分隔，行以 CRLF 结束（本实现宽松兼容单独的 LF 与 CR）；</li>
 *   <li>含逗号、双引号、CR、LF 的字段必须用双引号包裹，内部双引号以两个连续双引号转义；</li>
 *   <li>文件首个字符若为 BOM（{@code \uFEFF}）会被剥离，避免首列列名带脏字符；</li>
 *   <li>流末尾的行结束符不会产生多余空行；引号字段未闭合（EOF 前未见到收尾引号）抛 {@link IOException}。</li>
 * </ul>
 *
 * <p>适用场景：读取 {@link CsvWriter} 生成的文件或外部系统导出的 CSV（配合 BOM 剥离兼容 Excel 导出）。</p>
 *
 * <p>线程安全性：实例持有读取游标与回退缓冲，<b>非线程安全</b>；一个实例只能被单线程顺序消费。</p>
 *
 * <p>资源语义：以 {@link Reader} 构造时底层流归调用方所有，{@link #close()} 不关闭它；
 * 以 {@link File} 构造时由本对象负责关闭。{@link #stream()} 为惰性流，消费完请关闭本读取器。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * try (CsvReader reader = new CsvReader(new File("users.csv"), StandardCharsets.UTF_8)) {
 *     for (String[] row : reader.readAll()) {
 *         System.out.println(row[0] + " / " + row[1]);
 *     }
 * }
 * // 大文件可改用惰性流：
 * try (CsvReader reader = new CsvReader(new File("users.csv"), StandardCharsets.UTF_8);
 *         Stream<String[]> rows = reader.stream()) {
 *     rows.filter(row -> !"0".equals(row[0])).forEach(row -&gt; process(row));
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-27
 */
public final class CsvReader implements AutoCloseable {

    /** 状态：字段起始（尚未消费属于本字段的任何字符）。 */
    private static final int STATE_FIELD_START = 0;
    /** 状态：未加引号的普通字段内部。 */
    private static final int STATE_IN_UNQUOTED = 1;
    /** 状态：引号字段内部（此间的逗号与换行均为字面内容）。 */
    private static final int STATE_IN_QUOTED = 2;
    /** 状态：引号字段中刚读到双引号，语义待定（转义引号/字段结束/行结束）。 */
    private static final int STATE_AFTER_QUOTE = 3;
    /** 流结束标志（Reader.read 的约定值）。 */
    private static final int EOF = -1;
    /** “暂无回退字符”哨兵值，取 -2 以便与 EOF(-1) 区分。 */
    private static final int NO_PUSHED_CHAR = -2;
    /** UTF-8 BOM 字符（文件首字符若为它则剥离）。 */
    private static final char BOM = '\uFEFF';
    /** 字段分隔符：RFC 4180 规定为半角逗号。 */
    private static final char FIELD_SEPARATOR = ',';
    /** 字段包裹/转义字符：半角双引号。 */
    private static final char QUOTE_CHAR = '"';
    /** 缓冲读取大小：8192 字符。 */
    private static final int BUFFER_SIZE = 8192;

    /** 底层缓冲字符流。 */
    private final BufferedReader reader;
    /** 是否由本对象负责关闭底层流（File 构造为 true，Reader 构造为 false）。 */
    private final boolean ownReader;
    /** 一字符回退缓冲（处理 CR 后窥探 LF 时使用），由消费线程独占访问。 */
    private int pushedBackChar = NO_PUSHED_CHAR;
    /** 是否已检查过 BOM（只允许剥离文件最开头的一个）。 */
    private boolean bomChecked;

    /**
     * 以既有 Reader 创建读取器（内部再包一层缓冲）。
     *
     * @param reader 底层字符流，不可为 null；close() 不会关闭它，由调用方管理生命周期
     * @throws NullPointerException reader 为 null
     */
    public CsvReader(Reader reader) {
        this.reader = new BufferedReader(Objects.requireNonNull(reader, "reader 不能为 null"), BUFFER_SIZE);
        this.ownReader = false;
    }

    /**
     * 以文件创建读取器。
     *
     * @param file 目标文件，不可为 null
     * @param charset 解码字符集，不可为 null（建议 UTF-8）
     * @throws java.io.FileNotFoundException 文件不存在（IOException 子类）
     * @throws IOException 打开文件失败
     * @throws NullPointerException 任一参数为 null
     */
    public CsvReader(File file, Charset charset) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        Objects.requireNonNull(charset, "charset 不能为 null");
        this.reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset), BUFFER_SIZE);
        this.ownReader = true; // 文件流由本对象打开，也就由本对象负责关闭
    }

    /**
     * 一次性读出全部行（适合中小文件；大文件请用 {@link #stream()} 惰性消费）。
     *
     * @return 行集合（每行为字段数组，无行则为空数组），永不为 null；文件为空时为空列表
     * @throws IOException 读取失败或遇到未闭合的引号字段
     */
    public List<String[]> readAll() throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        String[] row = readRow();
        while (row != null) {
            rows.add(row);
            row = readRow();
        }
        return rows;
    }

    /**
     * 以惰性流的方式逐行读取：只在终端操作拉取时才解析下一行，适合大文件。
     *
     * <p>读取中出现的 {@link IOException} 会被包装为 {@link UncheckedIOException} 从流的终端操作抛出；
     * 流本身不负责关闭底层资源，调用方需用 try-with-resources 同时管理本读取器与返回的流。</p>
     *
     * @return 按文件顺序的行流（元素永不为 null），永不为 null 本身
     */
    public Stream<String[]> stream() {
        Iterator<String[]> iterator = new Iterator<String[]>() {

            /** 预取的下一行，null 表示流已读完。 */
            private String[] nextRow = readRowUnchecked();

            @Override
            public boolean hasNext() {
                return nextRow != null;
            }

            @Override
            public String[] next() {
                String[] current = nextRow;
                if (current == null) {
                    throw new NoSuchElementException("CSV 流已读完");
                }
                nextRow = readRowUnchecked();
                return current;
            }
        };
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false);
    }

    /**
     * 关闭读取器：仅当底层流由本对象打开（File 构造）时才关闭底层流。
     *
     * @throws IOException 关闭失败
     */
    @Override
    public void close() throws IOException {
        if (ownReader) {
            reader.close();
        }
    }

    // ------------------------------------------------------------------
    // 状态机解析
    // ------------------------------------------------------------------

    /**
     * 解析下一行；流正常结束返回 null。
     */
    private String[] readRow() throws IOException {
        List<String> fields = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        int state = STATE_FIELD_START;
        boolean rowStarted = false; // 本行是否消费过任何字符，用于区分“空行”与“流结束”
        while (true) {
            int c = readChar();
            if (c == EOF) {
                if (!rowStarted) {
                    return null; // 上一行已以行结束符收尾，流正常结束
                }
                if (state == STATE_IN_QUOTED) {
                    throw new IOException("CSV 引号字段在流结束前未闭合");
                }
                fields.add(cell.toString());
                return toArray(fields);
            }
            rowStarted = true;
            switch (state) {
                case STATE_FIELD_START:
                    if (c == QUOTE_CHAR) {
                        state = STATE_IN_QUOTED;
                    } else if (c == FIELD_SEPARATOR) {
                        fields.add(""); // 空字段：保持字段起始态继续解析
                    } else if (isRowEnd(c)) {
                        fields.add(cell.toString());
                        return toArray(fields);
                    } else {
                        cell.append((char) c);
                        state = STATE_IN_UNQUOTED;
                    }
                    break;
                case STATE_IN_UNQUOTED:
                    if (c == FIELD_SEPARATOR) {
                        fields.add(cell.toString());
                        cell.setLength(0);
                        state = STATE_FIELD_START;
                    } else if (isRowEnd(c)) {
                        fields.add(cell.toString());
                        return toArray(fields);
                    } else {
                        cell.append((char) c);
                    }
                    break;
                case STATE_IN_QUOTED:
                    if (c == QUOTE_CHAR) {
                        state = STATE_AFTER_QUOTE; // 可能是字段结束，也可能是转义引号的前半
                    } else {
                        cell.append((char) c); // 引号内的逗号、CR、LF 均按字面内容追加
                    }
                    break;
                case STATE_AFTER_QUOTE:
                    if (c == QUOTE_CHAR) {
                        cell.append(QUOTE_CHAR); // 连续两个引号：转义为单个引号
                        state = STATE_IN_QUOTED;
                    } else if (c == FIELD_SEPARATOR) {
                        fields.add(cell.toString());
                        cell.setLength(0);
                        state = STATE_FIELD_START;
                    } else if (isRowEnd(c)) {
                        fields.add(cell.toString());
                        return toArray(fields);
                    } else {
                        // 宽松处理：闭引号后紧跟普通字符按字面追加（严格 RFC 视为非法输入）
                        cell.append((char) c);
                        state = STATE_IN_QUOTED;
                    }
                    break;
                default:
                    throw new IllegalStateException("未知的解析状态: " + state);
            }
        }
    }

    /**
     * 判定字符是否行结束并消费之：兼容 CRLF、单独 LF 与单独 CR；
     * CR 后窥探到的非 LF 字符会被回退，留给下一行解析。
     */
    private boolean isRowEnd(int c) throws IOException {
        if (c == '\n') {
            return true;
        }
        if (c == '\r') {
            int next = readChar();
            if (next != '\n' && next != EOF) {
                pushBack(next); // 不是 CRLF，把多读的字符还给下一行
            }
            return true;
        }
        return false;
    }

    /**
     * 读取一个字符：优先消费回退缓冲，并在文件最开头剥离一次 BOM。
     */
    private int readChar() throws IOException {
        if (pushedBackChar != NO_PUSHED_CHAR) {
            int c = pushedBackChar;
            pushedBackChar = NO_PUSHED_CHAR;
            return c;
        }
        int c = reader.read();
        if (!bomChecked) {
            bomChecked = true;
            if (c == BOM) {
                c = reader.read(); // 剥离 BOM，仅文件最开头一次
            }
        }
        return c;
    }

    /**
     * 回退一个字符（仅支持一个字符的回退，满足行结束窥探需求）。
     */
    private void pushBack(int c) {
        pushedBackChar = c;
    }

    /**
     * 把字段列表转为数组。
     */
    private static String[] toArray(List<String> fields) {
        return fields.toArray(new String[fields.size()]);
    }

    /**
     * 把受检的 readRow 包装为非受检异常（供流迭代器使用）。
     */
    private String[] readRowUnchecked() {
        try {
            return readRow();
        } catch (IOException e) {
            throw new UncheckedIOException("读取 CSV 失败", e);
        }
    }
}

/*
 * Copyright (C) 2026 Pono contributors
 *
 * This file is part of OpenPnP.
 *
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.machine.reference.solutions;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Collects what {@link MachineDiagnostics} measured into a directory of files: one readable
 * report, the raw controller output, a snapshot of the settings the measurements were taken
 * under, and a CSV per measurement series so that the numbers can be plotted elsewhere.
 * <p>
 * Written incrementally, so that a run that is stopped part way, or that fails in one test group,
 * still leaves behind everything measured up to that point.
 */
public class MachineDiagnosticsReport {

    public enum Severity {
        Info("     "),
        Warning("WARN "),
        Problem("  !! ");

        private final String prefix;

        Severity(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    public static class Finding {
        public final Severity severity;
        public final String text;

        public Finding(Severity severity, String text) {
            this.severity = severity;
            this.text = text;
        }
    }

    private static final String REPORT_FILE = "report.txt";

    private final File directory;
    private final StringBuilder body = new StringBuilder();
    private final List<Finding> findings = new ArrayList<>();
    private final Date started = new Date();

    public MachineDiagnosticsReport(File parentDirectory) throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(started);
        File dir = new File(parentDirectory, stamp);
        // A second run within the same second must not overwrite the first one's files.
        int suffix = 1;
        while (dir.exists()) {
            dir = new File(parentDirectory, stamp + "-" + (++suffix));
        }
        if (!dir.mkdirs()) {
            throw new IOException("Cannot create the report directory " + dir.getAbsolutePath() + ".");
        }
        this.directory = dir;
    }

    public File getDirectory() {
        return directory;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public MachineDiagnosticsReport section(String title) {
        if (body.length() > 0) {
            body.append("\n");
        }
        body.append(title).append("\n");
        for (int i = 0; i < title.length(); i++) {
            body.append('-');
        }
        body.append("\n");
        return this;
    }

    public MachineDiagnosticsReport line(String text) {
        body.append(text).append("\n");
        return this;
    }

    public MachineDiagnosticsReport line(String format, Object... arguments) {
        return line(String.format(format, arguments));
    }

    public MachineDiagnosticsReport blank() {
        body.append("\n");
        return this;
    }

    /**
     * Record a conclusion. Findings are repeated at the head of the report, which is the part a
     * user reads first and often the only part they read.
     */
    public MachineDiagnosticsReport finding(Severity severity, String text) {
        findings.add(new Finding(severity, text));
        return this;
    }

    public MachineDiagnosticsReport finding(Severity severity, String format, Object... arguments) {
        return finding(severity, String.format(format, arguments));
    }

    public File writeText(String fileName, String content) throws IOException {
        File file = new File(directory, fileName);
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        return file;
    }

    /**
     * Write a measurement series. A byte order mark goes first because these files are opened in
     * a spreadsheet more often than not, and Excel reads a UTF-8 CSV without one as the local
     * code page.
     */
    public File writeCsv(String fileName, String[] header, List<Object[]> rows) throws IOException {
        File file = new File(directory, fileName);
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writeCsvRow(writer, header);
            for (Object[] row : rows) {
                writeCsvRow(writer, row);
            }
        }
        return file;
    }

    private void writeCsvRow(Writer writer, Object[] row) throws IOException {
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(csvCell(row[i]));
        }
        writer.write("\r\n");
    }

    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text;
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            text = Double.isFinite(number) ? String.format("%.6f", number) : "";
        }
        else {
            text = value.toString();
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /**
     * Write the report as it stands. Safe to call repeatedly, so that the file on disk keeps up
     * with a long run rather than appearing only at the end.
     */
    public File flush() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("Pono machine diagnostics\n");
        report.append("Started ")
              .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(started))
              .append("\n\n");
        if (findings.isEmpty()) {
            report.append("Findings: none recorded.\n");
        }
        else {
            report.append("Findings\n--------\n");
            for (Finding finding : findings) {
                report.append(finding.severity.getPrefix()).append(finding.text).append("\n");
            }
        }
        report.append("\n");
        report.append(body);
        return writeText(REPORT_FILE, report.toString());
    }

    public File getReportFile() {
        return new File(directory, REPORT_FILE);
    }
}

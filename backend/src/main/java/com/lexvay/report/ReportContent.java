package com.lexvay.report;

import java.util.List;

/** What goes into the evidence report, independent of the PDF layout. */
record ReportContent(String title, List<String[]> header, List<Item> items, String footer) {

    record Item(String heading, List<String> lines) {
    }
}

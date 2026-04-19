package com.heima.codereview.rag.embedding;

import com.heima.codereview.common.model.norm.PdfPageContent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfExtractor {

    public List<PdfPageContent> extract(InputStream pdfStream) {
        try (PDDocument document = Loader.loadPDF(pdfStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<PdfPageContent> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text == null || text.isBlank()) {
                    continue;
                }
                pages.add(new PdfPageContent(page, text.trim(), summarize(text)));
            }
            return pages;
        } catch (IOException e) {
            throw new IllegalStateException("PDF解析失败: " + e.getMessage(), e);
        }
    }

    private String summarize(String text) {
        String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 140) + "...";
    }
}

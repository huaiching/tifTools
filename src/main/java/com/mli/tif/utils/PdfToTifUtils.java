package com.mli.tif.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PDF 轉換為 TIF 工具類
 */
public class PdfToTifUtils {

    private static final int DEFAULT_DPI = 300;

    /**
     * PDF → 多個單頁 TIF，打包成 ZIP（輸入 byte[]）
     * @param file  PDF檔案 (資料流)
     * @param dpi   解析度: 預設 300
     * @param pageSize  頁面大小: 預設 A4
     * @param isColor   使用彩色: 預設 false
     */
    public static byte[] convertPdfToSeparateTifsAsZip(byte[] file, Integer dpi, String pageSize, boolean isColor) throws IOException {
        dpi = (dpi != null && dpi > 0) ? dpi : DEFAULT_DPI;

        List<byte[]> tifPages = convertPdfToSeparateTifs(file, dpi, pageSize, isColor);
        return createZipFromTifs(tifPages, "tifFile");
    }

    /**
     * PDF → 單一多頁 TIF（輸入 byte[]）
     * @param file  PDF檔案 (資料流)
     * @param dpi   解析度: 預設 300
     * @param pageSize  頁面大小: 預設 A4
     * @param isColor   使用彩色: 預設 false
     */
    public static byte[] convertPdfToMultiPageTif(byte[] file, Integer dpi, String pageSize, boolean isColor) throws IOException {
        dpi = (dpi != null && dpi > 0) ? dpi : DEFAULT_DPI;
        return convertToMultiPageTif(file, dpi, pageSize, isColor);
    }

    // ================================================================
    // 共用方法區
    // ================================================================

    /**
     * 驗證上傳的檔案
     */
    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("檔案不能為空");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException("只接受 PDF 檔案");
        }
    }

    /**
     * 將 PDF 每頁轉為單一 TIF byte[]
     */
    private static List<byte[]> convertPdfToSeparateTifs(byte[] pdfBytes, int dpi, String pageSize, boolean isColor) throws IOException {
        List<byte[]> tifPages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDDocument resizedDoc = resizePdfPages(document, pageSize);
            try {
                PDFRenderer renderer = new PDFRenderer(resizedDoc);
                int pageCount = resizedDoc.getNumberOfPages();

                for (int i = 0; i < pageCount; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                    if (!isColor) image = convertToGrayscale(image);

                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(image, "tiff", baos);
                        tifPages.add(baos.toByteArray());
                    }
                }
            } finally {
                if (resizedDoc != document) resizedDoc.close();
            }
        }
        return tifPages;
    }

    /**
     * 將 PDF 轉為單一多頁 TIF
     */
    private static byte[] convertToMultiPageTif(byte[] pdfBytes, int dpi, String pageSize, boolean isColor) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDDocument resizedDoc = resizePdfPages(document, pageSize);

            try {
                PDFRenderer renderer = new PDFRenderer(resizedDoc);
                int pageCount = resizedDoc.getNumberOfPages();

                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
                if (!writers.hasNext()) {
                    throw new IOException("找不到 TIFF 編寫器");
                }

                ImageWriter writer = writers.next();
                ImageWriteParam writeParam = writer.getDefaultWriteParam();

                if (writeParam.canWriteCompressed()) {
                    writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    writeParam.setCompressionType("LZW");
                }

                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.prepareWriteSequence(null);

                    for (int i = 0; i < pageCount; i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                        if (!isColor) image = convertToGrayscale(image);

                        IIOMetadata metadata = writer.getDefaultImageMetadata(
                                new javax.imageio.ImageTypeSpecifier(image), writeParam);

                        writer.writeToSequence(new IIOImage(image, null, metadata), writeParam);
                    }

                    writer.endWriteSequence();
                } finally {
                    writer.dispose();
                }
            } finally {
                if (resizedDoc != document) resizedDoc.close();
            }

            return baos.toByteArray();
        }
    }

    /**
     * 調整 PDF 頁面大小
     */
    private static PDDocument resizePdfPages(PDDocument originalDoc, String pageSize) throws IOException {
        PDDocument newDoc = new PDDocument();
        PDFRenderer renderer = new PDFRenderer(originalDoc);

        for (int i = 0; i < originalDoc.getNumberOfPages(); i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, 150);
            PDPage newPage = new PDPage(PageSizeEnum.getPDRectangle(pageSize));
            newDoc.addPage(newPage);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", baos);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(newDoc, baos.toByteArray(), "page_" + i);

                float pageWidth = PageSizeEnum.getEnum(pageSize).getWidth();
                float pageHeight = PageSizeEnum.getEnum(pageSize).getHeight();
                float scale = Math.min(pageWidth / image.getWidth(), pageHeight / image.getHeight());
                float scaledWidth = image.getWidth() * scale;
                float scaledHeight = image.getHeight() * scale;
                float x = (pageWidth - scaledWidth) / 2;
                float y = (pageHeight - scaledHeight) / 2;

                try (PDPageContentStream contentStream = new PDPageContentStream(newDoc, newPage)) {
                    contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight);
                }
            }
        }
        return newDoc;
    }

    /**
     * 將多個 TIF 資料打包成 ZIP
     */
    private static byte[] createZipFromTifs(List<byte[]> tifPages, String originalFileName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String baseFileName = (originalFileName != null) ? originalFileName.replace(".pdf", "") : "tifFile";

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < tifPages.size(); i++) {
                ZipEntry entry = new ZipEntry(String.format("%s_page_%d.tif", baseFileName, i + 1));
                zos.putNextEntry(entry);
                zos.write(tifPages.get(i));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * 將彩色圖片轉為灰階
     */
    private static BufferedImage convertToGrayscale(BufferedImage colorImage) {
        BufferedImage grayscale = new BufferedImage(colorImage.getWidth(), colorImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayscale.createGraphics();
        g.drawImage(colorImage, 0, 0, null);
        g.dispose();
        return grayscale;
    }

    // ================================================================
    // 頁面大小 Enum
    // ================================================================
    public enum PageSizeEnum {
        A4("A4", 595, 842),
        A3("A3", 842, 1191),
        A5("A5", 420, 595),
        LETTER("LETTER", 612, 792),
        LEGAL("LEGAL", 612, 1008),
        TABLOID("TABLOID", 792, 1224);

        private final String pageName;
        private final float width;
        private final float height;

        PageSizeEnum(String pageName, float width, float height) {
            this.pageName = pageName;
            this.width = width;
            this.height = height;
        }

        public float getWidth() { return width; }
        public float getHeight() { return height; }

        public static PageSizeEnum getEnum(String pageName) {
            if (pageName == null || pageName.trim().isEmpty()) return A4;
            for (PageSizeEnum p : values()) {
                if (p.pageName.equalsIgnoreCase(pageName)) return p;
            }
            return A4;
        }

        public static PDRectangle getPDRectangle(String pageName) {
            PageSizeEnum ps = getEnum(pageName);
            return new PDRectangle(ps.width, ps.height);
        }
    }
}

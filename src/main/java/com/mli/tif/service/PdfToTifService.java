package com.mli.tif.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PdfToTifService {

    private static final int DEFAULT_DPI = 300;
    private static final String DEFAULT_PAGE_SIZE = "A4";

    /**
     * 色彩模式枚舉
     */
    public enum ColorMode {
        COLOR,      // 彩色
        GRAYSCALE   // 黑白（灰階）
    }

    /**
     * 頁面尺寸枚舉（單位：點，1英吋 = 72點）
     */
    public enum PageSize {
        A4(595, 842),           // 210 x 297 mm
        A3(842, 1191),          // 297 x 420 mm
        A5(420, 595),           // 148 x 210 mm
        LETTER(612, 792),       // 8.5 x 11 inch
        LEGAL(612, 1008),       // 8.5 x 14 inch
        TABLOID(792, 1224);     // 11 x 17 inch

        private final float width;
        private final float height;

        PageSize(float width, float height) {
            this.width = width;
            this.height = height;
        }

        public float getWidth() {
            return width;
        }

        public float getHeight() {
            return height;
        }

        public PDRectangle toPDRectangle() {
            return new PDRectangle(width, height);
        }
    }

    /**
     * 驗證上傳的檔案
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("檔案不能為空");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException("只接受 PDF 檔案");
        }
    }

    /**
     * 取得解析度，若為 null 或無效則回傳預設值
     */
    private int getResolution(Integer dpi) {
        return (dpi != null && dpi > 0) ? dpi : DEFAULT_DPI;
    }

    /**
     * 解析頁面大小參數
     */
    private PageSize parsePageSize(String pageSizeStr) {
        if (pageSizeStr == null || pageSizeStr.trim().isEmpty()) {
            return PageSize.A4;
        }

        try {
            return PageSize.valueOf(pageSizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支援的頁面大小: " + pageSizeStr +
                    ". 支援的格式: A4, A3, A5, LETTER, LEGAL, TABLOID");
        }
    }

    /**
     * 解析色彩模式參數
     * @param colorModeInt 1=黑白, 2=彩色, null=預設黑白
     */
    private ColorMode parseColorMode(Integer colorModeInt) {
        if (colorModeInt == null) {
            return ColorMode.GRAYSCALE; // 預設黑白
        }

        switch (colorModeInt) {
            case 1:
                return ColorMode.GRAYSCALE; // 黑白
            case 2:
                return ColorMode.COLOR;     // 彩色
            default:
                throw new IllegalArgumentException("不支援的色彩模式: " + colorModeInt +
                        ". 請使用 1（黑白）或 2（彩色）");
        }
    }

    /**
     * 將彩色圖片轉換為灰階
     */
    private BufferedImage convertToGrayscale(BufferedImage colorImage) {
        BufferedImage grayscaleImage = new BufferedImage(
                colorImage.getWidth(),
                colorImage.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D g = grayscaleImage.createGraphics();
        g.drawImage(colorImage, 0, 0, null);
        g.dispose();

        return grayscaleImage;
    }

    /**
     * 將 PDF 的所有頁面分別轉換為多個單頁 TIF，並打包成 ZIP
     */
    public byte[] convertPdfToSeparateTifsAsZip(MultipartFile file, Integer dpi, String pageSizeStr, Integer colorModeInt) throws IOException {
        validateFile(file);
        int resolution = getResolution(dpi);
        PageSize pageSize = parsePageSize(pageSizeStr);
        ColorMode colorMode = parseColorMode(colorModeInt);

        List<byte[]> tifPages = convertPdfToSeparateTifs(file.getBytes(), resolution, pageSize, colorMode);
        return createZipFromTifs(tifPages, file.getOriginalFilename());
    }

    /**
     * 將 PDF 的所有頁面分別轉換為多個單頁 TIF
     */
    private List<byte[]> convertPdfToSeparateTifs(byte[] pdfBytes, int dpi, PageSize pageSize, ColorMode colorMode) throws IOException {
        List<byte[]> tifPages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDDocument resizedDoc = resizePdfPages(document, pageSize);

            try {
                PDFRenderer renderer = new PDFRenderer(resizedDoc);
                int pageCount = resizedDoc.getNumberOfPages();

                for (int i = 0; i < pageCount; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, dpi);

                    // 如果需要轉換為灰階
                    if (colorMode == ColorMode.GRAYSCALE) {
                        image = convertToGrayscale(image);
                    }

                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(image, "tiff", baos);
                        tifPages.add(baos.toByteArray());
                    }
                }
            } finally {
                if (resizedDoc != document) {
                    resizedDoc.close();
                }
            }
        }

        return tifPages;
    }

    /**
     * 調整 PDF 頁面大小
     */
    private PDDocument resizePdfPages(PDDocument originalDoc, PageSize targetSize) throws IOException {
        PDDocument newDoc = new PDDocument();
        PDFRenderer renderer = new PDFRenderer(originalDoc);

        for (int i = 0; i < originalDoc.getNumberOfPages(); i++) {
            // 渲染原始頁面為圖片
            BufferedImage image = renderer.renderImageWithDPI(i, 150);

            // 創建新頁面
            PDPage newPage = new PDPage(targetSize.toPDRectangle());
            newDoc.addPage(newPage);

            // 將圖片寫入新頁面
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", baos);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(newDoc, baos.toByteArray(), "page_" + i);

                // 計算縮放比例以符合目標頁面
                float pageWidth = targetSize.getWidth();
                float pageHeight = targetSize.getHeight();
                float imageWidth = image.getWidth();
                float imageHeight = image.getHeight();

                float scale = Math.min(pageWidth / imageWidth, pageHeight / imageHeight);
                float scaledWidth = imageWidth * scale;
                float scaledHeight = imageHeight * scale;

                // 居中放置
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
    private byte[] createZipFromTifs(List<byte[]> tifPages, String originalFileName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String baseFileName = originalFileName.replace(".pdf", "");

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
     * 將 PDF 的所有頁面轉換為單一多頁 TIF
     */
    public byte[] convertPdfToMultiPageTif(MultipartFile file, Integer dpi, String pageSizeStr, Integer colorModeInt) throws IOException {
        validateFile(file);
        int resolution = getResolution(dpi);
        PageSize pageSize = parsePageSize(pageSizeStr);
        ColorMode colorMode = parseColorMode(colorModeInt);

        return convertToMultiPageTif(file.getBytes(), resolution, pageSize, colorMode);
    }

    /**
     * 將 PDF bytes 轉換為多頁 TIF
     */
    private byte[] convertToMultiPageTif(byte[] pdfBytes, int dpi, PageSize pageSize, ColorMode colorMode) throws IOException {
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

                // 設定壓縮參數
                if (writeParam.canWriteCompressed()) {
                    writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    writeParam.setCompressionType("LZW");
                    writeParam.setCompressionQuality(1.0f);
                }

                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.prepareWriteSequence(null);

                    for (int i = 0; i < pageCount; i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i, dpi);

                        // 如果需要轉換為灰階
                        if (colorMode == ColorMode.GRAYSCALE) {
                            image = convertToGrayscale(image);
                        }

                        IIOMetadata metadata = writer.getDefaultImageMetadata(
                                new javax.imageio.ImageTypeSpecifier(image), writeParam);

                        writer.writeToSequence(new IIOImage(image, null, metadata), writeParam);
                    }

                    writer.endWriteSequence();
                } finally {
                    writer.dispose();
                }
            } finally {
                if (resizedDoc != document) {
                    resizedDoc.close();
                }
            }

            return baos.toByteArray();
        }
    }
}

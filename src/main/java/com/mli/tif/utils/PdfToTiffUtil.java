package com.mli.tif.utils;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * PDF 轉 TIFF 工具類 (使用 ICEpdf)
 *
 * Maven 依賴:
 * <dependency>
 *     <groupId>org.icepdf.os</groupId>
 *     <artifactId>icepdf-core</artifactId>
 *     <version>6.3.2</version>
 * </dependency>
 *
 * <!-- 支援多頁 TIFF -->
 * <dependency>
 *     <groupId>com.github.jai-imageio</groupId>
 *     <artifactId>jai-imageio-core</artifactId>
 *     <version>1.4.0</version>
 * </dependency>
 */
public class PdfToTiffUtil {

    private static final int DEFAULT_DPI = 300;

    /**
     * 將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）
     *
     * @param file 上傳的 PDF 檔案
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 生成的 TIFF 字節數組列表，每個元素對應一頁
     * @throws Exception 轉換異常
     */
    public static List<byte[]> pdfToSeparateTifs(MultipartFile file, boolean colorMode) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF 檔案不能為空");
        }

        return pdfToSeparateTifs(file.getInputStream(), colorMode);
    }

    /**
     * 將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）
     *
     * @param pdfInputStream PDF 輸入流
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 生成的 TIFF 字節數組列表，每個元素對應一頁
     * @throws Exception 轉換異常
     */
    public static List<byte[]> pdfToSeparateTifs(InputStream pdfInputStream, boolean colorMode) throws Exception {
        // 臨時保存 PDF
        File tempPdf = File.createTempFile("temp_pdf_", ".pdf");
        try {
            try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = pdfInputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            List<BufferedImage> images = renderPdfToImages(tempPdf.getAbsolutePath(), DEFAULT_DPI);
            List<byte[]> tiffBytesList = new ArrayList<>();

            for (BufferedImage image : images) {
                // 根據彩色模式轉換圖片
                BufferedImage processedImage = colorMode ? image : convertToGrayscale(image);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writeSinglePageTiff(processedImage, ios);
                }
                tiffBytesList.add(baos.toByteArray());
            }

            return tiffBytesList;

        } finally {
            if (tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

    /**
     * 將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）
     *
     * @param pdfFile PDF 檔案
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 生成的 TIFF 字節數組列表，每個元素對應一頁
     * @throws Exception 轉換異常
     */
    public static List<byte[]> pdfToSeparateTifs(File pdfFile, boolean colorMode) throws Exception {
        return pdfToSeparateTifs(new FileInputStream(pdfFile), colorMode);
    }

    /**
     * 將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）
     *
     * @param pdfBytes PDF 字節數組
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 生成的 TIFF 字節數組列表，每個元素對應一頁
     * @throws Exception 轉換異常
     */
    public static List<byte[]> pdfToSeparateTifs(byte[] pdfBytes, boolean colorMode) throws Exception {
        return pdfToSeparateTifs(new ByteArrayInputStream(pdfBytes), colorMode);
    }

    /**
     * 將 PDF 所有頁面轉換為單一多頁 TIF 檔案
     *
     * @param file 上傳的 PDF 檔案
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 多頁 TIFF 字節數組
     * @throws Exception 轉換異常
     */
    public static byte[] pdfToMultiPageTif(MultipartFile file, boolean colorMode) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF 檔案不能為空");
        }

        return pdfToMultiPageTif(file.getInputStream(), colorMode);
    }

    /**
     * 將 PDF 所有頁面轉換為單一多頁 TIF 檔案
     *
     * @param pdfInputStream PDF 輸入流
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 多頁 TIFF 字節數組
     * @throws Exception 轉換異常
     */
    public static byte[] pdfToMultiPageTif(InputStream pdfInputStream, boolean colorMode) throws Exception {
        // 臨時保存 PDF
        File tempPdf = File.createTempFile("temp_pdf_", ".pdf");
        try {
            try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = pdfInputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            List<BufferedImage> images = renderPdfToImages(tempPdf.getAbsolutePath(), DEFAULT_DPI);

            // 根據彩色模式轉換圖片
            List<BufferedImage> processedImages = new ArrayList<>();
            for (BufferedImage image : images) {
                processedImages.add(colorMode ? image : convertToGrayscale(image));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writeMultiPageTiff(processedImages, ios);
            }

            return baos.toByteArray();

        } finally {
            if (tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

    /**
     * 將 PDF 所有頁面轉換為單一多頁 TIF 檔案
     *
     * @param pdfFile PDF 檔案
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 多頁 TIFF 字節數組
     * @throws Exception 轉換異常
     */
    public static byte[] pdfToMultiPageTif(File pdfFile, boolean colorMode) throws Exception {
        return pdfToMultiPageTif(new FileInputStream(pdfFile), colorMode);
    }

    /**
     * 將 PDF 所有頁面轉換為單一多頁 TIF 檔案
     *
     * @param pdfBytes PDF 字節數組
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return 多頁 TIFF 字節數組
     * @throws Exception 轉換異常
     */
    public static byte[] pdfToMultiPageTif(byte[] pdfBytes, boolean colorMode) throws Exception {
        return pdfToMultiPageTif(new ByteArrayInputStream(pdfBytes), colorMode);
    }

    /**
     * 使用 ICEpdf 渲染 PDF 為圖片列表
     */
    private static List<BufferedImage> renderPdfToImages(String pdfPath, int dpi) {
        Document document = new Document();
        List<BufferedImage> images = new ArrayList<>();

        try {
            document.setFile(pdfPath);

            // 計算縮放比例
            float scale = dpi / 72f; // 72 是 PDF 的默認 DPI
            float rotation = 0f;

            // 渲染每一頁
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = (BufferedImage) document.getPageImage(
                        i,
                        GraphicsRenderingHints.SCREEN,
                        Page.BOUNDARY_CROPBOX,
                        rotation,
                        scale
                );
                images.add(image);
            }

        } catch (Exception e) {
            throw new RuntimeException("PDF 渲染失敗", e);
        } finally {
            document.dispose();
        }

        return images;
    }

    /**
     * 將彩色圖片轉換為灰階（黑白）
     */
    private static BufferedImage convertToGrayscale(BufferedImage colorImage) {
        BufferedImage grayImage = new BufferedImage(
                colorImage.getWidth(),
                colorImage.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D g2d = grayImage.createGraphics();
        g2d.drawImage(colorImage, 0, 0, null);
        g2d.dispose();

        return grayImage;
    }

    /**
     * 寫入單頁 TIFF
     */
    private static void writeSinglePageTiff(BufferedImage image, ImageOutputStream ios) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("TIFF");
        if (!writers.hasNext()) {
            throw new IllegalStateException("找不到 TIFF ImageWriter");
        }

        ImageWriter writer = writers.next();
        writer.setOutput(ios);

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionType("LZW");
            writeParam.setCompressionQuality(1.0f);
        }

        try {
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
    }

    /**
     * 寫入多頁 TIFF
     */
    private static void writeMultiPageTiff(List<BufferedImage> images, ImageOutputStream ios) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("圖片列表不能為空");
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("TIFF");
        if (!writers.hasNext()) {
            throw new IllegalStateException("找不到 TIFF ImageWriter");
        }

        ImageWriter writer = writers.next();
        writer.setOutput(ios);

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionType("LZW");
            writeParam.setCompressionQuality(1.0f);
        }

        try {
            writer.prepareWriteSequence(null);

            for (BufferedImage image : images) {
                writer.writeToSequence(new IIOImage(image, null, null), writeParam);
            }

            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }
}
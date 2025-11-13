package com.mli.tif.utils;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * PDF 轉 TIFF 工具類 (使用 ICEpdf) - 無臨時文件版本
 */
public class PdfToTiffUtil {

    private static final int DEFAULT_DPI = 300;

    /**
     * 將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案），並打包成 ZIP
     *
     * @param pdfBytes PDF 字節數組
     * @param colorMode 彩色模式: true=彩色, false=黑白(預設)
     * @return ZIP 字節數組，包含多個 .tif 檔案
     * @throws Exception 轉換異常
     */
    public static byte[] pdfToSeparateTifs(byte[] pdfBytes, boolean colorMode) throws Exception {
        List<BufferedImage> images = renderPdfToImages(pdfBytes, DEFAULT_DPI);
        Map<String, byte[]> tiffBytesList = new HashMap<>();

        for (int i = 0; i < images.size(); i++) {
            BufferedImage image = images.get(i);
            BufferedImage processedImage = colorMode ? image : convertToGrayscale(image);

            ByteArrayOutputStream tiffBaos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(tiffBaos)) {
                writeSinglePageTiff(processedImage, ios);
            }

            String fileName = String.format("tifFile_%03d.tif", i + 1);
            tiffBytesList.put(fileName, tiffBaos.toByteArray());
        }

        return ZipUtil.createZip(tiffBytesList);
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
        List<BufferedImage> images = renderPdfToImages(pdfBytes, DEFAULT_DPI);

        List<BufferedImage> processedImages = new ArrayList<>();
        for (BufferedImage image : images) {
            processedImages.add(colorMode ? image : convertToGrayscale(image));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writeMultiPageTiff(processedImages, ios);
        }

        return baos.toByteArray();
    }

    /**
     * 使用 ICEpdf 直接從 byte[] 渲染 PDF 為圖片列表（無臨時文件）
     */
    private static List<BufferedImage> renderPdfToImages(byte[] pdfBytes, int dpi) {
        Document document = new Document();
        List<BufferedImage> images = new ArrayList<>();

        try {
            document.setByteArray(pdfBytes, 0, pdfBytes.length, "");

            float scale = dpi / 72f; // 72 是 PDF 默認 DPI
            float rotation = 0f;

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
        ImageWriter writer = getTiffImageWriter();
        writer.setOutput(ios);

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        setupLzwCompression(writeParam);

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
        if (images.isEmpty()) {
            throw new IllegalArgumentException("圖片列表不能為空");
        }

        ImageWriter writer = getTiffImageWriter();
        writer.setOutput(ios);

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        setupLzwCompression(writeParam);

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

    /**
     * 獲取 TIFF ImageWriter（避免重複查找）
     */
    private static ImageWriter getTiffImageWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("TIFF");
        if (!writers.hasNext()) {
            throw new IllegalStateException("找不到 TIFF ImageWriter");
        }
        return writers.next();
    }

    /**
     * 設置 LZW 壓縮（共用邏輯）
     */
    private static void setupLzwCompression(ImageWriteParam writeParam) {
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionType("LZW");
            writeParam.setCompressionQuality(1.0f);
        }
    }
}
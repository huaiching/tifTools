package com.mli.tif.utils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class PdfToTiffUtil {

    // ====================== 1. 每頁獨立 TIF ======================
    /**
     * PDF 每頁轉成獨立 TIF，並回傳 Map<String, byte[]>
     *
     * @param pdfBytes   PDF 內容
     * @param color      true=彩色, false=灰階
     * @return ZIP bype[]
     */
    public static byte[] pdfToSeparateTifs(byte[] pdfBytes, boolean color) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF bytes 不能為空");
        }

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(document);
            ImageType imageType = color ? ImageType.RGB : ImageType.GRAY;

            Map<String, byte[]> tifMap = new HashMap<>();
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300, imageType);
                byte[] tifBytes = bufferedImageToTiffBytes(image); // 單頁轉換函式

                String fileName = String.format("%s%03d.tif", "tifFile_", i + 1);
                tifMap.put(fileName, tifBytes);
            }

            return ZipUtil.createZip(tifMap);
        }
    }

    // ====================== 2. 單一多頁 TIF ======================
    /**
     * 所有頁面合成一個多頁 TIF
     *
     * @param pdfBytes   PDF 內容
     * @param color      true=彩色, false=灰階
     * @return TIF byte[]
     */
    public static byte[] pdfToMultiPageTif(byte[] pdfBytes, boolean color) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF bytes 不能為空");
        }

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes));
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {

            PDFRenderer renderer = new PDFRenderer(document);
            ImageType imageType = color ? ImageType.RGB : ImageType.GRAY;

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tif");
            if (!writers.hasNext()) {
                throw new RuntimeException("系統未安裝 TIFF writer");
            }
            ImageWriter writer = writers.next();

            try (MemoryCacheImageOutputStream mos = new MemoryCacheImageOutputStream(os)) {
                writer.setOutput(mos);

                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionType("LZW");
                }

                int pageCount = document.getNumberOfPages();
                if (pageCount == 0) return new byte[0];

                writer.prepareWriteSequence(null);

                for (int i = 0; i < pageCount; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, 300, imageType);
                    IIOImage iioImage = new IIOImage(image, null, null);
                    writer.write(null, iioImage, param);
                }

                writer.endWriteSequence();  // 結束序列
            } finally {
                writer.dispose();
            }
            return os.toByteArray();
        }
    }

    // ====================== 內部共用：單頁 BufferedImage → TIFF byte[] ======================
    private static byte[] bufferedImageToTiffBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
            if (!writers.hasNext()) throw new RuntimeException("TIFF writer not found");

            ImageWriter writer = writers.next();
            try (MemoryCacheImageOutputStream mos = new MemoryCacheImageOutputStream(os)) {
                writer.setOutput(mos);

                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionType("LZW");
                }

                IIOImage iioImage = new IIOImage(image, null, null);
                writer.write(null, iioImage, param);
            } finally {
                writer.dispose();
            }
            return os.toByteArray();
        }
    }
}
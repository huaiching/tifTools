package com.mli.tif.utils;

import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zip 打包工具
 */
public class ZipUtil {

    /**
     * 將多個檔案壓縮成 ZIP 格式，並回傳為 byte 陣列
     * @param fileList 要壓縮的檔案 Map 清單（key=檔名, value=檔案內容）
     * @return 產出的 ZIP 檔案資料流（byte[]）
     */
    public static byte[] createZip(Map<String, byte[]> fileList) {
        // 參數驗證
        if (CollectionUtils.isEmpty(fileList)) {
            throw new RuntimeException("要打包的檔案 不可空白!!");
        }

        try (
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)
        ) {
            for (Map.Entry<String, byte[]> file : fileList.entrySet()) {
                String fileName = file.getKey();
                byte[] fileData = file.getValue();

                if (fileName == null || fileName.isBlank()) {
                    throw new RuntimeException("檔名不可為空");
                }
                if (fileData == null) {
                    throw new RuntimeException("檔案內容不可為空: " + fileName);
                }

                // 確保使用相對路徑格式
                String safeFileName = fileName.replaceAll("\\\\", "/");

                zipOutputStream.putNextEntry(new ZipEntry(safeFileName));
                zipOutputStream.write(fileData);
                zipOutputStream.closeEntry();
            }

            // 確保壓縮流正確結束
            zipOutputStream.finish();

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Zip 產生失敗: ", e);
        }
    }
}

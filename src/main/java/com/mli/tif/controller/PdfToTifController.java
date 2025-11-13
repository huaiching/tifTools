package com.mli.tif.controller;

import com.mli.tif.utils.PdfToTiffUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@Tag(name = "Tif Controller", description = "tif 生成 API")
@RequestMapping("/Tif")
public class PdfToTifController {

    /**
     * 將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）
     * 回傳 ZIP 壓縮檔包含所有 TIF 檔案
     *
     * @param file PDF 檔案（透過 multipart/form-data 上傳）
     */
    @Operation(summary = "將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）")
    @PostMapping(value = "/pdfToSeparateTifs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> pdfToSeparateTifs(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "彩色模式: true.是 / false.否(預設)", required = false) boolean isColor) throws Exception {

        byte[] fileByte = PdfToTiffUtil.pdfToSeparateTifs(file.getBytes(), isColor);
        String fileName = "output.zip";

        // 文件打包
        Resource resource = new ByteArrayResource(fileByte);
        // 文件下载
        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentDispositionFormData("attachment",
                URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .headers(respHeaders)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 將 PDF 所有頁面轉換為單一多頁 TIF 檔案
     *
     * @param file PDF 檔案（透過 multipart/form-data 上傳）
     */
    @Operation(summary = "將 PDF 所有頁面轉換為單一多頁 TIF 檔案")
    @PostMapping(value = "/pdfToMultiPageTif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> pdfToMultiPageTif(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "彩色模式: true.是 / false.否(預設)", required = false) boolean isColor) throws Exception {

        byte[] fileByte = PdfToTiffUtil.pdfToMultiPageTif(file.getBytes(), isColor);
        String fileName = "output.tif";

        // 文件打包
        Resource resource = new ByteArrayResource(fileByte);
        // 文件下载
        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentDispositionFormData("attachment",
                URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .headers(respHeaders)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

}

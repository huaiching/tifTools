package com.mli.tif.controller;

import com.mli.tif.service.PdfToTifService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Tif Controller", description = "tif 生成 API")
@RequestMapping("/Tif")
public class PdfToTifController {

    @Autowired
    private PdfToTifService pdfToTifService;

    /**
     * 將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）
     * 回傳 ZIP 壓縮檔包含所有 TIF 檔案
     *
     * @param file PDF 檔案（透過 multipart/form-data 上傳）
     * @param dpi 解析度，預設 300x300 DPI
     * @param pageSize 頁面大小（A4, A3, LETTER 等），預設 A4
     */
    @Operation(summary = "將 PDF 所有頁面轉換為多個 TIF 檔案（一頁一個檔案）")
    @PostMapping(value = "/pdf-to-separate-tifs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> convertPdfToSeparateTifs(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dpi (預設 300)", required = false) Integer dpi,
            @RequestParam(value = "pageSize (預設 A4)", required = false) String pageSize,
            @RequestParam(value = "色彩 (1.黑白(預設) / 2.彩色)", required = false) Integer colorMode) {

        try {
            byte[] zipBytes = pdfToTifService.convertPdfToSeparateTifsAsZip(file, dpi, pageSize, colorMode);
            ByteArrayResource resource = new ByteArrayResource(zipBytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    file.getOriginalFilename().replace(".pdf", "_pages.zip"));

            return new ResponseEntity<>(resource, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 將 PDF 所有頁面轉換為單一多頁 TIF 檔案
     *
     * @param file PDF 檔案（透過 multipart/form-data 上傳）
     * @param dpi 解析度，預設 300x300 DPI
     * @param pageSize 頁面大小（A4, A3, LETTER 等），預設 A4
     */
    @Operation(summary = "將 PDF 所有頁面轉換為單一多頁 TIF 檔案")
    @PostMapping(value = "/pdf-to-multi-page-tif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convertPdfToMultiPageTif(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dpi (預設 300)", required = false) Integer dpi,
            @RequestParam(value = "pageSize (預設 A4)", required = false) String pageSize,
            @RequestParam(value = "色彩 (1.黑白(預設) / 2.彩色)", required = false) Integer colorMode) {

        try {
            byte[] tifBytes = pdfToTifService.convertPdfToMultiPageTif(file, dpi, pageSize, colorMode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("image/tiff"));
            headers.setContentDispositionFormData("attachment",
                    file.getOriginalFilename().replace(".pdf", "_multi.tif"));

            return new ResponseEntity<>(tifBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}

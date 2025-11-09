package com.mli.tif.constants;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

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

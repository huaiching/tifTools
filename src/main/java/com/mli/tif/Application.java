package com.mli.tif;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Spring Boot 啟動完成後自動開啟 Swagger UI
     */
    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        String url = "http://localhost:9010/swagger-ui/index.html";

        // 支援 Linux、Mac、Windows
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            } catch (IOException e) {
                System.err.println("自動開啟瀏覽器失敗，請手動開啟: " + url);
            }
        }

        // 若 Desktop 不支援，採用 fallback 方式
        String os = System.getProperty("os.name").toLowerCase();
        Runtime rt = Runtime.getRuntime();

        try {
            if (os.contains("win")) {
                rt.exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else if (os.contains("mac")) {
                rt.exec("open " + url);
            } else if (os.contains("nix") || os.contains("nux")) {
                rt.exec("xdg-open " + url);
            } else {
                System.err.println("無法自動開啟瀏覽器，請手動開啟: " + url);
            }
        } catch (Exception e) {
            System.err.println("請手動開啟 Swagger UI: " + url);
        }
    }
}

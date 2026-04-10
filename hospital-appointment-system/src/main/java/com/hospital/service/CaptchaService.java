package com.hospital.service;

import com.hospital.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CaptchaService {

    private static final String REDIS_PREFIX = "hospital:captcha:";
    private static final int TTL_MINUTES = 5;
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LEN = 4;
    private static final int WIDTH = 110;
    private static final int HEIGHT = 40;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private RedisUtil redisUtil;

    public Map<String, String> generateCaptchaImage() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        redisUtil.set(REDIS_PREFIX + captchaId, code, TTL_MINUTES, TimeUnit.MINUTES);
        String imageBase64;
        try {
            imageBase64 = Base64.getEncoder().encodeToString(renderPng(code));
        } catch (IOException e) {
            log.error("Captcha image encode failed", e);
            redisUtil.delete(REDIS_PREFIX + captchaId);
            throw new RuntimeException("Captcha generation failed", e);
        }
        Map<String, String> out = new HashMap<>();
        out.put("captchaId", captchaId);
        out.put("imageBase64", imageBase64);
        return out;
    }

    public boolean validateAndConsume(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            return false;
        }
        String key = REDIS_PREFIX + captchaId.trim();
        Object stored = redisUtil.get(key);
        redisUtil.delete(key);
        if (stored == null) {
            return false;
        }
        String expected = String.valueOf(stored).trim();
        return expected.equalsIgnoreCase(captchaCode.trim());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private byte[] renderPng(String code) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setFont(new Font("Arial", Font.BOLD, 28));
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180)));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(random.nextInt(80), random.nextInt(80), random.nextInt(80)));
                int x = 18 + i * 22;
                int y = 28 + random.nextInt(6) - 3;
                double angle = (random.nextDouble() - 0.5) * 0.35;
                g.rotate(angle, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-angle, x, y);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}

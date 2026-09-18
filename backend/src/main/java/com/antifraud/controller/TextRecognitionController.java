package com.antifraud.controller;

import com.antifraud.service.TextRecognitionService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 文本识别接口：输入可疑文本 → 识别反诈类型 → 落日志 */
@RestController
@RequestMapping("/api/text")
public class TextRecognitionController {

    private final TextRecognitionService service;

    public TextRecognitionController(TextRecognitionService service) { this.service = service; }

    @PostMapping("/recognize")
    public Map<String, Object> recognize(@RequestBody Map<String, String> body) {
        return service.recognize(body.get("text"));
    }
}

package com.ssh.ssh.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class DefaultController {
    @GetMapping("/")
    public String index(){
        log.info("/ is called");
        return "abc";
    }
}

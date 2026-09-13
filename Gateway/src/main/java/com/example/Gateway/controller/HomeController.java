package com.example.Gateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class
HomeController {
    @GetMapping({"/", "/login"})
    public String getLogin() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String getDashboard(@RequestParam(defaultValue = "account") String service, Model model) {
        model.addAttribute("service", service);
        return "dashboard";
    }
}

package com.example.Gateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@Controller
public class HomeController {
    @Controller
    public class GatewayController {

        @GetMapping("/dashboard")
        public String getDashboard(Model model) {
            // dummy data
//            model.addAttribute("accounts", List.of(Map.of("name", "Savings", "detail", "5000")));
//            model.addAttribute("cards", List.of(Map.of("name", "Credit", "detail", "10000")));
//            model.addAttribute("services", List.of(Map.of("name", "Loan", "detail", "Active")));

            return "dashboard"; // template name without .html
        }
    }
}

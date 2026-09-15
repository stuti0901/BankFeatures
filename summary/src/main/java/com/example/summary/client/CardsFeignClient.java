package com.example.summary.client;

import com.example.summary.dto.CardsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "cards", url = "${services.cards.url}")
public interface CardsFeignClient {
    @GetMapping("/api/fetch")
    CardsDto fetchCard(@RequestParam("mobileNumber") String mobileNumber);
}
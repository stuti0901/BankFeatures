package com.example.cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ThreadLocalRandom;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CardsApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.example.cards.repository.CardsRepository repository;

    @Test
    void existingSeedCanBeUpdatedWithoutChangingItsNumber() throws Exception {
        // Preserve the original seed identifiers rather than renumber existing data.
        var existing = repository.findByMobileNumber("9876543210");
        if (existing.isEmpty()) return;
        var body = mvc.perform(get("/api/fetch").param("mobileNumber", "9876543210"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void crudPreservesAuditingAndDeletesOnlyTestData() throws Exception {
        String mobile;
        do {
            mobile = Long.toString(ThreadLocalRandom.current().nextLong(7000000000L, 8000000000L));
        } while (repository.findByMobileNumber(mobile).isPresent());

        mvc.perform(post("/api/create").param("mobileNumber", mobile)).andExpect(status().isCreated());
        var body = mvc.perform(get("/api/fetch").param("mobileNumber", mobile))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var dto = json.readValue(body, com.example.cards.dto.CardsDto.class);
        dto.setAmountUsed(100);
        dto.setAvailableAmount(99900);
        mvc.perform(put("/api/update").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto))).andExpect(status().isOk());
        mvc.perform(get("/api/fetch").param("mobileNumber", mobile))
                .andExpect(jsonPath("$.amountUsed").value(100));
        var entity = repository.findByMobileNumber(mobile).orElseThrow();
        assertNotNull(entity.getCreatedAt());
        assertEquals("CARDS_MS", entity.getCreatedBy());
        mvc.perform(delete("/api/delete").param("mobileNumber", mobile)).andExpect(status().isOk());
        mvc.perform(get("/api/fetch").param("mobileNumber", mobile)).andExpect(status().isNotFound());
    }
}

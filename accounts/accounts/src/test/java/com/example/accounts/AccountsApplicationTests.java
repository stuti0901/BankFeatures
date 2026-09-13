package com.example.accounts;

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
class AccountsApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.example.accounts.repository.CustomerRepository customers;
    @Autowired com.example.accounts.repository.AccountsRepository accounts;

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
        } while (customers.findByMobileNumber(mobile).isPresent());

        var input = new com.example.accounts.dto.CustomerDto();
        input.setName("Smoke Customer");
        input.setEmail("smoke@example.test");
        input.setMobileNumber(mobile);
        mvc.perform(post("/accounts/api/create").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(input))).andExpect(status().isCreated());
        var body = mvc.perform(get("/accounts/api/fetch").param("mobileNumber", mobile))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var dto = json.readValue(body, com.example.accounts.dto.CustomerDto.class);
        dto.setName("Updated Customer");
        mvc.perform(put("/accounts/api/update").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto))).andExpect(status().isOk());
        mvc.perform(get("/accounts/api/fetch").param("mobileNumber", mobile))
                .andExpect(jsonPath("$.name").value("Updated Customer"));
        var customer = customers.findByMobileNumber(mobile).orElseThrow();
        var account = accounts.findByCustomerId(customer.getCustomerId()).orElseThrow();
        assertNotNull(customer.getCreatedAt());
        assertEquals("ACCOUNTS_MS", customer.getCreatedBy());
        assertNotNull(account.getCreatedAt());
        assertEquals("ACCOUNTS_MS", account.getCreatedBy());
        mvc.perform(delete("/accounts/api/delete").param("mobileNumber", mobile)).andExpect(status().isOk());
        mvc.perform(get("/accounts/api/fetch").param("mobileNumber", mobile)).andExpect(status().isNotFound());
        assertTrue(customers.findByMobileNumber(mobile).isEmpty());
        assertTrue(accounts.findByCustomerId(customer.getCustomerId()).isEmpty());
    }
}

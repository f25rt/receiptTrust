package com.receipttrust;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LabelDebtorFlowTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    @LocalServerPort
    private int port;

    private String base() {
        return "http://localhost:" + port;
    }

    @Test
    void labelDebtorAndCustomDueDateAndAccumulation() throws Exception {
        register("Owner One", "owner1", "owner1@example.com", "password123");
        String token = login("owner1", "password123");

        Long receiptId = uploadReceipt(token);
        Long milk = addItem(token, receiptId, "Milk", "10.00");
        Long bread = addItem(token, receiptId, "Bread", "5.00");

        // Assign both items to the SAME label -> should accumulate into one debt of 15.00
        assignLabel(token, receiptId, milk, "Roommate Alex");
        assignLabel(token, receiptId, bread, "Roommate Alex");

        LocalDate due = LocalDate.now().plusDays(30);
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/receipts/" + receiptId + "/finalize?dueDate=" + due,
                HttpMethod.POST, new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode debts = mapper.readTree(resp.getBody());
        assertThat(debts).hasSize(1);
        JsonNode debt = debts.get(0);
        assertThat(debt.get("counterpartyUsername").asText()).isEqualTo("Roommate Alex");
        assertThat(debt.get("counterpartyIsLabel").asBoolean()).isTrue();
        assertThat(new BigDecimal(debt.get("originalAmount").asText())).isEqualByComparingTo("15.00");
        assertThat(debt.get("dueDate").asText()).isEqualTo(due.toString());

        // The label debt shows on the creditor's dashboard under owedToMe.
        JsonNode dash = get(token, "/api/debts/dashboard");
        assertThat(new BigDecimal(dash.get("totalOwedToMe").asText())).isEqualByComparingTo("15.00");
        JsonNode owed = dash.get("owedToMe");
        assertThat(owed).hasSize(1);
        assertThat(owed.get(0).get("counterpartyIsLabel").asBoolean()).isTrue();
    }

    // ---- helpers ----

    private void register(String fullName, String username, String email, String password) {
        rest.postForEntity(base() + "/api/auth/register",
                Map.of("fullName", fullName, "username", username, "email", email, "password", password),
                String.class);
    }

    private String login(String username, String password) throws Exception {
        ResponseEntity<String> resp = rest.postForEntity(base() + "/api/auth/login",
                Map.of("username", username, "password", password), String.class);
        return mapper.readTree(resp.getBody()).get("accessToken").asText();
    }

    private Long uploadReceipt(String token) throws Exception {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("storeName", "Corner Store");
        form.add("purchaseDate", LocalDate.now().toString());
        ByteArrayResource image = new ByteArrayResource("img".getBytes()) {
            @Override
            public String getFilename() {
                return "r.png";
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        form.add("image", new HttpEntity<>(image, partHeaders));
        ResponseEntity<String> resp = rest.exchange(base() + "/api/receipts", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
        return mapper.readTree(resp.getBody()).get("id").asLong();
    }

    private Long addItem(String token, Long receiptId, String name, String price) throws Exception {
        HttpHeaders headers = jsonAuthHeaders(token);
        String payload = mapper.writeValueAsString(Map.of("name", name, "quantity", 1, "unitPrice", price));
        ResponseEntity<String> resp = rest.exchange(base() + "/api/receipts/" + receiptId + "/items",
                HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
        return mapper.readTree(resp.getBody()).get("id").asLong();
    }

    private void assignLabel(String token, Long receiptId, Long itemId, String label) throws Exception {
        HttpHeaders headers = jsonAuthHeaders(token);
        String payload = mapper.writeValueAsString(Map.of(
                "splitType", "INDIVIDUAL",
                "targets", List.of(Map.of("label", label))));
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/receipts/" + receiptId + "/items/" + itemId + "/assignments",
                HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode get(String token, String path) throws Exception {
        ResponseEntity<String> resp = rest.exchange(base() + path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        return mapper.readTree(resp.getBody());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders jsonAuthHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

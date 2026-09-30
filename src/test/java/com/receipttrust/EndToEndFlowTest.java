package com.receipttrust;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EndToEndFlowTest {

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
    void fullDebtLifecycle() throws Exception {
        // Register creditor (John) and debtor (Sarah)
        register("John Doe", "john", "john@example.com", "password123");
        register("Sarah Lee", "sarah", "sarah@example.com", "password123");

        String johnToken = login("john", "password123");
        String sarahToken = login("sarah", "password123");

        // John and Sarah become friends
        Long requestId = sendFriendRequest(johnToken, "sarah");
        acceptFriendRequest(sarahToken, requestId);

        // John uploads a receipt
        Long receiptId = uploadReceipt(johnToken, "Costco", LocalDate.now());

        // John adds two items: Milk 10.00, Bread 5.00
        Long milkId = addItem(johnToken, receiptId, "Milk", 1, "10.00");
        Long breadId = addItem(johnToken, receiptId, "Bread", 1, "5.00");

        // Assign both items to Sarah individually
        assign(johnToken, receiptId, milkId, "INDIVIDUAL", List.of("sarah"));
        assign(johnToken, receiptId, breadId, "INDIVIDUAL", List.of("sarah"));

        // Finalize -> one debt for Sarah of 15.00
        JsonNode debts = finalize(johnToken, receiptId);
        assertThat(debts).hasSize(1);
        long debtId = debts.get(0).get("debtId").asLong();
        assertThat(new BigDecimal(debts.get(0).get("originalAmount").asText()))
                .isEqualByComparingTo("15.00");

        // Sarah's dashboard shows she owes 15.00
        JsonNode sarahDash = get(sarahToken, "/api/debts/dashboard");
        assertThat(new BigDecimal(sarahDash.get("totalIOwe").asText()))
                .isEqualByComparingTo("15.00");

        // Explanation lists Milk + Bread summing to 15.00
        JsonNode explanation = get(sarahToken, "/api/debts/" + debtId + "/explanation");
        assertThat(explanation.get("paidByUsername").asText()).isEqualTo("john");
        assertThat(explanation.get("storeName").asText()).isEqualTo("Costco");
        BigDecimal itemsSum = BigDecimal.ZERO;
        for (JsonNode item : explanation.get("items")) {
            itemsSum = itemsSum.add(new BigDecimal(item.get("amount").asText()));
        }
        assertThat(itemsSum).isEqualByComparingTo("15.00");

        // Sarah pays 15.00
        long paymentId = submitPayment(sarahToken, debtId, "15.00", "GCASH");

        // John approves -> debt settled, Sarah's trust score rises
        ResponseEntity<String> approve = exchange(johnToken, HttpMethod.POST,
                "/api/payments/" + paymentId + "/approve", null);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Debt now settled
        JsonNode debt = get(sarahToken, "/api/debts/" + debtId);
        assertThat(debt.get("status").asText()).isEqualTo("SETTLED");
        assertThat(new BigDecimal(debt.get("outstandingAmount").asText()))
                .isEqualByComparingTo("0.00");

        // Sarah started at 500; full settlement (+15) plus early (+10) = 525
        JsonNode sarahProfile = get(sarahToken, "/api/me");
        assertThat(sarahProfile.get("trustScore").asInt()).isEqualTo(525);
        assertThat(sarahProfile.get("debtsSettled").asLong()).isEqualTo(1);

        // History has the full lifecycle
        JsonNode history = get(sarahToken, "/api/debts/" + debtId + "/history");
        List<String> types = history.get("events").findValuesAsText("type");
        assertThat(types).contains("RECEIPT_UPLOADED", "DEBT_CREATED",
                "PAYMENT_SUBMITTED", "PAYMENT_APPROVED", "DEBT_SETTLED");
    }

    // ---- helpers ----

    private void register(String fullName, String username, String email, String password) {
        Map<String, String> body = Map.of(
                "fullName", fullName, "username", username, "email", email, "password", password);
        ResponseEntity<String> resp = rest.postForEntity(base() + "/api/auth/register", body, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String login(String username, String password) throws Exception {
        Map<String, String> body = Map.of("username", username, "password", password);
        ResponseEntity<String> resp = rest.postForEntity(base() + "/api/auth/login", body, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(resp.getBody()).get("accessToken").asText();
    }

    private Long sendFriendRequest(String token, String addressee) throws Exception {
        JsonNode node = post(token, "/api/friends/requests",
                Map.of("addresseeUsername", addressee));
        return node.get("requestId").asLong();
    }

    private void acceptFriendRequest(String token, Long requestId) throws Exception {
        exchange(token, HttpMethod.POST, "/api/friends/requests/" + requestId + "/accept", null);
    }

    private Long uploadReceipt(String token, String store, LocalDate date) throws Exception {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("storeName", store);
        form.add("purchaseDate", date.toString());
        ByteArrayResource image = new ByteArrayResource("fake-image".getBytes()) {
            @Override
            public String getFilename() {
                return "receipt.png";
            }
        };
        form.add("image", new HttpEntity<>(image, imagePartHeaders()));
        ResponseEntity<String> resp = rest.exchange(base() + "/api/receipts", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(resp.getBody()).get("id").asLong();
    }

    private HttpHeaders imagePartHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.IMAGE_PNG);
        return h;
    }

    private Long addItem(String token, Long receiptId, String name, int qty, String price) throws Exception {
        JsonNode node = post(token, "/api/receipts/" + receiptId + "/items",
                Map.of("name", name, "quantity", qty, "unitPrice", price));
        return node.get("id").asLong();
    }

    private void assign(String token, Long receiptId, Long itemId, String split, List<String> users)
            throws Exception {
        HttpHeaders headers = jsonAuthHeaders(token);
        List<Map<String, String>> targets = users.stream()
                .map(u -> Map.of("username", u))
                .toList();
        String payload = mapper.writeValueAsString(
                Map.of("splitType", split, "targets", targets));
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/receipts/" + receiptId + "/items/" + itemId + "/assignments",
                HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode finalize(String token, Long receiptId) throws Exception {
        ResponseEntity<String> resp = exchange(token, HttpMethod.POST,
                "/api/receipts/" + receiptId + "/finalize", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(resp.getBody());
    }

    private long submitPayment(String token, long debtId, String amount, String method) throws Exception {
        JsonNode node = post(token, "/api/debts/" + debtId + "/payments",
                Map.of("amount", amount, "method", method));
        return node.get("id").asLong();
    }

    private JsonNode get(String token, String path) throws Exception {
        ResponseEntity<String> resp = exchange(token, HttpMethod.GET, path, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(resp.getBody());
    }

    private JsonNode post(String token, String path, Object body) throws Exception {
        HttpHeaders headers = jsonAuthHeaders(token);
        String payload = mapper.writeValueAsString(body);
        ResponseEntity<String> resp = rest.exchange(base() + path, HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s: %s", path, resp.getStatusCode(), resp.getBody())
                .isTrue();
        return mapper.readTree(resp.getBody());
    }

    private ResponseEntity<String> exchange(String token, HttpMethod method, String path, Object body)
            throws Exception {
        HttpHeaders headers = body != null ? jsonAuthHeaders(token) : authHeaders(token);
        String payload = body != null ? mapper.writeValueAsString(body) : null;
        return rest.exchange(base() + path, method, new HttpEntity<>(payload, headers), String.class);
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

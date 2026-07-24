package io.tokenpilot.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "token-pilot.enabled=true",
                "token-pilot.budget.enabled=true",
                "token-pilot.budget.monthly-limit=0.005",
                "management.endpoints.web.exposure.include=prometheus,health"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SampleApplicationBudgetE2ETest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Budget가 활성화되면 BudgetEvaluator와 BudgetStateStore 빈이 등록되어야 한다")
    void shouldRegisterBudgetBeansWhenBudgetIsEnabled() throws Exception {
        HttpResponse<String> beans = get("/test/token-pilot/beans");

        assertThat(beans.statusCode()).isEqualTo(200);
        assertThat(beans.body())
                .contains("\"budgetEvaluator\":true")
                .contains("\"budgetStateStore\":true");
    }

    @Test
    @DisplayName("예산 한도를 초과하면 Budget endpoint가 BLOCK 상태를 반환해야 한다")
    void shouldReturnBlockWhenBudgetLimitIsExceeded() throws Exception {
        HttpResponse<String> budget = get("/test/token-pilot/budget");

        assertThat(budget.statusCode()).isEqualTo(200);
        assertThat(budget.body())
                .contains("\"enabled\":\"true\"")
                .contains("\"initialState\":\"ALLOW\"")
                .contains("\"blockedState\":\"BLOCK\"");
    }

    @Test
    @DisplayName("Budget endpoint의 금액 응답은 표시 경계 반올림을 적용해야 한다")
    void shouldFormatBudgetAmountsWithBoundaryRounding() throws Exception {
        HttpResponse<String> budget = get("/test/token-pilot/budget");

        assertThat(budget.statusCode()).isEqualTo(200);
        assertThat(budget.body())
                .contains("\"currentUsage\":\"0.005500\"")
                .contains("\"limit\":\"0.005000\"");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

package com.notesapp.service.ai;

import com.notesapp.config.AiProperties;
import com.notesapp.service.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AiBudgetGuard {

    private final AiProperties aiProperties;
    private final AtomicReference<YearMonth> month = new AtomicReference<>(YearMonth.now());
    private final AtomicReference<BigDecimal> monthSpend = new AtomicReference<>(BigDecimal.ZERO);

    public AiBudgetGuard(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public synchronized void registerCost(BigDecimal requestCostGbp) {
        YearMonth now = YearMonth.now();
        if (!month.get().equals(now)) {
            month.set(now);
            monthSpend.set(BigDecimal.ZERO);
        }
        BigDecimal nextSpend = monthSpend.get().add(requestCostGbp);
        BigDecimal limit = BigDecimal.valueOf(aiProperties.getMonthlyBudgetGbp());
        if (nextSpend.compareTo(limit) > 0) {
            throw new ValidationException("AI monthly budget exceeded. Limit GBP " + limit.setScale(2, RoundingMode.HALF_UP));
        }
        monthSpend.set(nextSpend);
    }

    public BigDecimal currentMonthSpend() {
        return monthSpend.get();
    }
}

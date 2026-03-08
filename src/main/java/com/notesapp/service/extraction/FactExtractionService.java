package com.notesapp.service.extraction;

import com.notesapp.service.NoteContentHelper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FactExtractionService {

    private static final Pattern HAVE_PATTERN =
        Pattern.compile("(?i)\\b(?:i\\s+)?have\\s+(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+([a-z]+)");

    private static final Pattern ATE_PATTERN =
        Pattern.compile("(?i)\\b(?:i\\s+)?ate\\s+(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+([a-z]+)");

    private static final Pattern FEVER_PATTERN =
        Pattern.compile("(?i)\\bfever(?:\\s+of)?\\s+(\\d+(?:\\.\\d+)?)\\s*([fFcC])?");

    private static final Pattern DOG_POOP_PATTERN =
        Pattern.compile("(?i)\\bdog\\b.*\\bpoop(?:ed)?\\b");

    private final Map<String, Integer> wordNumbers = new HashMap<>();
    private final NoteContentHelper contentHelper;

    public FactExtractionService(NoteContentHelper contentHelper) {
        this.contentHelper = contentHelper;
        wordNumbers.put("one", 1);
        wordNumbers.put("two", 2);
        wordNumbers.put("three", 3);
        wordNumbers.put("four", 4);
        wordNumbers.put("five", 5);
        wordNumbers.put("six", 6);
        wordNumbers.put("seven", 7);
        wordNumbers.put("eight", 8);
        wordNumbers.put("nine", 9);
        wordNumbers.put("ten", 10);
    }

    public List<ExtractedFact> extract(String rawText) {
        List<ExtractedFact> facts = new ArrayList<>();
        String normalized = contentHelper.toPlainText(rawText);
        if (normalized.isEmpty()) {
            return facts;
        }

        extractCountFacts(normalized, HAVE_PATTERN, "_count", facts, 0.92);
        extractCountFacts(normalized, ATE_PATTERN, "_eaten", facts, 0.94);
        extractFeverFacts(normalized, facts);
        extractDogPoop(normalized, facts);
        return facts;
    }

    private void extractCountFacts(String rawText, Pattern pattern, String suffix, List<ExtractedFact> sink, double confidence) {
        Matcher matcher = pattern.matcher(rawText);
        while (matcher.find()) {
            Integer number = parseNumber(matcher.group(1));
            if (number == null) {
                continue;
            }
            String noun = singularize(matcher.group(2));
            if (noun.isBlank()) {
                continue;
            }
            sink.add(ExtractedFact.number(noun + suffix, BigDecimal.valueOf(number), null, confidence));
        }
    }

    private void extractFeverFacts(String rawText, List<ExtractedFact> sink) {
        Matcher matcher = FEVER_PATTERN.matcher(rawText);
        while (matcher.find()) {
            BigDecimal value = new BigDecimal(matcher.group(1));
            String unitToken = matcher.group(2);
            if (unitToken == null || unitToken.isBlank()) {
                sink.add(ExtractedFact.highRiskNumber(
                    "body_temperature",
                    value,
                    null,
                    0.85,
                    "Is this fever value in Fahrenheit or Celsius?"
                ));
            } else {
                String normalizedUnit = unitToken.toUpperCase(Locale.ROOT);
                sink.add(ExtractedFact.number("body_temperature", value, normalizedUnit, 0.95));
            }
        }
    }

    private void extractDogPoop(String rawText, List<ExtractedFact> sink) {
        Matcher matcher = DOG_POOP_PATTERN.matcher(rawText);
        if (matcher.find()) {
            sink.add(ExtractedFact.text("dog_bathroom_event", "pooped", 0.88));
        }
    }

    private Integer parseNumber(String token) {
        String cleaned = token.trim().toLowerCase(Locale.ROOT);
        if (wordNumbers.containsKey(cleaned)) {
            return wordNumbers.get(cleaned);
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String singularize(String noun) {
        String value = noun.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("s") && value.length() > 1) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}

package org.xhy.application.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.xhy.application.run.service.ApprovedActionResultNormalizer;
import org.xhy.infrastructure.exception.BusinessException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts GitHub create-issue MCP output into a validated browser URL. */
@Component
public class GitHubIssueResultNormalizer implements ApprovedActionResultNormalizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESULT_LENGTH = 1_000_000;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\\\"'`]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISSUE_NUMBER_PATTERN = Pattern.compile(
            "(?i)\\b(?:created\\s+)?issue\\s*(?:number\\s*)?#?\\s*([1-9]\\d*)\\b");
    private static final Pattern PULL_REQUEST_TEXT_PATTERN = Pattern.compile(
            "(?i)\\b(?:pull\\s+requests?|pr)\\s*#?\\s*\\d+\\b");
    private static final Set<String> URL_FIELDS = Set.of("url", "html_url", "htmlurl", "issue_url", "issueurl",
            "web_url", "weburl");
    private static final Set<String> NUMBER_FIELDS = Set.of("number", "issue_number", "issuenumber");
    private static final Set<String> TEXT_FIELDS = Set.of("content", "text", "message", "output", "result");

    @Override
    public String normalize(String serverName, String actionName, String targetRepository, String resultPayload) {
        if (!"github".equalsIgnoreCase(serverName) || !"create_issue".equalsIgnoreCase(actionName)) {
            return resultPayload;
        }
        String repository = normalizeRepository(targetRepository);
        if (resultPayload == null || resultPayload.isBlank() || resultPayload.length() > MAX_RESULT_LENGTH) {
            throw invalidResult();
        }

        ResultFields fields = new ResultFields();
        JsonNode root = parseJson(resultPayload);
        if (root == null) {
            addText(fields, resultPayload, 0);
        } else {
            collect(root, null, fields, 0);
        }
        if (fields.error || fields.pullRequest
                || fields.texts.stream().anyMatch(text -> PULL_REQUEST_TEXT_PATTERN.matcher(text).find())) {
            throw invalidResult();
        }

        LinkedHashSet<String> issueNumbers = new LinkedHashSet<>(fields.numbers);
        List<String> rawUrls = new ArrayList<>(fields.urls);
        for (String text : fields.texts) {
            Matcher matcher = URL_PATTERN.matcher(text);
            while (matcher.find()) {
                rawUrls.add(trimTrailingPunctuation(matcher.group()));
            }
            Matcher numberMatcher = ISSUE_NUMBER_PATTERN.matcher(text);
            while (numberMatcher.find()) {
                issueNumbers.add(numberMatcher.group(1));
            }
        }

        LinkedHashSet<String> canonicalUrls = new LinkedHashSet<>();
        for (String rawUrl : rawUrls) {
            String canonical = canonicalIssueUrl(rawUrl, repository);
            if (canonical != null) {
                canonicalUrls.add(canonical);
            }
        }
        if (canonicalUrls.size() > 1) {
            throw invalidResult();
        }
        if (!canonicalUrls.isEmpty()) {
            String canonical = canonicalUrls.iterator().next();
            String urlNumber = canonical.substring(canonical.lastIndexOf('/') + 1);
            if (!issueNumbers.isEmpty() && (issueNumbers.size() != 1 || !issueNumbers.contains(urlNumber))) {
                throw invalidResult();
            }
            return canonical;
        }
        if (issueNumbers.size() != 1) {
            throw invalidResult();
        }
        return "https://github.com/" + repository + "/issues/" + issueNumbers.iterator().next();
    }

    private void collect(JsonNode node, String fieldName, ResultFields fields, int depth) {
        if (depth > 8 || node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = entry.getValue();
                if (URL_FIELDS.contains(key) && value.isTextual()) {
                    fields.urls.add(value.asText());
                }
                if (NUMBER_FIELDS.contains(key)) {
                    addNumber(fields, value);
                }
                if (("iserror".equals(key) || "is_error".equals(key)) && value.isBoolean()
                        && value.booleanValue()) {
                    fields.error = true;
                }
                if (("pull_request".equals(key) || "pullrequest".equals(key)) && !value.isNull()
                        && !(value.isBoolean() && !value.booleanValue())) {
                    fields.pullRequest = true;
                }
                if ("type".equals(key) && value.isTextual()
                        && value.asText().toLowerCase(Locale.ROOT).replace('-', '_').contains("pull_request")) {
                    fields.pullRequest = true;
                }
                collect(value, key, fields, depth + 1);
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> collect(value, fieldName, fields, depth + 1));
            return;
        }
        if (node.isTextual()) {
            if (fieldName == null || TEXT_FIELDS.contains(fieldName)) {
                addText(fields, node.asText(), depth);
            } else {
                JsonNode nested = parseJson(node.asText());
                if (nested != null) {
                    collect(nested, null, fields, depth + 1);
                }
            }
        }
    }

    private void addText(ResultFields fields, String text, int depth) {
        if (text == null || text.isBlank()) {
            return;
        }
        JsonNode nested = depth < 8 ? parseJson(text) : null;
        if (nested != null) {
            collect(nested, null, fields, depth + 1);
        } else {
            fields.texts.add(text);
        }
    }

    private JsonNode parseJson(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }
        try {
            return JSON.readTree(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addNumber(ResultFields fields, JsonNode node) {
        String value = node.isNumber() ? node.asText() : node.isTextual() ? node.asText().trim() : "";
        if (value.matches("[1-9]\\d*")) {
            fields.numbers.add(value);
        }
    }

    private String canonicalIssueUrl(String rawUrl, String repository) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException exception) {
            if (rawUrl.toLowerCase(Locale.ROOT).contains("github.com")) {
                throw invalidResult();
            }
            return null;
        }
        String host = uri.getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).contains("github.com")) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalidResult();
        }

        String[] path = uri.getRawPath().split("/");
        String owner;
        String repo;
        String number;
        if ("github.com".equalsIgnoreCase(host) && path.length == 5 && "issues".equalsIgnoreCase(path[3])) {
            owner = path[1];
            repo = path[2];
            number = path[4];
        } else if ("api.github.com".equalsIgnoreCase(host) && path.length == 6
                && "repos".equalsIgnoreCase(path[1]) && "issues".equalsIgnoreCase(path[4])) {
            owner = path[2];
            repo = path[3];
            number = path[5];
        } else {
            throw invalidResult();
        }
        if (!number.matches("[1-9]\\d*") || !(owner + "/" + repo).equalsIgnoreCase(repository)) {
            throw invalidResult();
        }
        return "https://github.com/" + repository + "/issues/" + number;
    }

    private String normalizeRepository(String repository) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}")) {
            throw invalidResult();
        }
        return repository.toLowerCase(Locale.ROOT);
    }

    private String trimTrailingPunctuation(String value) {
        return value.replaceFirst("[.,;:!?\\])}]+$", "");
    }

    private BusinessException invalidResult() {
        return new BusinessException("GitHub MCP 未返回与已批准仓库匹配的 Issue URL");
    }

    private static final class ResultFields {
        private final Set<String> urls = new LinkedHashSet<>();
        private final Set<String> numbers = new LinkedHashSet<>();
        private final List<String> texts = new ArrayList<>();
        private boolean error;
        private boolean pullRequest;
    }
}

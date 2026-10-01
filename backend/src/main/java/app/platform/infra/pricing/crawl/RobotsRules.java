package app.platform.infra.pricing.crawl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * robots.txt rules for this bot (RFC 9309): the group naming our product token, or the "*" group; the most specific
 * (longest) matching rule wins and Allow wins a tie; "*" and "$" wildcards; Crawl-delay honored.
 */
public final class RobotsRules {

    private record Rule(boolean allow, String path, Pattern pattern) {
    }

    private final List<Rule> rules;
    private final Duration crawlDelay;

    private RobotsRules(List<Rule> rules, Duration crawlDelay) {
        this.rules = rules;
        this.crawlDelay = crawlDelay;
    }

    /** Everything allowed: a missing robots.txt (404) means no restrictions. */
    public static RobotsRules allowAll() {
        return new RobotsRules(List.of(), null);
    }

    /** Nothing allowed: used when robots.txt cannot be read for another reason (5xx, 403), to stay on the safe side. */
    public static RobotsRules denyAll() {
        return new RobotsRules(List.of(new Rule(false, "/", compile("/"))), null);
    }

    public static RobotsRules parse(String content, String productToken) {
        String token = productToken.toLowerCase(Locale.ROOT);
        List<Rule> specific = new ArrayList<>();
        List<Rule> generic = new ArrayList<>();
        Duration specificDelay = null;
        Duration genericDelay = null;
        boolean inSpecific = false;
        boolean inGeneric = false;
        boolean lastWasAgent = false;
        boolean sawSpecific = false;

        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.replaceFirst("#.*", "").strip();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (field.equals("user-agent")) {
                if (!lastWasAgent) {
                    inSpecific = false;
                    inGeneric = false;
                }
                String agent = value.toLowerCase(Locale.ROOT);
                if (!agent.equals("*") && token.contains(agent)) {
                    inSpecific = true;
                    sawSpecific = true;
                } else if (agent.equals("*")) {
                    inGeneric = true;
                }
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            switch (field) {
                case "allow", "disallow" -> {
                    if (value.isEmpty()) {
                        continue; // "Disallow:" with no path allows everything
                    }
                    Rule rule = new Rule(field.equals("allow"), value, compile(value));
                    if (inSpecific) {
                        specific.add(rule);
                    }
                    if (inGeneric) {
                        generic.add(rule);
                    }
                }
                case "crawl-delay" -> {
                    Duration delay = parseDelay(value);
                    if (inSpecific) {
                        specificDelay = delay;
                    }
                    if (inGeneric) {
                        genericDelay = delay;
                    }
                }
                default -> {
                    // sitemap and unknown fields are handled elsewhere or ignored
                }
            }
        }
        return sawSpecific ? new RobotsRules(specific, specificDelay) : new RobotsRules(generic, genericDelay);
    }

    /** @param pathAndQuery e.g. "/produto/123/nome?x=1" */
    public boolean allows(String pathAndQuery) {
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.pattern().matcher(pathAndQuery).lookingAt()) {
                if (best == null || rule.path().length() > best.path().length()
                        || (rule.path().length() == best.path().length() && rule.allow())) {
                    best = rule;
                }
            }
        }
        return best == null || best.allow();
    }

    public Optional<Duration> crawlDelay() {
        return Optional.ofNullable(crawlDelay);
    }

    /** "*" matches any sequence, a trailing "$" anchors the end; everything else is literal. Matched from the start. */
    private static Pattern compile(String path) {
        boolean anchored = path.endsWith("$");
        String[] parts = (anchored ? path.substring(0, path.length() - 1) : path).split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex + (anchored ? "$" : ""));
    }

    private static Duration parseDelay(String value) {
        try {
            double seconds = Double.parseDouble(value);
            return seconds > 0 ? Duration.ofMillis((long) (seconds * 1000)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

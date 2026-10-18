package org.xhy.application.run.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative lexical support checks for retrieval topics and generated issue claims. */
final class KnowledgeEvidencePolicy {
    private static final ObjectMapper JSON = new ObjectMapper();
    // Do not absorb adjacent Han characters into Latin words (for example "requests但...").
    private static final Pattern TERM_PATTERN = Pattern.compile("\\p{IsHan}+|(?:(?!\\p{IsHan})[\\p{L}\\p{N}_-])+");
    private static final Pattern TOPIC_SEPARATOR = Pattern
            .compile("(?iu)[,;，；、。\\n]+|\\b(?:and|or|also|then|plus|with)\\b|以及|并且|同时|而且|和|与|及");
    private static final Pattern CLAIM_BOUNDARY = Pattern
            .compile("(?iu)[.!?,，。！？;；\\r\\n]+|\\b(?:and|or|but|however|although|yet|whereas|while|whilst)\\b|"
                    + "以及|并且|同时|而且|然而|但是|不过|可是|相反|虽然|尽管|和|与|及|(?<!不)但|而|却");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.!?！？;；\\r\\n]+\\s*");
    private static final String RETRY_PARTIAL_OBJECT_SCOPE = "(?:one\\s+of\\s+(?:(?:[\\p{L}-]+\\s+){0,4})(?:requests?|jobs?|actions?|operations?|items?|"
            + "records?|messages?|documents?|cases?|steps?|attempts?|calls?|tasks?|files?|transactions?|"
            + "events?|users?|tenants?|customers?|accounts?|clients?|organizations?|teams?|systems?|"
            + "servers?|machines?|endpoints?|devices?|targets?|instances?)|"
            + "one\\s+(?:(?:[\\p{L}-]+\\s+){0,3})(?:requests?|jobs?|actions?|operations?|items?|"
            + "records?|messages?|documents?|cases?|steps?|attempts?|calls?|tasks?|files?|transactions?|"
            + "events?|users?|tenants?|customers?|accounts?|clients?|organizations?|teams?|systems?|"
            + "servers?|machines?|endpoints?|devices?|targets?|instances?)|"
            + "single\\s+(?:(?:[\\p{L}-]+\\s+){0,3})(?:requests?|jobs?|actions?|operations?|items?|"
            + "records?|messages?|documents?|cases?|steps?|attempts?|calls?|tasks?|files?|transactions?|"
            + "events?|users?|tenants?|customers?|accounts?|clients?|organizations?|teams?|systems?|"
            + "servers?|machines?|endpoints?|devices?|targets?|instances?)|"
            + "(?:subset|fraction|portion|part)\\s+of\\s+(?:(?:[\\p{L}-]+\\s+){0,4})"
            + "(?:requests?|jobs?|actions?|operations?|items?|records?|messages?|documents?|cases?|steps?|"
            + "attempts?|calls?|tasks?|files?|transactions?|events?|users?|tenants?|customers?|accounts?|"
            + "clients?|organizations?|teams?|systems?|servers?|machines?|endpoints?|devices?|targets?|instances?))";
    private static final Pattern LEADING_RETRY_SCOPE_CLAUSE = Pattern
            .compile("(?iu)^\\s*(?:unless|if|when|whenever|until|while|before|after|during|since|once|"
                    + "provided(?:\\s+that)?|assuming|depending\\s+on|only\\s+(?:if|when|in|under|for)|"
                    + "(?:on|within|outside)\\s+|"
                    + "without\\s+(?!(?:fail|exception|doubt|question|reservations?|a\\s+doubt|any\\s+doubt)\\b)|"
                    + "with\\s+(?!(?:(?:(?:absolute|complete|total|full|utter|unshakable)\\s+)*"
                    + "(?:certainty|confidence|assurance)|no\\s+(?:doubt|question|exception|reservations?))\\b)|"
                    + "for\\s+(?:some|certain|specific|selected|particular)|"
                    + "for\\s+(?:[\\p{L}-]+\\s+){1,3}(?:customers?|users?|tenants?|accounts?|clients?|"
                    + "organizations?|teams?)|" + "in\\s+(?:some|certain|specific|selected|particular)|"
                    + "in\\s+(?:production|staging|development|testing|the|this|that|these|those|a|an)|"
                    + "with\\s+(?:some|certain|specific|selected|particular)|"
                    + "under\\s+(?:(?:[\\p{L}-]+\\s+){0,2})(?:load|pressure|stress|the|these|those))\\b");
    private static final Pattern RETRY_INFLECTED_ACTION_TERM = Pattern.compile("(?iu)\\bretr(?:ies|ying|ied)\\b");
    private static final Pattern RETRY_PREFIXED_BASE_ACTION_TERM = Pattern
            .compile("(?iu)\\b(?:am|is|are|was|were|be|been|being|do|does|did|has|have|had|can|could|may|might|must|"
                    + "shall|should|will|would|cannot|can't|won't|wouldn't|shouldn't|couldn't|doesn't|don't|didn't|"
                    + "isn't|aren't|wasn't|weren't|hasn't|haven't|hadn't|to|not|never)\\s+(?<action>retry)\\b");
    private static final Pattern RETRY_MODAL_ACTION_TERM = Pattern
            .compile("(?iu)(?:\\b(?<modal>can|could|may|might|must|shall|should|will|would|cannot|can't|won't|"
                    + "wouldn't|shouldn't|couldn't)\\s+|\\b(?<modalAbility>can|could|may|might|must|shall|"
                    + "should|will|would|cannot|can't|won't|wouldn't|shouldn't|couldn't)\\s+be\\s+able\\s+to\\s+|"
                    + "\\b(?:(?:am|is|are|was|were|be|been|being)\\s+|"
                    + "(?:has|have|had)\\s+been\\s+)?(?<ability>able)\\s+to\\s+)(?<action>retry)\\b");
    private static final Pattern RETRY_MODAL_QUALIFIED_ACTION_TERM = Pattern
            .compile("(?iu)\\b(?<modal>can|could|may|might|must|shall|should|will|would|cannot|can't|won't|"
                    + "wouldn't|shouldn't|couldn't|mustn't|mightn't)\\s+"
                    + "(?:(?:(?:[\\p{L}]+ly)|still|soon|again|always|often|usually|sometimes|never|not|"
                    + "be|been|being|have|has|had|to|able|unable|capable|incapable|only|also|ever|just|"
                    + "already|fail|continue|continues|continued|continuing|keep|keeps|kept|keeping|on)\\s+"
                    + "){1,5}(?<action>retr(?:y|ies|ying|ied))\\b");
    private static final Pattern RETRY_NON_ABSOLUTE_QUALIFIER = Pattern
            .compile("(?iu)\\b(?:(?!(?:actually|ever)\\b)[\\p{L}]+ly|still|soon|again|always|often|usually|"
                    + "sometimes|never|not|only|also|just|already|continue|continues|continued|continuing|"
                    + "keep|keeps|kept|keeping|on|in|under|all|every|each|both|entire|if|unless|when|whenever|"
                    + "until|before|after|during|once|while|since|where|provided|providing|assuming|depending|"
                    + "except|on|with(?!\\s+(?:(?:(?:absolute|complete|total|full|utter|unshakable)\\s+)*(?:certainty|confidence|assurance)|"
                    + "no\\s+(?:doubt|question|exception|reservations?))\\b)|"
                    + "within|outside|without(?!\\s+(?:(?:fail|exception|doubt|question|reservations?)|a\\s+doubt|any\\s+doubt)\\b)|"
                    + "some|certain|specific|selected|particular|several|many|few|multiple|"
                    + RETRY_PARTIAL_OBJECT_SCOPE + "|" + "for\\s+(?:(?:some|certain|specific|selected|particular)|"
                    + "(?:[\\p{L}-]+\\s+){1,3}(?:customers?|users?|tenants?|accounts?|clients?|organizations?|teams?)))\\b");
    private static final Pattern RETRY_PRE_MODAL_SCOPE_QUALIFIER = Pattern
            .compile("(?iu)\\b(?:still|soon|again|always|often|usually|sometimes|never|not|only|also|just|already|"
                    + "reliably|successfully|occasionally|generally|typically|normally|rarely|"
                    + "continue|continues|continued|continuing|keep|keeps|kept|keeping|all|every|each|both|entire|"
                    + "if|unless|when|whenever|until|before|after|during|once|while|since|where|provided|"
                    + "providing|assuming|depending|except|some|certain|specific|selected|particular|several|many|few|multiple|"
                    + RETRY_PARTIAL_OBJECT_SCOPE + "|"
                    + "in|on|with(?!\\s+(?:(?:(?:absolute|complete|total|full|utter|unshakable)\\s+)*(?:certainty|confidence|assurance)|"
                    + "no\\s+(?:doubt|question|exception|reservations?))\\b)|"
                    + "within|outside|without(?!\\s+(?:(?:fail|exception|doubt|question|reservations?)|a\\s+doubt|any\\s+doubt)\\b)|under|"
                    + "for\\s+(?:(?:some|certain|specific|selected|particular)|"
                    + "(?:[\\p{L}-]+\\s+){1,3}(?:customers?|users?|tenants?|accounts?|clients?|organizations?|teams?)))\\b");
    private static final Pattern RETRY_ACTION_SCOPE_QUALIFIER = Pattern.compile(
            "(?iu)\\b(?:conditionally|if|unless|when|whenever|until|before|after|during|once|while|since|where|"
                    + "provided|providing|assuming|depending|except|only|in|on|"
                    + "with(?!\\s+(?:(?:(?:absolute|complete|total|full|utter|unshakable)\\s+)*"
                    + "(?:certainty|confidence|assurance)|no\\s+(?:doubt|question|exception|reservations?))\\b)|"
                    + "within|outside|without(?!\\s+(?:(?:fail|exception|doubt|question|reservations?)|a\\s+doubt|any\\s+doubt)\\b)|"
                    + "some|certain|specific|selected|particular|several|many|few|multiple|under|"
                    + RETRY_PARTIAL_OBJECT_SCOPE + "|"
                    + "still|soon|again|always|often|usually|sometimes|reliably|successfully|occasionally|"
                    + "generally|typically|normally|rarely|"
                    + "for\\s+(?:(?:some|certain|specific|selected|particular)|"
                    + "(?:[\\p{L}-]+\\s+){1,3}(?:customers?|users?|tenants?|accounts?|clients?|organizations?|teams?)))\\b");
    private static final Pattern RETRY_ASSERTION_SCOPE_QUALIFIER = Pattern.compile(
            "(?iu)\\b(?:conditionally|if|unless|when|whenever|until|before|after|during|once|while|since|where|"
                    + "provided|providing|assuming|depending|except|in|on|"
                    + "with(?!\\s+(?:(?:(?:absolute|complete|total|full|utter|unshakable)\\s+)*"
                    + "(?:certainty|confidence|assurance)|no\\s+(?:doubt|question|exception|reservations?))\\b)|"
                    + "within|outside|without(?!\\s+(?:(?:fail|exception|doubt|question|reservations?)|a\\s+doubt|any\\s+doubt)\\b)|"
                    + "under|some|certain|specific|selected|particular|several|many|few|multiple|"
                    + RETRY_PARTIAL_OBJECT_SCOPE + "|"
                    + "for\\s+(?:(?:some|certain|specific|selected|particular|several|many|few|multiple)|"
                    + "(?:[\\p{L}-]+\\s+){1,3}(?:customers?|users?|tenants?|accounts?|clients?|organizations?|teams?)))\\b");
    private static final Pattern RETRY_CAPABILITY_ACTION_TERM = Pattern
            .compile("(?iu)\\b(?:(?<modal>can|could|may|might|must|shall|should|will|would|cannot|can't|won't|"
                    + "wouldn't|shouldn't|couldn't)\\s+(?<modalNegation>(?:not|never)\\s+)?"
                    + "(?:be|have\\s+been)\\s+)?"
                    + "(?:(?:(?:am|is|are|was|were|be|been|being)\\s+|(?:has|have|had)\\s+been\\s+)?"
                    + "(?<inability>in)?capable)\\s+of\\s+(?<action>retr(?:y|ies|ying|ied))\\b");
    private static final Pattern RETRY_MODAL_CAPABILITY_ACTION_TERM = Pattern
            .compile("(?iu)\\b(?<modal>can|could|may|might|must|shall|should|will|would|cannot|can't|won't|"
                    + "wouldn't|shouldn't|couldn't)\\s+(?<modalNegation>(?:not|never)\\s+)?"
                    + "(?:be|have\\s+been)\\s+(?<inability>in)?capable\\s+of\\s+"
                    + "(?<action>retr(?:y|ies|ying|ied))\\b");
    private static final Pattern RETRY_INFINITIVE_ACTION_PATTERN = Pattern.compile("(?iu)\\bto\\s+(?<action>retry)\\b");
    private static final Pattern RETRY_INTENT_INFLECTED_ACTION_TERM = Pattern
            .compile("(?iu)\\b(?:plan(?:s|ned|ning)?|intend(?:s|ed|ing)?|expect(?:s|ed|ing)?|"
                    + "hope(?:s|d|ing)?|aim(?:s|ed|ing)?)\\b"
                    + "(?:\\s+[\\p{L}\\p{N}'’-]+){0,5}\\s+(?<action>retrying)\\b");
    private static final Pattern RETRY_BASE_ACTION_TERM = Pattern.compile("(?iu)\\bretry\\b");
    private static final String RETRY_ACTION_TERM = "__retry_action_verb__";
    private static final String RETRY_SCOPED_ACTION_TERM = "__retry_scoped_action_verb__";
    private static final String RETRY_MODAL_ACTION_TERM_PREFIX = "__retry_modal_";
    private static final String RETRY_INFINITIVE_ACTION_TERM = "__retry_infinitive_action__";
    private static final String RETRY_INTENT_ACTION_TERM = "__retry_intent_action__";
    private static final Pattern RETRY_NOUN_COMPLEMENT = Pattern
            .compile("(?iu)^(?:\\s+(?:handler|handlers|controller|controllers|policy|policies|limit|limits|cap|caps|"
                    + "budget|budgets|configuration|configurations|setting|settings|threshold|thresholds|"
                    + "maximum|minimum|count|counts|number|numbers|delay|delays|latency|latencies|timeout|"
                    + "timeouts|backoffs?|back-offs?|attempt|attempts|am|is|are|was|were|has|have|had|"
                    + "can|could|may|might|must|shall|should|will|would|cannot|can't|won't|wouldn't|"
                    + "shouldn't|couldn't)\\b|"
                    + "\\s*[:：]\\s*(?:\\d+|no|none|zero|one|two|three|four|five|six|seven|eight|nine|ten|many|"
                    + "several|few|multiple|numerous|unlimited|unbounded|infinite|endless|limitless)\\b)");
    private static final Pattern RETRY_NOUN_PREFIX = Pattern
            .compile("(?iu)\\b(?:\\d+|zero|one|two|three|four|five|six|seven|eight|nine|ten|many|several|few|multiple|"
                    + "numerous|unlimited|unbounded|infinite|endless|limitless|uncapped|unrestricted|maximum|minimum|"
                    + "total|consecutive|repeated|successful|failed|remaining|available|additional|extra|some|any|no|"
                    + "a|an|the|these|those|their|our|its|allow(?:s|ed|ing)?|"
                    + "support(?:s|ed|ing)?|offer(?:s|ed|ing)?|include(?:s|d|ing)?|have|has|had)\\s+$");
    private static final Pattern NOT_ONLY = Pattern.compile("(?iu)\\bnot\\s+only\\b");
    private static final Pattern CHINESE_NON_NEGATING_IDIOMS = Pattern.compile("不得不|不但|不仅|不只(?:是)?|不单|不光|不止");
    private static final Pattern ENGLISH_NEGATION = Pattern.compile(
            "(?iu)\\b(?:not|never|cannot|can't|won't|wouldn't|shouldn't|doesn't|don't|didn't|isn't|aren't|wasn't|weren't|hasn't|haven't|hadn't|neither|nor)\\b|\\b(?:fail|fails|failed|failing)\\s+to\\b");
    // Scope "no" to known predicate forms, leaving complements such as "with no monitoring" alone.
    private static final Pattern NO_NEGATION = Pattern
            .compile("(?iu)^\\s*no\\s+|\\bthere\\s+(?:is|are|was|were|has|have)\\s+no\\s+|"
                    + "\\b(?:has|have|had|provide|provides|provided|providing)\\s+no\\s+");
    private static final Pattern NO_LONGER_NEGATION = Pattern.compile("(?iu)\\bno\\s+longer\\b");
    private static final String RETRY_CONFIGURATION_NOUN_GUARD = "(?!\\s+(?:limit|limits|cap|caps|policy|policies|budget|budgets|configuration|configurations|"
            + "setting|settings|threshold|thresholds|maximum|minimum|count|counts|number|numbers|"
            + "delay|delays|latency|latencies|timeout|timeouts|backoffs?|back-offs?)\\b)";
    private static final Pattern NO_RETRY_SUPPORT_NEGATION = Pattern.compile(
            "(?iu)\\b(?:support(?:s|ed|ing)?|offer(?:s|ed|ing)?)\\s+no\\s+(?:(?:a|any)\\s+)?retr(?:y|ies|ying|ied)\\b"
                    + RETRY_CONFIGURATION_NOUN_GUARD);
    private static final Pattern POSITIVE_NO_RETRY_LIMIT = Pattern
            .compile("(?iu)\\b(?:support(?:s|ed|ing)?|offer(?:s|ed|ing)?|provide(?:s|d|ing)?)\\s+no\\s+"
                    + "(?:(?:a|any)\\s+)?(?<action>retry)\\s+(?:limit|limits|cap|caps)\\b");
    private static final Pattern WITH_NO_RETRY_NEGATION = Pattern.compile(
            "(?iu)\\bwith\\s+no\\s+(?:(?:a|any)\\s+)?retr(?:y|ies|ying|ied)\\b" + RETRY_CONFIGURATION_NOUN_GUARD);
    private static final Pattern WITHOUT_RETRY_NEGATION = Pattern
            .compile("(?iu)\\bwithout\\s+(?:(?:a|any)\\s+)?retr(?:y|ies|ying|ied)\\b" + RETRY_CONFIGURATION_NOUN_GUARD);
    private static final Pattern AVOID_RETRY_NEGATION = Pattern
            .compile("(?iu)\\bavoid(?:s|ed|ing)?\\s+(?:(?:a|any)\\s+)?retr(?:y|ies|ying|ied)\\b");
    private static final Pattern LACK_RETRY_NEGATION = Pattern
            .compile("(?iu)\\black(?:s|ed|ing)?\\s+(?:(?:(?:the|a|an)\\s+)?(?:ability|capacity)\\s+to\\s+"
                    + "|(?:(?:a|any)\\s+)?)retr(?:y|ies|ying|ied)(?:\\s+support)?\\b");
    private static final String RETRY_ACTION_INTERMEDIARY_VERB = "(?:keep(?:s|ing)?|kept)(?:\\s+on)?|continue(?:s|d|ing)?|perform(?:s|ed|ing)?|"
            + "attempt(?:s|ed|ing)?|initiat(?:e|es|ed|ing)|conduct(?:s|ed|ing)?|"
            + "do(?:es|ne|ing)?|did|make(?:s|ing)?|made";
    private static final String RETRY_ACTION_INTERMEDIARY = "(?:(?:" + RETRY_ACTION_INTERMEDIARY_VERB
            + ")\\s+(?:to\\s+)?|" + "(?:carry|carries|carried|carrying)\\s+out\\s+(?:to\\s+)?){0,3}"
            + "(?:(?:a|an|the)\\s+)?";
    private static final String RETRY_ADVERB_PREFIX = "(?:(?:successfully|reliably|consistently|repeatedly|properly|quickly|slowly|safely|"
            + "automatically|manually|promptly|continuously|immediately|well|fast|again|soon)\\s+){0,2}";
    private static final String RETRY_PREVENTION_ADVERB_PREFIX = "(?:(?:ever)\\s+)?" + RETRY_ADVERB_PREFIX;
    private static final String RETRY_PREVENTION_FROM_PREFIX = "(?:prevent(?:s|ed|ing)?\\s+(?:[\\p{L}\\p{N}'’-]+\\s+){1,5}from\\s+"
            + "|(?:(?:(?:am|is|are|was|were|be|been|being)(?:\\s+being)?|(?:has|have|had)\\s+been)"
            + "\\s+)?prevented\\s+from\\s+)";
    private static final String RETRY_PREVENTION_ACTION_INTERMEDIARY = "(?:(?:(?:am|is|are|was|were|be|been|being)\\s+)?able\\s+to\\s+)?"
            + RETRY_ACTION_INTERMEDIARY;
    private static final String PREVENTION_OF_NEGATED_RETRY_COMPLEMENT = "(?:(?:not|never)\\s+"
            + RETRY_PREVENTION_ADVERB_PREFIX + "retr(?:y|ies|ying|ied)" + "|fail(?:s|ed|ing)?\\s+to\\s+"
            + RETRY_ACTION_INTERMEDIARY + RETRY_PREVENTION_ADVERB_PREFIX + "retr(?:y|ies|ying|ied))";
    private static final String RETRY_INABILITY_PHRASE = "(?:unable\\s+to|incapable\\s+of)\\s+"
            + RETRY_ACTION_INTERMEDIARY + RETRY_ADVERB_PREFIX + "retr(?:y|ies|ying|ied)";
    private static final String RETRY_REFUSAL_PHRASE = "(?:refus(?:e|es|ed|ing)|declin(?:e|es|ed|ing))\\s+to\\s+"
            + RETRY_ACTION_INTERMEDIARY + RETRY_ADVERB_PREFIX + "retr(?:y|ies|ying|ied)";
    private static final String RETRY_PREVENTION_FROM_PHRASE = RETRY_PREVENTION_FROM_PREFIX
            + RETRY_PREVENTION_ACTION_INTERMEDIARY + RETRY_PREVENTION_ADVERB_PREFIX + "retr(?:y|ies|ying|ied)";
    private static final String RETRY_PREVENTION_PHRASE = "(?:prevent(?:s|ed|ing)?\\s+" + RETRY_ACTION_INTERMEDIARY
            + RETRY_PREVENTION_ADVERB_PREFIX + "retr(?:y|ies|ying|ied)|" + RETRY_PREVENTION_FROM_PHRASE + ")";
    private static final Pattern POSITIVE_DOUBLE_NEGATED_RETRY_ACTION = Pattern
            .compile("(?iu)\\b(?:(?:not|never)\\s+(?:be\\s+)?|(?:doesn't|don't|didn't|isn't|aren't|wasn't|weren't|"
                    + "hasn't|haven't|hadn't|cannot|can't|won't|wouldn't|shouldn't|couldn't)\\s+)(?:"
                    + "lack(?:s|ed|ing)?\\s+(?:(?:(?:the|a|an)\\s+)?(?:ability|capacity)\\s+to\\s+"
                    + "|(?:(?:a|any)\\s+)?)retr(?:y|ies|ying|ied)(?:\\s+support)?"
                    + "|avoid(?:s|ed|ing)?\\s+(?:(?:a|any)\\s+)?retr(?:y|ies|ying|ied)" + "|" + RETRY_REFUSAL_PHRASE
                    + "|" + RETRY_PREVENTION_PHRASE
                    + "|stop(?:s|ped|ping)?\\s+(?:(?:(?:an?|the)\\s+)?attempt(?:s|ed|ing)?\\s+to\\s+)?"
                    + "retr(?:y|ies|ying|ied)"
                    + "|refrain(?:s|ed|ing)?\\s+from\\s+(?:initiating\\s+)?retr(?:y|ies|ying|ied)"
                    + "|fail(?:s|ed|ing)?\\s+to\\s+" + RETRY_ACTION_INTERMEDIARY + RETRY_ADVERB_PREFIX
                    + "retr(?:y|ies|ying|ied)" + "|" + RETRY_INABILITY_PHRASE + ")\\b");
    private static final Pattern RETRY_REFUSAL_NEGATION = Pattern.compile("(?iu)\\b(?:" + RETRY_REFUSAL_PHRASE
            + "|stop(?:s|ped|ping)?\\s+(?:(?:(?:an?|the)\\s+)?attempt(?:s|ed|ing)?\\s+to\\s+)?"
            + "retr(?:y|ies|ying|ied)"
            + "|refrain(?:s|ed|ing)?\\s+from\\s+(?:initiating\\s+)?retr(?:y|ies|ying|ied))\\b");
    private static final Pattern RETRY_PREVENTION_NEGATION = Pattern
            .compile("(?iu)\\b" + RETRY_PREVENTION_PHRASE + "\\b");
    private static final Pattern POSITIVE_PREVENTION_OF_NEGATED_RETRY_ACTION = Pattern
            .compile("(?iu)\\b" + RETRY_PREVENTION_FROM_PREFIX + RETRY_ACTION_INTERMEDIARY
                    + PREVENTION_OF_NEGATED_RETRY_COMPLEMENT + "\\b");
    private static final Pattern ACTIVE_PREVENTION_NEGATED_RETRY_TARGET = Pattern
            .compile("(?iu)\\bprevent(?:s|ed|ing)?\\s+(?<target>(?:(?:the|a|an)\\s+)?[\\p{L}\\p{N}'’-]+"
                    + "(?:\\s+[\\p{L}\\p{N}'’-]+){0,4})\\s+from\\s+" + PREVENTION_OF_NEGATED_RETRY_COMPLEMENT + "\\b");
    private static final Pattern PASSIVE_PREVENTION_NEGATED_RETRY_TARGET = Pattern.compile(
            "(?iu)\\b(?<target>(?:(?:the|a|an)\\s+)?[\\p{L}\\p{N}'’-]+" + "(?:\\s+[\\p{L}\\p{N}'’-]+){0,2})\\s+"
                    + "(?:(?:am|is|are|was|were|be|been|being)(?:\\s+being)?|(?:has|have|had)\\s+been)"
                    + "\\s+prevented(?:\\s+by\\s+[\\p{L}\\p{N}'’-]+" + "(?:\\s+[\\p{L}\\p{N}'’-]+){0,4})?\\s+from\\s+"
                    + RETRY_ACTION_INTERMEDIARY + PREVENTION_OF_NEGATED_RETRY_COMPLEMENT + "\\b");
    private static final Pattern UNABLE_TO_RETRY_NEGATION = Pattern
            .compile("(?iu)\\b" + RETRY_INABILITY_PHRASE + "\\b");
    private static final Pattern CHINESE_NEGATION = Pattern.compile("从未|不能|不可|不再|不会|没有|未能|未|没|不|无");
    private static final Pattern NEGATED_STATUS_ADJECTIVE = Pattern
            .compile("(?iu)\\b(?:unavailable|unready|disabled|unsupported|inactive|incompatible|unsafe)\\b");
    private static final Set<String> TITLE_ARTICLES = Set.of("a", "an", "the", "this", "that", "these", "those");
    private static final Set<String> TITLE_PREDICATE_ADJECTIVES = Set.of("available", "unavailable", "ready", "unready",
            "enabled", "disabled", "supported", "unsupported", "active", "inactive", "compatible", "incompatible",
            "safe", "unsafe");
    private static final Set<String> TITLE_FINITE_AUXILIARIES = Set.of("am", "are", "be", "been", "being", "can",
            "could", "did", "do", "does", "had", "has", "have", "is", "may", "might", "must", "shall", "should", "was",
            "were", "will", "would");
    // Preserve otherwise-common relation words in claim order so complements such as
    // "with/without authentication" cannot be substituted.
    private static final Set<String> CLAIM_RELATION_TERMS = Set.of("with", "without");
    private static final Set<String> COMMON_TERMS = Set.of("a", "about", "action", "add", "after", "all", "also", "an",
            "and", "any", "are", "as", "at", "be", "because", "been", "before", "being", "body", "both", "but", "by",
            "can", "change", "could", "create", "description", "details", "did", "do", "does", "document", "draft",
            "feature", "fix", "for", "from", "get", "github", "has", "have", "help", "how", "if", "implement", "in",
            "into", "is", "issue", "issues", "it", "make", "me", "more", "must", "need", "new", "not", "of", "on", "or",
            "our", "please", "project", "pull", "repo", "repository", "request", "review", "should", "since", "task",
            "text", "than", "that", "the", "then", "this", "those", "through", "to", "update", "use", "want", "was",
            "we", "were", "when", "where", "which", "who", "why", "will", "with", "would", "write", "you", "your", "s1",
            "s2", "s3", "s4", "s5");
    private static final List<String> COMMON_CHINESE_PHRASES = List.of("请帮我", "请问", "帮我", "帮忙", "一个", "一下", "这个", "那些",
            "创建", "新建", "建立", "添加", "新增", "修复", "更新", "打开", "提交", "工单", "问题", "任务", "项目", "仓库", "功能", "需求", "标题", "正文",
            "描述", "内容", "详情", "如何", "怎样", "什么", "哪里", "为什么", "是否", "可以", "需要", "希望", "相关", "请", "为", "在");
    private static final double MIN_QUERY_COVERAGE = 0.8;

    private KnowledgeEvidencePolicy() {
    }

    static Set<String> distinctiveTerms(String value) {
        return Set.copyOf(new LinkedHashSet<>(distinctiveTermSequence(value)));
    }

    private static List<String> distinctiveTermSequence(String value) {
        return distinctiveTermSequence(value, false);
    }

    private static List<String> distinctiveTermSequence(String value, boolean preserveClaimRelations) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String phrase : COMMON_CHINESE_PHRASES) {
            normalized = normalized.replace(phrase, " ");
        }
        Matcher matcher = TERM_PATTERN.matcher(normalized);
        List<String> terms = new ArrayList<>();
        while (matcher.find()) {
            String term = matcher.group();
            if (term.codePoints()
                    .allMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
                if (term.length() >= 2) {
                    for (int index = 0; index < term.length() - 1; index++) {
                        String bigram = term.substring(index, index + 2);
                        if (!COMMON_CHINESE_PHRASES.contains(bigram)) {
                            terms.add(bigram);
                        }
                    }
                }
            } else if (term.length() >= 3 && (!COMMON_TERMS.contains(term)
                    || preserveClaimRelations && CLAIM_RELATION_TERMS.contains(term))) {
                terms.add(term);
            }
        }
        return List.copyOf(terms);
    }

    static List<Set<String>> topics(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Set<String>> topics = Arrays.stream(TOPIC_SEPARATOR.split(query))
                .map(KnowledgeEvidencePolicy::distinctiveTerms).filter(topic -> !topic.isEmpty()).toList();
        return topics.isEmpty() ? List.of(distinctiveTerms(query)) : topics;
    }

    static boolean supportsQuery(String query, List<KnowledgeCitation> citations) {
        Set<String> queryTerms = distinctiveTerms(query);
        if (queryTerms.size() < 2 || citations == null || citations.isEmpty()) {
            return false;
        }
        Set<String> supportedTerms = new LinkedHashSet<>();
        List<Set<String>> sourceTerms = new ArrayList<>();
        for (KnowledgeCitation citation : citations) {
            Set<String> terms = distinctiveTerms(citation.getContent());
            sourceTerms.add(terms);
            supportedTerms.addAll(terms);
        }
        if (intersectionSize(queryTerms, supportedTerms) < minimumCoverage(queryTerms.size())) {
            return false;
        }
        for (Set<String> topic : topics(query)) {
            boolean citedTopic = sourceTerms.stream()
                    .anyMatch(terms -> intersectionSize(topic, terms) >= minimumCoverage(topic.size()));
            if (!citedTopic) {
                return false;
            }
        }
        return true;
    }

    static boolean supportsDraftClaims(String arguments, List<KnowledgeCitation> citations) {
        if (arguments == null || citations == null || citations.isEmpty()) {
            return false;
        }
        try {
            JsonNode root = JSON.readTree(arguments);
            if (root == null || !root.isObject()) {
                return false;
            }
            String title = root.path("title").isTextual() ? root.path("title").asText() : "";
            String body = root.path("body").isTextual() ? root.path("body").asText() : "";
            Set<String> claims = new LinkedHashSet<>();
            clauses(title).stream().filter(KnowledgeEvidencePolicy::isFactualTitleClause)
                    .map(KnowledgeEvidencePolicy::claimTerms).forEach(claims::addAll);
            claims.addAll(claimTerms(body));
            if (claims.isEmpty()) {
                return false;
            }
            Set<String> supportedTerms = new LinkedHashSet<>();
            List<String> evidenceClauses = new ArrayList<>();
            for (KnowledgeCitation citation : citations) {
                supportedTerms.addAll(claimTerms(citation.getContent()));
                evidenceClauses.addAll(clauses(citation.getContent()));
            }
            if (!supportedTerms.containsAll(claims)) {
                return false;
            }
            List<String> polarityClaims = new ArrayList<>(clauses(body));
            clauses(title).stream().filter(KnowledgeEvidencePolicy::isFactualTitleClause).forEach(polarityClaims::add);
            for (String claim : polarityClaims) {
                List<String> orderedClaimTerms = claimTermSequence(claim);
                Set<String> claimTerms = new LinkedHashSet<>(orderedClaimTerms);
                if (claimTerms.isEmpty()) {
                    continue;
                }
                boolean claimNegated = isNegated(claim);
                boolean matchingPolaritySupported = false;
                for (String evidenceClause : evidenceClauses) {
                    Set<String> evidenceTerms = claimTerms(evidenceClause);
                    if (intersectionSize(claimTerms, evidenceTerms) < minimumCoverage(claimTerms.size())) {
                        continue;
                    }
                    // Deliberately require source terms in the claim's order; reordered paraphrases can be withheld.
                    if (!appearsInOrder(orderedClaimTerms, claimTermSequence(evidenceClause))) {
                        continue;
                    }
                    if (POSITIVE_PREVENTION_OF_NEGATED_RETRY_ACTION.matcher(evidenceClause).find()
                            && !positivePreventionTargetsClaim(evidenceClause, claim)) {
                        continue;
                    }
                    boolean evidenceNegated = isNegated(evidenceClause);
                    if (claimNegated == evidenceNegated) {
                        matchingPolaritySupported = true;
                    } else {
                        return false;
                    }
                }
                if (!matchingPolaritySupported) {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static Set<String> claimTerms(String value) {
        return new LinkedHashSet<>(claimTermSequence(value));
    }

    private static List<String> claimTermSequence(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String sentence : SENTENCE_BOUNDARY.split(value)) {
            boolean leadingRetryScopeActive = false;
            for (String clause : clauses(sentence)) {
                List<String> clauseTerms = distinctiveTermSequence(clause, true).stream()
                        .map(KnowledgeEvidencePolicy::normalizeEnglishInflection).toList();
                int predicateStart = firstPlausibleRetryPredicate(clause);
                boolean startsWithRetryScope = LEADING_RETRY_SCOPE_CLAUSE.matcher(clause).find();
                if (predicateStart >= 0) {
                    int predicateIndex = distinctiveTermSequence(clause.substring(0, predicateStart), true).size();
                    if (predicateIndex < clauseTerms.size()) {
                        clauseTerms = new ArrayList<>(clauseTerms);
                        String actionClause = leadingRetryScopeActive || startsWithRetryScope
                                ? clause + " conditionally"
                                : clause;
                        clauseTerms.set(predicateIndex, retryActionTerm(actionClause, predicateStart));
                    }
                    leadingRetryScopeActive = false;
                } else if (startsWithRetryScope) {
                    leadingRetryScopeActive = true;
                }
                terms.addAll(clauseTerms);
            }
        }
        return terms;
    }

    private static String retryActionTerm(String clause, int predicateStart) {
        Matcher modalMatcher = RETRY_MODAL_ACTION_TERM.matcher(clause);
        while (modalMatcher.find()) {
            if (modalMatcher.start("action") == predicateStart) {
                String modal = modalMatcher.group("modal");
                String modalAbility = modalMatcher.group("modalAbility");
                if (isNegated(clause)
                        && ((isDefiniteRetryInabilityModal(modal) && !hasNonAbsoluteRetryQualifierAroundAction(clause,
                                modalMatcher.start("modal"), modalMatcher.end("action")))
                                || (isDefiniteRetryInabilityModal(modalAbility)
                                        && !hasNonAbsoluteRetryQualifierAroundAction(clause,
                                                modalMatcher.start("modalAbility"), modalMatcher.end("action"))))) {
                    return scopeRetryActionTerm(RETRY_ACTION_TERM, clause);
                }
                if (modal != null) {
                    return scopeRetryActionTerm(RETRY_MODAL_ACTION_TERM_PREFIX + modal.toLowerCase(Locale.ROOT),
                            clause);
                }
                if (modalAbility != null) {
                    return scopeRetryActionTerm(
                            RETRY_MODAL_ACTION_TERM_PREFIX + modalAbility.toLowerCase(Locale.ROOT) + "_ability",
                            clause);
                }
                if (isNegated(clause)) {
                    return scopeRetryActionTerm(RETRY_ACTION_TERM, clause);
                }
                return scopeRetryActionTerm(RETRY_MODAL_ACTION_TERM_PREFIX + "can", clause);
            }
        }
        Matcher modalCapabilityMatcher = RETRY_MODAL_CAPABILITY_ACTION_TERM.matcher(clause);
        while (modalCapabilityMatcher.find()) {
            if (modalCapabilityMatcher.start("action") == predicateStart) {
                String modal = modalCapabilityMatcher.group("modal");
                String modalNegation = modalCapabilityMatcher.group("modalNegation");
                String inability = modalCapabilityMatcher.group("inability");
                return scopeRetryActionTerm(RETRY_MODAL_ACTION_TERM_PREFIX + modal.toLowerCase(Locale.ROOT)
                        + (modalNegation == null ? "" : "_not") + (inability == null ? "_capability" : "_inability"),
                        clause);
            }
        }
        Matcher qualifiedModalMatcher = RETRY_MODAL_QUALIFIED_ACTION_TERM.matcher(clause);
        while (qualifiedModalMatcher.find()) {
            if (qualifiedModalMatcher.start("action") == predicateStart) {
                String modal = qualifiedModalMatcher.group("modal");
                if (isNegated(clause) && isDefiniteRetryInabilityModal(modal)
                        && !hasNonAbsoluteRetryQualifierAroundAction(clause, qualifiedModalMatcher.start("modal"),
                                qualifiedModalMatcher.end("action"))) {
                    return scopeRetryActionTerm(RETRY_ACTION_TERM, clause);
                }
                return scopeRetryActionTerm(
                        RETRY_MODAL_ACTION_TERM_PREFIX + modal.toLowerCase(Locale.ROOT) + "_qualified", clause);
            }
        }
        Matcher capabilityMatcher = RETRY_CAPABILITY_ACTION_TERM.matcher(clause);
        while (capabilityMatcher.find()) {
            if (capabilityMatcher.start("action") == predicateStart) {
                if (isNegated(clause)) {
                    return RETRY_ACTION_SCOPE_QUALIFIER.matcher(clause).find()
                            ? RETRY_SCOPED_ACTION_TERM
                            : RETRY_ACTION_TERM;
                }
                String modal = capabilityMatcher.group("modal");
                if (modal == null) {
                    String inability = capabilityMatcher.group("inability");
                    return scopeRetryActionTerm(isNegated(clause) || inability != null
                            ? RETRY_ACTION_TERM
                            : RETRY_MODAL_ACTION_TERM_PREFIX + "can", clause);
                }
                String modalNegation = capabilityMatcher.group("modalNegation");
                String inability = capabilityMatcher.group("inability");
                return scopeRetryActionTerm(RETRY_MODAL_ACTION_TERM_PREFIX + modal.toLowerCase(Locale.ROOT)
                        + (modalNegation == null ? "" : "_not") + (inability == null ? "_capability" : "_inability"),
                        clause);
            }
        }
        Matcher intentInflectedMatcher = RETRY_INTENT_INFLECTED_ACTION_TERM.matcher(clause);
        while (intentInflectedMatcher.find()) {
            if (intentInflectedMatcher.start("action") == predicateStart) {
                return scopeRetryActionTerm(RETRY_INTENT_ACTION_TERM, clause);
            }
        }
        Matcher infinitiveMatcher = RETRY_INFINITIVE_ACTION_PATTERN.matcher(clause);
        while (infinitiveMatcher.find()) {
            if (infinitiveMatcher.start("action") == predicateStart) {
                if (isActionLevelRetryInfinitive(clause)) {
                    return RETRY_ACTION_SCOPE_QUALIFIER.matcher(clause).find()
                            ? RETRY_SCOPED_ACTION_TERM
                            : RETRY_ACTION_TERM;
                }
                return scopeRetryActionTerm(RETRY_INFINITIVE_ACTION_TERM, clause);
            }
        }
        return scopeRetryActionTerm(RETRY_ACTION_TERM, clause);
    }

    private static String scopeRetryActionTerm(String actionTerm, String clause) {
        return RETRY_ASSERTION_SCOPE_QUALIFIER.matcher(clause).find()
                ? RETRY_SCOPED_ACTION_TERM + actionTerm
                : actionTerm;
    }

    private static boolean isDefiniteRetryInabilityModal(String modal) {
        return modal != null && (modal.equalsIgnoreCase("cannot") || modal.equalsIgnoreCase("can't"));
    }

    private static boolean hasNonAbsoluteRetryQualifierAroundAction(String clause, int modalStart, int actionEnd) {
        return LEADING_RETRY_SCOPE_CLAUSE.matcher(clause.substring(0, modalStart)).find()
                || RETRY_PRE_MODAL_SCOPE_QUALIFIER.matcher(clause.substring(0, modalStart)).find()
                || RETRY_NON_ABSOLUTE_QUALIFIER.matcher(clause.substring(modalStart, actionEnd)).find()
                || RETRY_NON_ABSOLUTE_QUALIFIER.matcher(clause.substring(actionEnd)).find();
    }

    private static boolean isActionLevelRetryInfinitive(String clause) {
        return POSITIVE_DOUBLE_NEGATED_RETRY_ACTION.matcher(clause).find()
                || POSITIVE_PREVENTION_OF_NEGATED_RETRY_ACTION.matcher(clause).find()
                || RETRY_REFUSAL_NEGATION.matcher(clause).find() || RETRY_PREVENTION_NEGATION.matcher(clause).find()
                || UNABLE_TO_RETRY_NEGATION.matcher(clause).find() || LACK_RETRY_NEGATION.matcher(clause).find()
                || AVOID_RETRY_NEGATION.matcher(clause).find();
    }

    private static boolean appearsInOrder(List<String> claimTerms, List<String> evidenceTerms) {
        int matchedTerms = 0;
        for (String evidenceTerm : evidenceTerms) {
            if (evidenceTerm.equals(claimTerms.get(matchedTerms)) && ++matchedTerms == claimTerms.size()) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeEnglishInflection(String term) {
        if (term.equals("retries")) {
            return term;
        }
        if (term.endsWith("ies") && term.length() > 4) {
            return term.substring(0, term.length() - 3) + "y";
        }
        return term;
    }

    private static List<String> clauses(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(CLAIM_BOUNDARY.split(text)).map(String::trim).filter(clause -> !clause.isEmpty()).toList();
    }

    private static boolean isNegated(String clause) {
        String withoutPositiveDoubleNegations = POSITIVE_DOUBLE_NEGATED_RETRY_ACTION.matcher(clause).replaceAll(" ");
        withoutPositiveDoubleNegations = POSITIVE_PREVENTION_OF_NEGATED_RETRY_ACTION
                .matcher(withoutPositiveDoubleNegations).replaceAll(" ");
        String withoutNonNegatingIdioms = NOT_ONLY.matcher(withoutPositiveDoubleNegations).replaceAll(" ");
        withoutNonNegatingIdioms = CHINESE_NON_NEGATING_IDIOMS.matcher(withoutNonNegatingIdioms).replaceAll(" ");
        return ENGLISH_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || NO_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || NO_LONGER_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || NO_RETRY_SUPPORT_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || WITH_NO_RETRY_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || WITHOUT_RETRY_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || AVOID_RETRY_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || RETRY_PREVENTION_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || LACK_RETRY_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || RETRY_REFUSAL_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || UNABLE_TO_RETRY_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || CHINESE_NEGATION.matcher(withoutNonNegatingIdioms).find()
                || NEGATED_STATUS_ADJECTIVE.matcher(withoutNonNegatingIdioms).find();
    }

    private static boolean positivePreventionTargetsClaim(String evidenceClause, String claim) {
        Matcher targetMatcher = PASSIVE_PREVENTION_NEGATED_RETRY_TARGET.matcher(evidenceClause);
        if (!targetMatcher.find()) {
            targetMatcher = ACTIVE_PREVENTION_NEGATED_RETRY_TARGET.matcher(evidenceClause);
            if (!targetMatcher.find()) {
                return false;
            }
        }
        Set<String> targetTerms = claimTerms(targetMatcher.group("target"));
        if (targetTerms.isEmpty()) {
            return false;
        }
        int predicateStart = firstPlausibleRetryPredicate(claim);
        if (predicateStart < 0) {
            return false;
        }
        return claimSubjectMatchesPreventionTarget(targetTerms, claim, predicateStart);
    }

    private static int firstPlausibleRetryPredicate(String claim) {
        int firstPredicateStart = Integer.MAX_VALUE;
        Matcher actionMatcher = POSITIVE_NO_RETRY_LIMIT.matcher(claim);
        if (actionMatcher.find() && hasDistinctiveSubjectBefore(claim, actionMatcher.start("action"))) {
            firstPredicateStart = actionMatcher.start("action");
        }
        actionMatcher = RETRY_INFLECTED_ACTION_TERM.matcher(claim);
        while (actionMatcher.find()) {
            if (hasDistinctiveSubjectBefore(claim, actionMatcher.start())
                    && !RETRY_NOUN_COMPLEMENT.matcher(claim.substring(actionMatcher.end())).find()
                    && !(actionMatcher.group().equalsIgnoreCase("retries")
                            && RETRY_NOUN_PREFIX.matcher(claim.substring(0, actionMatcher.start())).find())) {
                firstPredicateStart = Math.min(firstPredicateStart, actionMatcher.start());
                break;
            }
        }
        actionMatcher = RETRY_PREFIXED_BASE_ACTION_TERM.matcher(claim);
        while (actionMatcher.find()) {
            if (hasDistinctiveSubjectBefore(claim, actionMatcher.start("action"))) {
                firstPredicateStart = Math.min(firstPredicateStart, actionMatcher.start("action"));
                break;
            }
        }
        actionMatcher = RETRY_BASE_ACTION_TERM.matcher(claim);
        while (actionMatcher.find()) {
            if (!RETRY_NOUN_COMPLEMENT.matcher(claim.substring(actionMatcher.end())).find()
                    && hasDistinctiveSubjectBefore(claim, actionMatcher.start())) {
                firstPredicateStart = Math.min(firstPredicateStart, actionMatcher.start());
                break;
            }
        }
        return firstPredicateStart == Integer.MAX_VALUE ? -1 : firstPredicateStart;
    }

    private static boolean hasDistinctiveSubjectBefore(String claim, int predicateStart) {
        return !distinctiveTermSequence(claim.substring(0, predicateStart), true).isEmpty();
    }

    private static boolean claimSubjectMatchesPreventionTarget(Set<String> targetTerms, String claim,
            int predicateStart) {
        int retryActionIndex = claimTermSequence(claim.substring(0, predicateStart)).size();
        if (retryActionIndex <= 0) {
            return false;
        }
        List<String> orderedClaimTerms = claimTermSequence(claim);
        Set<String> claimSubjectTerms = new LinkedHashSet<>(orderedClaimTerms.subList(0, retryActionIndex));
        return intersectionSize(targetTerms, claimSubjectTerms) >= minimumCoverage(targetTerms.size());
    }

    private static boolean isFactualTitleClause(String clause) {
        if (isNegated(clause)) {
            return true;
        }
        if (distinctiveChineseBigramCount(clause) >= 3) {
            return true;
        }

        // Titles are often noun phrases, so require a predicate marker after the opening subject phrase.
        List<String> words = new ArrayList<>();
        Matcher matcher = TERM_PATTERN.matcher(clause.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        int subjectIndex = !words.isEmpty() && TITLE_ARTICLES.contains(words.get(0)) ? 1 : 0;
        int minimumTerms = subjectIndex == 1 ? 2 : 3;
        if (words.size() <= subjectIndex + 1 || claimTerms(clause).size() < minimumTerms) {
            return false;
        }

        // The subject may be a multiword noun phrase, as in "The gateway retry is broken".
        for (int index = subjectIndex + 1; index < words.size(); index++) {
            String word = words.get(index);
            if (TITLE_FINITE_AUXILIARIES.contains(word) || TITLE_PREDICATE_ADJECTIVES.contains(word)) {
                return true;
            }
            boolean inflectedVerb = word.matches("(?iu).*(?:s|es|ies|ed|ing)$");
            if (inflectedVerb && index < words.size() - 1) {
                return true;
            }
        }
        return false;
    }

    private static long distinctiveChineseBigramCount(String value) {
        return claimTerms(value).stream()
                .filter(term -> term.codePoints()
                        .allMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN))
                .count();
    }

    static int minimumCoverage(int termCount) {
        return Math.max(1, (int) Math.ceil(termCount * MIN_QUERY_COVERAGE));
    }

    private static int intersectionSize(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(right::contains).count();
    }
}

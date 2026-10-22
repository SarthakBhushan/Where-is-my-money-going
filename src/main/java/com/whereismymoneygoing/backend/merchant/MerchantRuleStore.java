package com.whereismymoneygoing.backend.merchant;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.whereismymoneygoing.backend.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Reads built-in rules (classpath) and personal rules (local YAML file); writes only the local file. */
@Component
public class MerchantRuleStore {

    private final ObjectMapper yaml = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Path localFile;

    public MerchantRuleStore(AppProperties properties) {
        this.localFile = Path.of(properties.localRulesPath()).toAbsolutePath();
    }

    public List<MerchantRule> builtInRules() {
        try (InputStream in = new ClassPathResource("config/merchants.yml").getInputStream()) {
            return yaml.readValue(in, RuleFile.class).rules();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read built-in merchants.yml", e);
        }
    }

    public List<MerchantRule> localRules() {
        if (!Files.exists(localFile)) return List.of();
        try (InputStream in = Files.newInputStream(localFile)) {
            return yaml.readValue(in, RuleFile.class).rules();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + localFile, e);
        }
    }

    /** Built-in rules first, then local ones: the first match wins. */
    public List<MerchantRule> allRules() {
        List<MerchantRule> all = new ArrayList<>(builtInRules());
        all.addAll(localRules());
        return all;
    }

    public List<String> merchantNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allRules().forEach(r -> { if (r.merchant() != null && !r.merchant().isBlank()) names.add(r.merchant()); });
        return List.copyOf(names);
    }

    public synchronized void addLocalRule(String merchant, String keyword) {
        String m = merchant == null ? "" : merchant.strip();
        String k = keyword == null ? "" : keyword.strip().toUpperCase(Locale.ROOT);
        if (m.isEmpty()) throw new IllegalArgumentException("Merchant name cannot be empty.");
        if (k.isEmpty()) throw new IllegalArgumentException("Keyword cannot be empty.");

        List<MerchantRule> rules = new ArrayList<>(localRules());
        int index = -1;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).merchant().equalsIgnoreCase(m)) { index = i; break; }
        }
        if (index < 0) {
            rules.add(new MerchantRule(m, List.of(k)));
        } else {
            MerchantRule existing = rules.get(index);
            List<String> keywords = new ArrayList<>(existing.contains());
            if (!keywords.contains(k)) keywords.add(k);
            Collections.sort(keywords);
            rules.set(index, new MerchantRule(existing.merchant(), keywords));
        }
        rules.sort(Comparator.comparing(r -> r.merchant().toUpperCase(Locale.ROOT)));

        try {
            Files.createDirectories(localFile.getParent());
            yaml.writeValue(localFile.toFile(), new RuleFile(rules));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + localFile, e);
        }
    }
}

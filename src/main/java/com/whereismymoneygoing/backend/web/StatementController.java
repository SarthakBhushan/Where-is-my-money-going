package com.whereismymoneygoing.backend.web;

import com.whereismymoneygoing.backend.ai.AssistantService;
import com.whereismymoneygoing.backend.analytics.AnalyticsService;
import com.whereismymoneygoing.backend.enrichment.TransactionEnricher;
import com.whereismymoneygoing.backend.merchant.MerchantResolver;
import com.whereismymoneygoing.backend.merchant.MerchantRuleStore;
import com.whereismymoneygoing.backend.merchant.NarrationPreprocessor;
import com.whereismymoneygoing.backend.model.ParsedStatement;
import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.parser.IncorrectPasswordException;
import com.whereismymoneygoing.backend.parser.PasswordRequiredException;
import com.whereismymoneygoing.backend.parser.SampleStatementService;
import com.whereismymoneygoing.backend.parser.StatementParseException;
import com.whereismymoneygoing.backend.parser.StatementService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StatementController {

    public static final String SESSION_KEY = "statement";

    public record UnknownRow(String narration, int transactions,
                             java.math.BigDecimal totalSpend, String suggestedKeyword) {}

    private final StatementService statements;
    private final SampleStatementService samples;
    private final AnalyticsService analytics;
    private final MerchantRuleStore ruleStore;
    private final MerchantResolver resolver;
    private final TransactionEnricher enricher;
    private final AssistantService assistant;

    public StatementController(StatementService statements, SampleStatementService samples,
                               AnalyticsService analytics, MerchantRuleStore ruleStore,
                               MerchantResolver resolver, TransactionEnricher enricher,
                               AssistantService assistant) {
        this.statements = statements;
        this.samples = samples;
        this.analytics = analytics;
        this.ruleStore = ruleStore;
        this.resolver = resolver;
        this.enricher = enricher;
        this.assistant = assistant;
    }

    // ---------- landing / upload ----------

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("sampleBanks", SampleStatementService.BANKS);
        return "index";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "password", required = false) String password,
                         HttpSession session, Model model) {
        model.addAttribute("sampleBanks", SampleStatementService.BANKS);
        if (file.isEmpty()) {
            model.addAttribute("error", "Choose a statement file first.");
            return "index";
        }
        try {
            session.setAttribute(SESSION_KEY,
                    statements.parse(file.getOriginalFilename(), file.getBytes(), password));
            assistant.forget(session.getId());
            return "redirect:/dashboard";
        } catch (PasswordRequiredException e) {
            model.addAttribute("error", "🔒 This statement is password-protected. "
                    + "Enter its password below and choose the file again.");
        } catch (IncorrectPasswordException e) {
            model.addAttribute("error", "🔒 The password is incorrect. Please try again.");
        } catch (StatementParseException e) {
            model.addAttribute("error", e.getMessage());
        } catch (IOException e) {
            model.addAttribute("error", "Could not read the uploaded file.");
        }
        return "index";
    }

    @PostMapping("/sample")
    public String sample(@RequestParam("bank") String bank, HttpSession session, Model model) {
        try {
            session.setAttribute(SESSION_KEY, samples.load(bank));
            assistant.forget(session.getId());
            return "redirect:/dashboard";
        } catch (StatementParseException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("sampleBanks", SampleStatementService.BANKS);
            return "index";
        }
    }

    @PostMapping("/reset")
    public String reset(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        assistant.forget(session.getId());
        return "redirect:/";
    }

    // ---------- dashboard ----------

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "top", defaultValue = "10") int top,
                            HttpSession session, Model model) {
        ParsedStatement statement = current(session);
        if (statement == null) return "redirect:/";
        List<Transaction> txns = statement.transactions();

        model.addAttribute("statement", statement);
        model.addAttribute("from", txns.stream().map(Transaction::date).min(Comparator.naturalOrder()).orElse(LocalDate.now()));
        model.addAttribute("to", txns.stream().map(Transaction::date).max(Comparator.naturalOrder()).orElse(LocalDate.now()));
        model.addAttribute("summary", analytics.summary(txns));
        model.addAttribute("monthly", analytics.monthlyCashFlow(txns));
        model.addAttribute("balance", analytics.balanceTrajectory(txns));
        model.addAttribute("merchants", analytics.merchantSummary(txns, top));
        model.addAttribute("kinds", analytics.spendByKind(txns));
        model.addAttribute("subscriptions", analytics.recurringSubscriptions(txns));
        model.addAttribute("top", top);
        return "dashboard";
    }

    // ---------- transactions ----------

    @GetMapping("/transactions")
    public String transactions(@RequestParam(value = "q", defaultValue = "") String q,
                               @RequestParam(value = "type", defaultValue = "All") String type,
                               @RequestParam(value = "merchant", defaultValue = "All") String merchant,
                               HttpSession session, Model model) {
        ParsedStatement statement = current(session);
        if (statement == null) return "redirect:/";
        List<Transaction> all = statement.transactions();

        String needle = q.strip().toLowerCase(Locale.ROOT);
        List<Transaction> filtered = all.stream()
                .filter(t -> needle.isEmpty()
                        || t.narration().toLowerCase(Locale.ROOT).contains(needle)
                        || (t.merchant() != null && t.merchant().toLowerCase(Locale.ROOT).contains(needle)))
                .filter(t -> "All".equals(type) || t.type().name().equals(type))
                .filter(t -> "All".equals(merchant) || merchant.equals(t.merchant()))
                .toList();

        model.addAttribute("rows", filtered);
        model.addAttribute("total", all.size());
        model.addAttribute("merchantOptions", all.stream().map(Transaction::merchant)
                .filter(m -> m != null && !Transaction.UNKNOWN_MERCHANT.equals(m))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        model.addAttribute("merchant", merchant);
        return "transactions";
    }

    // ---------- developer ----------

    @GetMapping("/developer")
    public String developer(HttpSession session, Model model) {
        ParsedStatement statement = current(session);
        if (statement == null) return "redirect:/";
        List<Transaction> txns = statement.transactions();

        List<UnknownRow> unknown = analytics.unknownSpending(txns).stream().limit(20)
                .map(u -> new UnknownRow(u.narration(), u.transactions(), u.totalSpend(),
                        NarrationPreprocessor.extractKeyword(u.narration())))
                .toList();

        model.addAttribute("coverage", analytics.coverage(txns));
        model.addAttribute("unknown", unknown);
        model.addAttribute("merchantNames", ruleStore.merchantNames());
        return "developer";
    }

    @PostMapping("/developer/rules")
    public String addRule(@RequestParam("merchant") String merchant,
                          @RequestParam("keyword") String keyword,
                          HttpSession session, RedirectAttributes redirect) {
        ParsedStatement statement = current(session);
        if (statement == null) return "redirect:/";
        try {
            ruleStore.addLocalRule(merchant, keyword);
            resolver.reload();
            session.setAttribute(SESSION_KEY, new ParsedStatement(statement.bank(), statement.fileName(),
                    enricher.enrichAll(statement.transactions())));
            redirect.addFlashAttribute("flash", "Rule saved. Matching transactions were re-classified.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flash", "⚠️ " + e.getMessage());
        }
        return "redirect:/developer";
    }

    private static ParsedStatement current(HttpSession session) {
        return (ParsedStatement) session.getAttribute(SESSION_KEY);
    }
}
package com.builtbyjuls.clearing.trade;

import com.builtbyjuls.clearing.ledger.ClearedTrade;
import com.builtbyjuls.clearing.ledger.ClearedTradeRepository;
import com.builtbyjuls.clearing.messaging.TradeSubmissionMessage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clearing")
public class TradeClearingController {

    private final TradeClearingService tradeClearingService;
    private final ClearedTradeRepository clearedTradeRepository;

    public TradeClearingController(
            TradeClearingService tradeClearingService,
            ClearedTradeRepository clearedTradeRepository) {
        this.tradeClearingService = tradeClearingService;
        this.clearedTradeRepository = clearedTradeRepository;
    }

    @PostMapping("/trades")
    public ResponseEntity<ClearedTradePositionDto> novateTrade(@Valid @RequestBody TradeSubmissionMessage submission) {
        ClearedTradePositionDto position = tradeClearingService.novateAndClearTrade(submission);
        return ResponseEntity.ok(position);
    }

    @GetMapping("/members/{memberCode}/trades")
    public ResponseEntity<List<ClearedTrade>> getMemberTrades(@PathVariable("memberCode") String memberCode) {
        List<ClearedTrade> trades = clearedTradeRepository.findByMemberCodeOrderByClearedAtDesc(memberCode);
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/members/{memberCode}/portfolio-touch")
    public ResponseEntity<List<ClearedTrade>> touchMemberPortfolio(@PathVariable("memberCode") String memberCode) {
        List<ClearedTrade> trades = tradeClearingService.inspectMemberPortfolioWithSnapshotTouch(memberCode);
        return ResponseEntity.ok(trades);
    }
}

package com.cyberstock.instrument;

import com.cyberstock.common.Locales;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService service;

    public InstrumentController(InstrumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<InstrumentDto> list(
            @RequestParam(defaultValue = "false") boolean marquee,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String lang) {
        return service.list(Locales.resolve(lang), marquee);
    }

    @GetMapping("/search")
    public List<InstrumentDto> search(
            @RequestParam("q") String q,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String lang) {
        return service.search(q, Locales.resolve(lang));
    }

    @GetMapping("/{symbol}")
    public InstrumentDto get(
            @PathVariable String symbol,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String lang) {
        return service.toDto(service.require(symbol), Locales.resolve(lang));
    }
}

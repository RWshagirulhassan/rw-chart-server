package com.readywealth.trading.trade_engine.marketdata.api.http;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TicksUiController {
	@GetMapping("/ticks/ui")
	public String ticksUi() {
		return "redirect:/ui/index.html";
	}

	@GetMapping("/session/ui")
	public String sessionUi() {
		return "redirect:/ui/index.html";
	}
}

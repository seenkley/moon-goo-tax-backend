package valkyrie.moon.goo.tax.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.troja.eve.esi.ApiException;
import valkyrie.moon.goo.tax.corp.wallet.CorpWalletFetcher;

@RestController
@RequestMapping("/")
public class AuthController {

	@Autowired
	private Auth auth;

	@Autowired
	private CorpWalletFetcher walletFetcher;

	@RequestMapping("/")
	public String index() {
		String html = "Please click this link: <a href=\"" + auth.getAuthUrl() + "\">Eve Auth</a>";
		html += "<br> Click here for statistics: <a href=\"/statistics/\">klick me </a>";
		html += "<br> Click here for configuration: <a href=\"/configuration/\">klick me </a>";
		return html;
	}

	@RequestMapping(value = "/callback",
			params = {"code", "state"},
			method = RequestMethod.GET)
	public String callback(@RequestParam("code") String code, @RequestParam("state") String state) {

		try {
			auth.authenticate(state, code);
		} catch (ApiException e) {
			e.printStackTrace();
		}

		return "You can close the window now -- code: " +  code + " - state: " + state;
	}

	@RequestMapping("/fetch")
	public void fetchWalletData() {
		walletFetcher.fetchWalletData();
	}
}

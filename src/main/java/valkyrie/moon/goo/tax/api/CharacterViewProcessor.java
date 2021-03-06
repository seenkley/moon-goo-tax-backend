package valkyrie.moon.goo.tax.api;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import valkyrie.moon.goo.tax.character.Character;
import valkyrie.moon.goo.tax.character.CharacterRepository;
import valkyrie.moon.goo.tax.corp.wallet.TransactionLog;
import valkyrie.moon.goo.tax.corp.wallet.TransactionLogRepository;
import valkyrie.moon.goo.tax.marketData.dtos.MoonOre;

@Component
public class CharacterViewProcessor {

	private static final Logger LOG = LoggerFactory.getLogger(CharacterView.class);

	@Autowired
	private CharacterViewRepository characterViewRepository;
	@Autowired
	private CharacterRepository characterRepository;
	@Autowired
	private TransactionLogRepository transactionLogRepository;

	public void prepareCharacterView() {

		LOG.info("Preparing character views for frontend...");

		List<Character> allCharacters = characterRepository.findAll();
		List<CharacterView> characterViews = new ArrayList<>();

		for (Character character : allCharacters) {
			setCharacterViewData(characterViews, character);
		}
		characterViewRepository.saveAll(characterViews);
	}

	private void setCharacterViewData(List<CharacterView> characterViews, Character character) {
		CharacterView characterView = new CharacterView();
		characterView.setId(character.getId());
		characterView.setName(character.getName());
		characterView.setMinedOre(prepareMinedOre(character));
		characterView.setDebt(character.getDept().getToPay());
		characterView.setCorpName(character.getCorpName());
		// get transaction logs
		List<TransactionLog> transactionLogs = transactionLogRepository.findByCharacterName(character.getName());
		characterView.setTransactionLogs(transactionLogs);
		characterViews.add(characterView);
	}

	private List<MinedOre> prepareMinedOre(Character character) {
		List<MinedOre> minedOre = new ArrayList<>();
		if (character.getMinedMoonOre() != null) {
			for (MoonOre entry : character.getMinedMoonOre().values()) {
				minedOre.add(new MinedOre(entry.getVisualName(), entry.getMinedAmount(), entry.getDelta()));
			}
		}
		return minedOre;
	}
}

package valkyrie.moon.goo.tax.corp;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.troja.eve.esi.ApiException;
import net.troja.eve.esi.api.IndustryApi;
import net.troja.eve.esi.model.CorporationMiningObserverResponse;
import net.troja.eve.esi.model.CorporationMiningObserversResponse;
import valkyrie.moon.goo.tax.auth.EsiApi;
import valkyrie.moon.goo.tax.character.Character;
import valkyrie.moon.goo.tax.character.CharacterManagement;
import valkyrie.moon.goo.tax.config.PersistedConfigProperties;
import valkyrie.moon.goo.tax.config.PersistedConfigPropertiesRepository;
import valkyrie.moon.goo.tax.marketData.dtos.MoonOre;
import valkyrie.moon.goo.tax.marketData.dtos.MoonOreReprocessConstants;
import valkyrie.moon.goo.tax.marketData.dtos.RefinedMoonOre;
import valkyrie.moon.goo.tax.marketData.moonOre.MoonOreRepository;
import valkyrie.moon.goo.tax.marketData.refinedMoonOre.RefinedMoonOreRepository;

@Component
public class CorpMiningFetcher {

	private static final Logger LOG = LoggerFactory.getLogger(CorpMiningFetcher.class);

	@Autowired
	private EsiApi api;

	@Autowired
	private CharacterManagement characterManagement;

	@Autowired
	private PersistedConfigPropertiesRepository persistedConfigPropertiesRepository;

	@Autowired
	private MoonOreRepository moonOreRepository;
	@Autowired
	private RefinedMoonOreRepository refinedMoonOreRepository;
	@Autowired
	private UpdateTimeTrackerRepository updateTimeTrackerRepository;

	private final IndustryApi industryApi = new IndustryApi();

	public void fetchMiningStatistics() {

		// first initialize dates and config
		UpdateTimeTracker updateTimeTracker = updateTimeTrackerRepository.findAll().get(0);
		LocalDate today = LocalDate.of(2020, 10, 2);
		//		LocalDate today = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		//		LocalDate lastUpdate = LocalDate.of(2020, 10, 2);
		LocalDate lastUpdate = updateTimeTracker.getLastUpdate();
		//		if (!lastUpdate.isBefore(today)) {
		//			// nothing to update yet!
		//			LOG.info("last update: {} | current date: {} - nothing to do yet.", lastUpdate, today);
		//			return;
		//		}

		industryApi.setApiClient(api.getApi());
		Character leadChar = characterManagement.getLeadChar();
		if (leadChar == null) {
			LOG.warn("No char for fetching data found - please auth char first.");
			return;
		}
		List<RefinedMoonOre> refinedMoonOres = refinedMoonOreRepository.findAll();

		try {
			Map<Integer, Character> touchedChars = getMiningLog(leadChar.getCorpId(), refinedMoonOres, today);

			// get debt
			calculateDebt(refinedMoonOres, touchedChars);

		} catch (ApiException apiException) {
			apiException.printStackTrace();
		}

	}

	private Map<Integer, Character> getMiningLog(Integer corpId, List<RefinedMoonOre> refinedMoonOres, LocalDate today) throws ApiException {
		LOG.info("Getting mining log from ESI.");
		List<CorporationMiningObserversResponse> corporationCorporationIdMiningObservers = industryApi.getCorporationCorporationIdMiningObservers(corpId, EsiApi.DATASOURCE, null, null, null);
		Map<Integer, Character> touchedChars = new HashMap<>();

		LOG.info("Processing {} stations...", corporationCorporationIdMiningObservers.size());
		for (CorporationMiningObserversResponse corporationCorporationIdMiningObserver : corporationCorporationIdMiningObservers) {
			List<CorporationMiningObserverResponse> observerResponse = industryApi.getCorporationCorporationIdMiningObserversObserverId(corpId, corporationCorporationIdMiningObserver.getObserverId(), EsiApi.DATASOURCE, null, null, null);
			LOG.info("Processing {} mining log entries...", observerResponse.size());
			for (CorporationMiningObserverResponse miner : observerResponse) {
				LocalDate lastUpdated = miner.getLastUpdated();
				//				lastUpdated.plus();

				lastUpdated.isBefore(lastUpdated);

				Integer id = miner.getCharacterId();

				Character character = lookupCharacter(touchedChars, id);

				Map<Integer, MoonOre> minedMoonOre = character.getMinedMoonOre();
				Integer minedOreTypeId = miner.getTypeId();

				prepareMoonOre(minedMoonOre, minedOreTypeId, refinedMoonOres);

				long minedAmount = minedMoonOre.get(minedOreTypeId).getMinedAmount();// total mined for this type
				minedMoonOre.get(minedOreTypeId).setMinedAmount(minedAmount + miner.getQuantity());
				touchedChars.put(character.getId(), character);
			}
		}
		return touchedChars;
	}

	private Character lookupCharacter(Map<Integer, Character> touchedChars, Integer id) {
		Character character;
		if (!touchedChars.containsKey(id)) {
			character = characterManagement.findCharacter(id);
		} else {
			character = touchedChars.get(id);
		}
		return character;
	}

	private void calculateDebt(List<RefinedMoonOre> refinedMoonOres, Map<Integer, Character> touchedChars) {
		LOG.info("Calculating mining debt...");
		PersistedConfigProperties config = persistedConfigPropertiesRepository.findById(1).get();
		float refinementMultiplier = config.getRefinementMultiplier();
		float tax = config.getTax();

		// build refinedMoonOre datastructure:
		Map<String, Float> refinedMoonOreMap = new HashMap<>();

		refinedMoonOres.forEach(ore -> {
			refinedMoonOreMap.put(ore.name, ore.price);
		});

		for (Map.Entry<Integer, Character> touchedCharacter : touchedChars.entrySet()) {
			float currentDept = 0;
			Integer characterId = touchedCharacter.getKey();
			Character character = touchedCharacter.getValue();
			for (Map.Entry<Integer, MoonOre> minedOre : character.getMinedMoonOre().entrySet()) {
				Integer oreId = minedOre.getKey();
				MoonOre ore = minedOre.getValue();
				currentDept = calculatePrice(refinementMultiplier, tax, refinedMoonOreMap, currentDept, ore);
			}
			character.getDept().setToPay((long) (character.getDept().getToPay() + currentDept));
			character.getDept().setCharacterId(characterId);

			characterManagement.saveChar(character);
		}
	}

	private float calculatePrice(float refinementMultiplier, float tax, Map<String, Float> refinedMoonOreMap, float currentDept, MoonOre ore) {
		//calculate value of 100 pieces
		List<Pair<String, Integer>> pairs = MoonOreReprocessConstants.reprocessConstants.get(ore.name);
		for (Pair<String, Integer> pair : pairs) {
			float price = refinedMoonOreMap.get(pair.getLeft());
			float finalPrice = (float) (price * pair.getRight() * refinementMultiplier * ore.getMultiplier() * tax);
			currentDept += finalPrice * ((int) ore.getMinedAmount() / 100) ;
		}
		return currentDept;
	}

	private void prepareMoonOre(Map<Integer, MoonOre> minedMoonOre, Integer minedOreTypeId, List<RefinedMoonOre> refinedMoonOres) {
		Optional<MoonOre> minedOre = moonOreRepository.findById(String.valueOf(minedOreTypeId));
		if (!minedOre.isPresent()) {
			LOG.error("Did not find moon ore with id {}", minedOreTypeId);
		} else {
			MoonOre ore = minedOre.get();
			List<Pair<String, Integer>> refinedPairs = MoonOreReprocessConstants.reprocessConstants.get(ore.getName());
			if (minedMoonOre.containsKey(minedOreTypeId)) {
				MoonOre moonOre = minedMoonOre.get(minedOreTypeId);
				moonOre.setMinedAmount(moonOre.getMinedAmount() + ore.getMinedAmount());
			} else {
				minedMoonOre.putIfAbsent(minedOreTypeId, ore);
			}
		}
	}

	public Date convertToDateViaInstant(LocalDate dateToConvert) {
		return Date.from(dateToConvert.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
	}

}

package valkyrie.moon.goo.tax.marketData.refinedMoonOre;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import valkyrie.moon.goo.tax.marketData.EsiFetcher;
import valkyrie.moon.goo.tax.marketData.dtos.RefinedMoonOre;
import valkyrie.moon.goo.tax.marketData.dtos.TypeName;
import valkyrie.moon.goo.tax.marketData.dtos.UniverseGroups;

@Component
public class RefinedMoonOreFetcher extends EsiFetcher {

	Logger LOGGER = LoggerFactory.getLogger(RefinedMoonOreFetcher.class);
	private static final String GROUP_ID_URLS = "https://esi.evetech.net/latest/universe/groups/427/?datasource=tranquility&language=en-us";
	private static final String PRICE_URL = "https://market.fuzzwork.co.uk/aggregates/?region=%s&types=";
	private static final String JITA_REGION_ID = "30000142";
	private static final String PERIMETER_REGION_ID = "30000144";

	@Autowired
	private RefinedMoonOreRepository repository;

	public List<RefinedMoonOre> buildRefinedMoonOreDatabase() {
		LOGGER.info("Fetching refined moon ore ids...");
		try {

			UniverseGroups group = getUniverseGroups(new URL(GROUP_ID_URLS));
			ObjectMapper mapper = new ObjectMapper();
			List<TypeName> typeNames = fetchNames(mapper.writeValueAsString(group.getTypes()), new URL(NAME_URLS), TypeName.class);

			// fetch prices jita
			List<RefinedMoonOre> moonOrePricesJita = getRefinedMoonOres(group, typeNames, String.format(PRICE_URL, JITA_REGION_ID));

			// fetch prices perimeter
			List<RefinedMoonOre> moonOrePricesPerimeter = getRefinedMoonOres(group, typeNames, String.format(PRICE_URL, PERIMETER_REGION_ID));

			// compare those two and take the more expensive price
			List<RefinedMoonOre> finalPrices = calculateFinalPrices(moonOrePricesPerimeter, moonOrePricesJita);

			// save moon ore prices to mongodb
			LOGGER.info("Saving {} refined moon ore prices to DB", finalPrices.size());
			repository.saveAll(finalPrices);
			return finalPrices;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private List<RefinedMoonOre> calculateFinalPrices(List<RefinedMoonOre> moonOrePricesPerimeter, List<RefinedMoonOre> moonOrePricesJita) {
		List<RefinedMoonOre> finalPrices = new ArrayList<>();

		for (RefinedMoonOre refinedMoonOreJita : moonOrePricesJita) {
			for (RefinedMoonOre refinedMoonOrePreimeter : moonOrePricesPerimeter) {
				if (refinedMoonOreJita.getName().equals(refinedMoonOrePreimeter.getName())) {
					if (refinedMoonOreJita.getPrice() > refinedMoonOrePreimeter.getPrice()) {
						finalPrices.add(refinedMoonOreJita);
					} else {
						finalPrices.add(refinedMoonOrePreimeter);
					}
				}
			}
		}
		return finalPrices;
	}

	private List<RefinedMoonOre> getRefinedMoonOres(UniverseGroups group, List<TypeName> typeNames, String url) throws IOException {
		URL moonOreUrl = new URL(url + group.getTypes().stream().map(String::valueOf).collect(Collectors.joining(",")));
		return fetchPrices(typeNames, group.getTypes(), moonOreUrl);
	}

	private List<RefinedMoonOre> fetchPrices(List<TypeName> names, Set<Integer> types, URL moonOreUrl) throws IOException {
		URL url = moonOreUrl;
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));

		StringBuilder response = new StringBuilder();
		String responseLine = null;
		while ((responseLine = in.readLine()) != null) {
			response.append(responseLine.trim());
		}
		JsonObject object = new Gson().fromJson(response.toString(), JsonObject.class);

		List<RefinedMoonOre> refinedMoonOres = new ArrayList<>();

		parseTypes(names, types, object, refinedMoonOres);
		return refinedMoonOres;
	}

	private void parseTypes(List<TypeName> names, Set<Integer> types, JsonObject object, List<RefinedMoonOre> refinedMoonOres) {
		for (Integer id : types) {
			JsonObject jsonElement = (JsonObject) object.get(id.toString());
			JsonObject buy = (JsonObject) jsonElement.get("buy");
			float max = buy.get("max").getAsFloat();

			String finalName = "";
			for (TypeName name : names) {
				if (name.getId().equals(id.toString())) {
					finalName = name.getName();
				}
			}
			refinedMoonOres.add(new RefinedMoonOre(id.toString(), finalName, max, new Date()));
		}
	}

}

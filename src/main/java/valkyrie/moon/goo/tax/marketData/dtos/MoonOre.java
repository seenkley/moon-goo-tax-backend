package valkyrie.moon.goo.tax.marketData.dtos;

import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;

public class MoonOre {

	@Id
	public String id;

	public String name;
	public double multiplier;

	public long minedAmount;

	public Map<Integer, RefinedMoonOre> refinedMoonOres;

	public MoonOre() {
	}

	public MoonOre(String id, String name, double multiplier, long minedAmount, Map<Integer, RefinedMoonOre> refinedMoonOres) {
		this.id = id;
		this.name = name;
		this.multiplier = multiplier;
		this.minedAmount = minedAmount;
		this.refinedMoonOres = refinedMoonOres;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getMultiplier() {
		return multiplier;
	}

	public void setMultiplier(double multiplier) {
		this.multiplier = multiplier;
	}

	public long getMinedAmount() {
		return minedAmount;
	}

	public void setMinedAmount(long minedAmount) {
		this.minedAmount = minedAmount;
	}

	public Map<Integer, RefinedMoonOre> getRefinedMoonOres() {
		return refinedMoonOres;
	}

	public void setRefinedMoonOres(Map<Integer, RefinedMoonOre> refinedMoonOres) {
		this.refinedMoonOres = refinedMoonOres;
	}

	@Override
	public String toString() {
		return "MoonOre{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", multiplier=" + multiplier + ", minedAmount=" + minedAmount + ", refinedMoonOres=" + refinedMoonOres + '}';
	}
}

package valkyrie.moon.goo.tax.workers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import valkyrie.moon.goo.tax.DebtManager;
import valkyrie.moon.goo.tax.corp.CorpMiningFetcher;
import valkyrie.moon.goo.tax.corp.UpdateTimeTracker;
import valkyrie.moon.goo.tax.corp.UpdateTimeTrackerRepository;

@Component
public class DebtWorker {

	private Logger LOG = LoggerFactory.getLogger(DebtWorker.class);

	@Autowired
	private CorpMiningFetcher miningFetcher;
	@Autowired
	private UpdateTimeTrackerRepository updateTimeTrackerRepository;
	@Autowired
	private DebtManager debtManager;

	// cron job: everyday, every 4h
	//	@Scheduled(fixedRate = 3_600_000, initialDelay = 3_600_000)
	@Scheduled(cron = "0 30 1/4 * * *")
	public void fetchMoonLedgerData() {
		miningFetcher.fetchMiningStatistics();
	}

	// cron job: everyday at 00:30 UTC
	@Scheduled(cron = "0 15 0 * * *")
	public void resetUpdate() {
		persistShouldUpdate(true);
		miningFetcher.resetDelta();
	}

	// cron job: everyday Monday at 02:00 UTC
	@Scheduled(cron = "0 0 2 * * MON")
	public void increaseDebtIfNecessary() {
		LOG.info("Running debt increase worker...");
		debtManager.increaseDebtIfNecessary();
	}

	public void persistShouldUpdate(boolean shouldUpdate) {
		LOG.info("Resetting isUpdatedToday to {}", !shouldUpdate);
		Optional<UpdateTimeTracker> updateTimeTrackerOptional = updateTimeTrackerRepository.findById(1);
		UpdateTimeTracker updateTimeTracker;
		if (updateTimeTrackerOptional.isPresent()) {
			updateTimeTracker = updateTimeTrackerOptional.get();
			if (shouldUpdate) {
				updateTimeTracker.setUpdatedToday(false);
			} else {
				// don't update
				updateTimeTracker.setUpdatedToday(true);
			}
			updateTimeTrackerRepository.save(updateTimeTracker);
		} else {
			LOG.error("Could not reset update! No updatetime stored in db...");
		}
	}

	public void forceFetchMoonOreData() {
		miningFetcher.fetchMiningStatistics();
	}
}

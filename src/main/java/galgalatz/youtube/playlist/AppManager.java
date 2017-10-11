package galgalatz.youtube.playlist;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import galgalatz.youtube.playlist.galgalatz.GalgalatzUpdater;

public class AppManager {

	public static void main(String[] args) {

		ExecutorService executorService = Executors.newSingleThreadExecutor();
		executorService.execute(new GalgalatzUpdater());
		while (true) {
			try {
				Thread.currentThread().sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
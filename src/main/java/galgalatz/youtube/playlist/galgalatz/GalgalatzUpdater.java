package galgalatz.youtube.playlist.galgalatz;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.model.*;
import com.google.gson.Gson;

import galgalatz.youtube.playlist.Song;
import galgalatz.youtube.playlist.consts.Consts;
import galgalatz.youtube.playlist.google.GoogleServices;
import com.google.api.services.youtube.YouTube;

public class GalgalatzUpdater implements Runnable {

	private static final Logger LOG = LogManager.getLogger(GalgalatzUpdater.class);

	private static YouTube youtube;
	private static Boolean test = false;

	@Override
	public void run() {
		try {
			youtube = GoogleServices.getYouTubeService();
			while (true) {
				updatePlaylist();
				try {
					Thread.currentThread().sleep(Consts.THREAD_SLEEP_TIME);
				} catch (InterruptedException e) {
					LOG.error("Error while try sleep: ", e);
				}
			}
		} catch (Exception e) {
			LOG.error("Fail to create youtube service: ", e);
		}
	}

	private static void updatePlaylist() {
		try {
			PlaylistItemListResponse currentPlaylist = getCurrentPlaylist();
			LOG.debug("Current Galgaltz playlist:");
			LOG.debug("----------");
			for (PlaylistItem playlistItem : currentPlaylist.getItems()) {
				LOG.debug(playlistItem.getSnippet().getPosition() + 1 + ". " + playlistItem.getSnippet().getTitle()
						+ "(" + playlistItem.getSnippet().getResourceId().getVideoId() + ")");
			}
			LOG.debug("----------");
			GalgalatzResponse galgalatzResponse = getInfoFromGalgalatz();
			Song currentSong = new Song(galgalatzResponse.getTitle(), galgalatzResponse.getAutor());
			Song nextSong = new Song(galgalatzResponse.getTitleNext(), galgalatzResponse.getAutorNext());
			currentSong = cleanSongName(currentSong);
			nextSong = cleanSongName(nextSong);
			LOG.debug(String.format("Current song on galgalatz: %s", currentSong));
			LOG.debug(String.format("Next song on galgalatz: %s", nextSong));
			tryAddSongToPlaylist(currentPlaylist, currentSong);
			tryAddSongToPlaylist(currentPlaylist, nextSong);

		} catch (GoogleJsonResponseException e) {
			LOG.error("There was a service error: ", e);
			if (test) {
				String token;
				Boolean refresh;
				token = GoogleServices.credential.getRefreshToken();
				try {
					refresh = GoogleServices.credential.refreshToken();
				} catch (IOException e1) {
					LOG.error("REFRESH FAIL: ", e);
				}
				token = GoogleServices.credential.getRefreshToken();
				token = GoogleServices.credential.getAccessToken();
			}
		} catch (Exception e) {
			LOG.error("Fail to update playlist: ", e);
		}
	}

	private static void tryAddSongToPlaylist(PlaylistItemListResponse currentPlaylist, Song song) throws Exception {
		Integer searchIndex;

		//
		if (song != null) {
			if (StringUtils.trimToNull(song.getAutor()) != null && StringUtils.trimToNull(song.getTitle()) != null) {
				if (!isForbiddenSong(song)) {
					SearchListResponse searchListResponse = getSearchResult(song);
					searchIndex = 0;
					if (searchListResponse.getItems() != null && searchListResponse.getItems().size() > 0) {
						LOG.debug("Search result for song: ");
						LOG.debug("----------");
						for (SearchResult item : searchListResponse.getItems()) {
							LOG.debug((++searchIndex) + ". " + item.getSnippet().getTitle());
						}
						LOG.debug("----------");
						String videoId = searchListResponse.getItems().get(0).getId().getVideoId();
						LOG.debug(String.format("New song videoId : %s", videoId));
						if (!isSongAlreadyExists(currentPlaylist, videoId)) {
							if (currentPlaylist.getItems().size() >= Consts.PLAYLIST_LIMIT) {
								RemoveOldestSongFromPlaylist(currentPlaylist);
							}
							LOG.info(String.format("Adding new song, video song: %s , id: %s", song, videoId));
							InsertNewSong(videoId);
						} else {
							LOG.info(String.format("Song already in playlist, song: %s, video id: %s", song, videoId));
						}
					} else {
						LOG.info(String.format("fail find any result in Youtube for search : %s", song));
					}
				} else {
					LOG.info(String.format("Forbidden song: %s", song));
				}
			} else {
				LOG.info(String.format("Empty song: %s", song));
			}
		} else {
			LOG.info("Fail to retrive current song from galgaltz song == null");
		}

	}

	private static SearchListResponse getSearchResult(Song currentSong) throws IOException {
		YouTube.Search.List searchResult = youtube.search().list(Consts.SNIPPET);
		searchResult.setType("video");
		searchResult.setQ(currentSong.getAutor().concat(" ").concat(currentSong.getTitle()));
		SearchListResponse searchResponse = searchResult.execute();
		return searchResponse;
	}

	private static PlaylistItemListResponse getCurrentPlaylist() throws IOException {
		YouTube.PlaylistItems.List playlist = youtube.playlistItems().list(Consts.SNIPPET);
		playlist.setPlaylistId(Consts.PLAYLIST_ID);
		playlist.setMaxResults(Consts.MAX_SEARCH_RESULT);
		PlaylistItemListResponse currentPlaylistResponse = playlist.execute();
		return currentPlaylistResponse;
	}

	private static GalgalatzResponse getInfoFromGalgalatz() throws ClientProtocolException, IOException {

		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(Consts.GALGALATZ_URL);
		httpGet.setHeader("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
		httpGet.setHeader("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36");
		HttpClientContext context = HttpClientContext.create();
		CloseableHttpResponse response1 = httpclient.execute(httpGet, context);

		CookieStore cookieStore = context.getCookieStore();
		List<Cookie> cookies = cookieStore.getCookies();

		httpclient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
		response1 = httpclient.execute(httpGet, context);

		cookies = cookieStore.getCookies();

		httpclient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
		response1 = httpclient.execute(httpGet, context);

		cookies = cookieStore.getCookies();

		String theString = null;
		GalgalatzResponse galgalatzResponse = null;
		try {
			HttpEntity entity1 = response1.getEntity();
			theString = IOUtils.toString(entity1.getContent(), "UTF-8");
			EntityUtils.consume(entity1);
			Gson gson = new Gson();
			galgalatzResponse = gson.fromJson(theString, GalgalatzResponse.class);
		} finally {
			try {
				response1.close();
			} catch (Exception e) {
			}
		}
		return galgalatzResponse;
	}

	private static Song cleanSongName(Song song) {
		if (song != null) {
			song.setAutor(song.getAutor().trim());
			song.setTitle(song.getTitle().trim());
		}
		return song;
	}

	private static Boolean isSongAlreadyExists(PlaylistItemListResponse currentPlaylist, String videoId) {
		for (PlaylistItem playlistItem : currentPlaylist.getItems()) {
			if (playlistItem.getSnippet().getResourceId().getVideoId().equals(videoId))
				return true;
		}
		return false;
	}

	private static void RemoveOldestSongFromPlaylist(PlaylistItemListResponse currentPlaylist) throws IOException {
		String lastSongId = currentPlaylist.getItems().get(currentPlaylist.getItems().size() - 1).getId();
		YouTube.PlaylistItems.Delete deleteRequest = youtube.playlistItems().delete(lastSongId);
		deleteRequest.execute();
	}

	private static PlaylistItem createPlaylistItem(String videoId) {
		PlaylistItem playlistItem = new PlaylistItem();
		PlaylistItemSnippet snippet = new PlaylistItemSnippet();
		snippet.set("position", Consts.NEW_SONG_POSITION);
		ResourceId resourceId = new ResourceId();
		resourceId.set("kind", "youtube#video");
		resourceId.set("videoId", videoId);
		snippet.setResourceId(resourceId);
		playlistItem.setSnippet(snippet);
		snippet.set("playlistId", Consts.PLAYLIST_ID);
		return playlistItem;
	}

	private static void InsertNewSong(String videoId) throws IOException {
		PlaylistItem newItem = createPlaylistItem(videoId);
		YouTube.PlaylistItems.Insert playlistItemsInsertRequest = youtube.playlistItems().insert(Consts.SNIPPET,
				newItem);
		playlistItemsInsertRequest.execute();
	}

	public static Boolean isForbiddenSong(Song currentSong) {
		File forbiddenListFile = new File("forbidden.txt");
		FileReader reader = null;
		BufferedReader in = null;
		try {
			reader = new FileReader(forbiddenListFile);
			in = new BufferedReader(reader);
			String line = null;
			while ((line = in.readLine()) != null) {
				if (currentSong.getAutor().contains(line) || currentSong.getTitle().contains(line)) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			LOG.error("Fail to check forbidden songs: ", e);
			return false;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e2) {
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (Exception e3) {
				}
			}
		}
	}

}
package com.lumitier.ipaddresshider.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumitier.ipaddresshider.IpAddressHider;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Verbindung zu Twitch: anmelden, und regelmaessig fragen, ob der Kanal live ist.
 *
 * WAS DIESE KLASSE DARF UND WAS NICHT:
 * Sie fragt Twitch genau zwei Dinge - wem gehoert dieser Token, und ist
 * dieser Kanal gerade live. Es werden KEINE Berechtigungen angefordert
 * (leere Scope-Liste): kein Chat, kein Kanal, keine E-Mail. Der Token
 * kann nichts ausser sich selbst ausweisen.
 *
 * WIE ANGEMELDET WIRD: Device Code Flow. Die Mod bekommt einen kurzen Code,
 * der Nutzer gibt ihn auf twitch.tv/activate ein. Kein Passwort laeuft
 * durch Minecraft, keine Weiterleitung, kein lokaler Webserver. Die App ist
 * bei Twitch als "oeffentlich" registriert - es gibt kein Client-Secret,
 * das jemand aus dem Jar ziehen koennte.
 *
 * WIE DER STREAMER-MODUS GESCHALTET WIRD: gar nicht direkt. Diese Klasse
 * meldet nur "live" oder "nicht live", und IpAddressHider rechnet das zum
 * selbst gesetzten Schalter dazu. Dadurch wird nie etwas in die Config
 * geschrieben, und ein von Hand eingeschalteter Streamer-Modus bleibt an,
 * wenn der Stream endet.
 *
 * FEHLER SIND NIE FATAL: Netz weg, Twitch down, Token abgelaufen - es wird
 * geloggt, der letzte bekannte Zustand bleibt stehen, und nichts davon
 * erreicht den Render-Thread. Alle Anfragen laufen auf einem eigenen
 * Hintergrundfaden.
 */
public final class TwitchLink {

	/**
	 * Client-ID der bei Twitch registrierten App "IP Address Hider".
	 * Kein Geheimnis - oeffentliche Apps haben kein Secret, die ID steht in
	 * jeder Anfrage im Klartext.
	 */
	private static final String CLIENT_ID = "kyrk7edpld6qh1g3w2ehhb2vls1ebe";

	/** Leer: keine Berechtigungen. Siehe Klassenkommentar. */
	private static final String SCOPES = "";

	/** Wie oft gefragt wird, ob der Kanal live ist. */
	private static final long LIVE_CHECK_SECONDS = 60;

	private static final String ID = "https://id.twitch.tv/oauth2/";
	private static final String HELIX = "https://api.twitch.tv/helix/";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum State {
		/** Nicht verbunden. */
		DISCONNECTED,
		/** Code wird gerade bei Twitch angefragt. */
		REQUESTING,
		/** Code steht da, wartet auf Bestaetigung auf twitch.tv/activate. */
		WAITING_FOR_USER,
		/** Verbunden. */
		CONNECTED,
		/** Etwas ist schiefgegangen - Details im Log, Zustand neu verbinden. */
		FAILED
	}

	/** Gespeichert in einer EIGENEN Datei, nicht in der normalen Config. */
	private static final class Stored {
		String accessToken;
		String refreshToken;
		String userId;
		String login;
	}

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private static ScheduledExecutorService worker;

	private static volatile State state = State.DISCONNECTED;
	private static volatile boolean live;
	private static volatile String userCode;
	private static volatile String login;
	/** Zaehlt jede sichtbare Aenderung hoch - der Bildschirm baut dann neu. */
	private static volatile int version;

	private static Stored stored;
	private static ScheduledFuture<?> liveTask;
	private static ScheduledFuture<?> pollTask;

	private TwitchLink() {}

	// ------------------------------------------------------------------
	// Abfragen fuer Oberflaeche und Streamer-Modus
	// ------------------------------------------------------------------

	public static State state() { return state; }
	public static boolean live() { return live; }
	public static String userCode() { return userCode; }
	public static String login() { return login; }
	public static int version() { return version; }

	private static void changed() {
		version++;
	}

	// ------------------------------------------------------------------
	// Start, Verbinden, Trennen
	// ------------------------------------------------------------------

	/** Beim Spielstart: gespeicherten Token laden und Live-Abfrage starten. */
	public static synchronized void start() {
		if (worker != null) {
			return;
		}
		worker = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "IP Address Hider - Twitch");
			// Daemon: Haelt das Spiel beim Beenden nicht auf.
			t.setDaemon(true);
			return t;
		});
		stored = readStored();
		if (stored != null && stored.accessToken != null) {
			login = stored.login;
			state = State.CONNECTED;
			changed();
			startLiveChecks();
		}
	}

	/** Device Code anfordern - der Nutzer sieht danach den Code. */
	public static synchronized void connect() {
		if (state == State.REQUESTING || state == State.WAITING_FOR_USER) {
			return;
		}
		state = State.REQUESTING;
		userCode = null;
		changed();
		worker.execute(() -> {
			try {
				JsonObject r = post(ID + "device", Map.of(
					"client_id", CLIENT_ID,
					"scopes", SCOPES));
				String deviceCode = r.get("device_code").getAsString();
				userCode = r.get("user_code").getAsString();
				int interval = Math.max(5, r.get("interval").getAsInt());
				long expiresAt = System.currentTimeMillis()
					+ r.get("expires_in").getAsLong() * 1000L;
				state = State.WAITING_FOR_USER;
				changed();
				pollForToken(deviceCode, interval, expiresAt);
			} catch (Exception e) {
				fail("Could not request a device code from Twitch.", e);
			}
		});
	}

	/** Verbindung loesen: Token bei Twitch widerrufen und lokal loeschen. */
	public static synchronized void disconnect() {
		stopLiveChecks();
		if (pollTask != null) {
			pollTask.cancel(false);
			pollTask = null;
		}
		Stored old = stored;
		stored = null;
		live = false;
		login = null;
		userCode = null;
		state = State.DISCONNECTED;
		writeStored(null);
		changed();
		if (old != null && old.accessToken != null) {
			// Widerruf im Hintergrund. Schlaegt er fehl, ist der Token
			// trotzdem lokal weg - und laeuft bei Twitch von selbst ab.
			worker.execute(() -> {
				try {
					post(ID + "revoke", Map.of("client_id", CLIENT_ID,
						"token", old.accessToken));
				} catch (Exception e) {
					IpAddressHider.LOGGER.warn("[IP Address Hider] Could not revoke the Twitch token.", e);
				}
			});
		}
	}

	// ------------------------------------------------------------------
	// Anmeldung abschliessen
	// ------------------------------------------------------------------

	private static void pollForToken(String deviceCode, int interval, long expiresAt) {
		pollTask = worker.schedule(() -> {
			if (state != State.WAITING_FOR_USER) {
				return;
			}
			if (System.currentTimeMillis() > expiresAt) {
				fail("The Twitch code expired before it was confirmed.", null);
				return;
			}
			try {
				HttpResponse<String> res = postRaw(ID + "token", Map.of(
					"client_id", CLIENT_ID,
					"scopes", SCOPES,
					"device_code", deviceCode,
					"grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
				if (res.statusCode() == 200) {
					JsonObject tok = JsonParser.parseString(res.body()).getAsJsonObject();
					Stored s = new Stored();
					s.accessToken = tok.get("access_token").getAsString();
					s.refreshToken = tok.has("refresh_token")
						? tok.get("refresh_token").getAsString() : null;
					identify(s);
					stored = s;
					writeStored(s);
					login = s.login;
					userCode = null;
					state = State.CONNECTED;
					changed();
					startLiveChecks();
					return;
				}
				// "authorization_pending" heisst: Nutzer hat noch nicht
				// bestaetigt. Alles andere ist ein echter Fehler.
				if (!res.body().contains("authorization_pending")
						&& !res.body().contains("slow_down")) {
					fail("Twitch rejected the code: " + res.body(), null);
					return;
				}
			} catch (Exception e) {
				IpAddressHider.LOGGER.warn("[IP Address Hider] Twitch token poll failed, retrying.", e);
			}
			pollForToken(deviceCode, interval, expiresAt);
		}, interval, TimeUnit.SECONDS);
	}

	/** Wem gehoert der Token? Braucht keine Berechtigung. */
	private static void identify(Stored s) throws Exception {
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(ID + "validate"))
				.header("Authorization", "OAuth " + s.accessToken)
				.timeout(Duration.ofSeconds(10))
				.GET().build(),
			HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) {
			throw new IllegalStateException("validate returned " + res.statusCode());
		}
		JsonObject v = JsonParser.parseString(res.body()).getAsJsonObject();
		s.userId = v.get("user_id").getAsString();
		s.login = v.get("login").getAsString();
	}

	// ------------------------------------------------------------------
	// Live-Abfrage
	// ------------------------------------------------------------------

	private static synchronized void startLiveChecks() {
		stopLiveChecks();
		// Sofort einmal, dann im Takt. Bis die erste Antwort da ist, gilt
		// "nicht live" - beim Spielstart sind das ein, zwei Sekunden.
		liveTask = worker.scheduleWithFixedDelay(TwitchLink::checkLive,
			0, LIVE_CHECK_SECONDS, TimeUnit.SECONDS);
	}

	private static synchronized void stopLiveChecks() {
		if (liveTask != null) {
			liveTask.cancel(false);
			liveTask = null;
		}
	}

	private static void checkLive() {
		Stored s = stored;
		if (s == null || s.accessToken == null) {
			return;
		}
		try {
			HttpResponse<String> res = getStreams(s);
			if (res.statusCode() == 401 && refresh(s)) {
				res = getStreams(s);
			}
			if (res.statusCode() == 401) {
				// Token tot und nicht erneuerbar. Live-Zustand stehen
				// lassen waere falsch - aber der Nutzer muss neu verbinden.
				fail("Twitch login expired - please connect again.", null);
				stopLiveChecks();
				return;
			}
			if (res.statusCode() != 200) {
				IpAddressHider.LOGGER.warn("[IP Address Hider] Twitch live check returned "
					+ res.statusCode() + ", keeping last state.");
				return;
			}
			JsonArray data = JsonParser.parseString(res.body()).getAsJsonObject()
				.getAsJsonArray("data");
			boolean now = data != null && !data.isEmpty();
			if (now != live) {
				live = now;
				changed();
				IpAddressHider.LOGGER.info("[IP Address Hider] Twitch channel is "
					+ (now ? "live - Streamer Mode on." : "offline."));
			}
		} catch (Exception e) {
			// Netzfehler: letzten Zustand behalten. Lieber zu lange
			// verdeckt als ploetzlich offen.
			IpAddressHider.LOGGER.warn("[IP Address Hider] Twitch live check failed, keeping last state.", e);
		}
	}

	private static HttpResponse<String> getStreams(Stored s) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(URI.create(HELIX + "streams?user_id="
					+ URLEncoder.encode(s.userId, StandardCharsets.UTF_8)))
				.header("Client-Id", CLIENT_ID)
				.header("Authorization", "Bearer " + s.accessToken)
				.timeout(Duration.ofSeconds(10))
				.GET().build(),
			HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * Abgelaufenen Token erneuern. Oeffentliche Apps duerfen das ohne
	 * Secret; der alte Refresh-Token ist danach verbraucht und wird ersetzt.
	 */
	private static boolean refresh(Stored s) {
		if (s.refreshToken == null) {
			return false;
		}
		try {
			HttpResponse<String> res = postRaw(ID + "token", Map.of(
				"client_id", CLIENT_ID,
				"grant_type", "refresh_token",
				"refresh_token", s.refreshToken));
			if (res.statusCode() != 200) {
				IpAddressHider.LOGGER.warn("[IP Address Hider] Twitch token refresh failed: " + res.body());
				return false;
			}
			JsonObject tok = JsonParser.parseString(res.body()).getAsJsonObject();
			s.accessToken = tok.get("access_token").getAsString();
			if (tok.has("refresh_token")) {
				s.refreshToken = tok.get("refresh_token").getAsString();
			}
			writeStored(s);
			return true;
		} catch (Exception e) {
			IpAddressHider.LOGGER.warn("[IP Address Hider] Twitch token refresh failed.", e);
			return false;
		}
	}

	// ------------------------------------------------------------------
	// Hilfen
	// ------------------------------------------------------------------

	private static void fail(String message, Exception e) {
		if (e == null) {
			IpAddressHider.LOGGER.warn("[IP Address Hider] " + message);
		} else {
			IpAddressHider.LOGGER.warn("[IP Address Hider] " + message, e);
		}
		userCode = null;
		state = State.FAILED;
		changed();
	}

	private static JsonObject post(String url, Map<String, String> form) throws Exception {
		HttpResponse<String> res = postRaw(url, form);
		if (res.statusCode() != 200) {
			throw new IllegalStateException(url + " returned " + res.statusCode() + ": " + res.body());
		}
		String body = res.body();
		return body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
	}

	private static HttpResponse<String> postRaw(String url, Map<String, String> form) throws Exception {
		StringJoiner j = new StringJoiner("&");
		form.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8)
			+ "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
		return HTTP.send(HttpRequest.newBuilder(URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(j.toString())).build(),
			HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * Eigene Datei fuer den Token, getrennt von ipaddresshider.json.
	 *
	 * Die normale Config darf man herumschicken, wenn jemand Hilfe will.
	 * Diese hier nicht - und getrennt ist das ohne Nachdenken richtig.
	 */
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir()
			.resolve(IpAddressHider.MOD_ID + "-twitch.json");
	}

	private static Stored readStored() {
		Path p = file();
		if (!Files.exists(p)) {
			return null;
		}
		try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
			return GSON.fromJson(r, Stored.class);
		} catch (Exception e) {
			IpAddressHider.LOGGER.warn("[IP Address Hider] Could not read the Twitch login, connect again.", e);
			return null;
		}
	}

	private static void writeStored(Stored s) {
		Path p = file();
		try {
			if (s == null) {
				Files.deleteIfExists(p);
				return;
			}
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(s, w);
			}
		} catch (Exception e) {
			IpAddressHider.LOGGER.warn("[IP Address Hider] Could not save the Twitch login.", e);
		}
	}
}

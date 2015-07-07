import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder

import org.serviio.library.metadata.*
import org.serviio.library.online.*
import groovy.json.*
import groovy.util.XmlSlurper

class Goodgame extends WebResourceUrlExtractor {
	final Integer VERSION = 1

	final String VALID_CHANNEL_URL = "^http://(?:[^\\.]*.)?goodgame\\.ru/channel/([a-zA-Z0-9_]+).*\$"
	final String HLS_URL_FORMAT = "http://hls.goodgame.ru/hls/%s%s.m3u8"
	final String FIND_CHANNEL_FORMAT = 'iframe frameborder=\\"0\\" width=\\"100%\\" height=\\"100%\\" src=\\"http://goodgame.ru/player(\\d)?\\?(\\d+)'
	final String INFO_URL = 'http://goodgame.ru/api/getchannelstatus?id=%s'

	final boolean DEBUG = false

	int getVersion() {
		return VERSION
	}

	String getExtractorName() {
		return 'goodgame.ru'
	}

	boolean extractorMatches(URL feedUrl) {
		return (feedUrl ==~ VALID_CHANNEL_URL)
	}

	WebResourceContainer extractItems(URL resourceUrl, int maxItemsToRetrieve) {
		def items, title
		def channelName = (String) (resourceUrl =~ VALID_CHANNEL_URL)[0][1]

		title = "${channelName} Stream"
		items = extractHlsStream(resourceUrl, channelName)

		def container = new WebResourceContainer()
		container.setTitle(title)
		container.setItems(items)

		return container
	}

	List<WebResourceItem> extractHlsStream(URL resourceUrl, String channelName) {
		def res = getUrlContent(resourceUrl)
		def channelId = (String) (res =~ FIND_CHANNEL_FORMAT)[0][2]

		def infoReqLink = String.format(INFO_URL, channelName)
		def infoText = (new URL(infoReqLink)).text
		def root = new XmlSlurper().parseText(infoText)

		def status = root.stream[0].status

		def streamLink = String.format(HLS_URL_FORMAT, channelId, '')
		def thumbnail = ''

		def isLive = (status == 'Live')

		if (isLive) {
			thumbnail = root.stream[0].thumb.text()
		} else {
			throw new RuntimeException('Offline')
		}

		if (DEBUG) {
			log('\n\n\n')
			log('channelId = ' + channelId)
			log('thumbnail = ' + thumbnail)
			log('isLive = ' + isLive)
			log('status = ' + status)
			log('\n\n\n')
		}

		def qualities = ['1080p': '', '720p': '_720', '480p': '_480', '240p': '_240']
		def items = []
		qualities.each { key, value ->
			items += new WebResourceItem(title: channelName + ' ' + key, additionalInfo: [
				url: String.format(HLS_URL_FORMAT, channelId, value),
				live: isLive,
				cacheKey: channelName + '-hls' + value,
				thumbnailUrl: thumbnail,
				expiresImmediately: true
			])
		}
		return items
	}

	String getUrlContent (URL url) {
		URL obj = url
		def conn = (HttpURLConnection) obj.openConnection()
		conn.setReadTimeout(5000)
		conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8")
		conn.addRequestProperty("User-Agent", "Mozilla")

		def redirect = false;

		// normally, 3xx is redirect
		int status = conn.getResponseCode()
		if (status != HttpURLConnection.HTTP_OK) {
			if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
				redirect = true
			}
		}

		if (redirect) {
			// get redirect url from "location" header field
			def newUrl = conn.getHeaderField("Location")

			// get the cookie if need, for login
			def cookies = conn.getHeaderField("Set-Cookie")

			// open the new connnection again
			conn = new URL(newUrl).openConnection()
			conn.setRequestProperty("Cookie", cookies)
			conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8")
			conn.addRequestProperty("User-Agent", "Mozilla")
		}

		def inReader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
		def inputLine
		def html = new StringBuffer()

		while ((inputLine = inReader.readLine()) != null) {
			html.append(inputLine)
		}
		inReader.close()

		return html.toString()
	}

	ContentURLContainer extractUrl (WebResourceItem item, PreferredQuality requestedQuality) {
		def c = new ContentURLContainer()
		if (item != null) {
			c.setExpiresImmediately(item.additionalInfo.expiresImmediately)
			c.setContentUrl(item.additionalInfo.url)
			c.setLive(item.additionalInfo.live)
			c.setThumbnailUrl(item.additionalInfo.thumbnailUrl)
			c.setCacheKey(item.additionalInfo.cacheKey)
		}
		return c
	}
}

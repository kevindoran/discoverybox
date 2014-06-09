package nz.co.kevindoran.vimeodownloader;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ImmediateRefreshHandler;
import com.gargoylesoftware.htmlunit.IncorrectnessListener;
import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Hello world!
 *
 */
public class VimeoDownloader {

    private final URL RSS_URL;
    private final int MAX_ATTEMPTS = 10;
    private final int PAGE_LIMIT = 25;
    private final String VIMEO_URL = "http://vimeo.com";
    private WebClient webClient;
    private int lastDownloadeableVideoIndex = 0;

    public VimeoDownloader() {
        try {
            RSS_URL = new URL("http://vimeo.com/channels/staffpicks/videos/rss");
        } catch (MalformedURLException ex) {
            throw new RuntimeException(
                    "The static URL should never be malformed.", ex);
        }
        createWebClient();
    }

    private void createWebClient() {
        webClient = new WebClient(BrowserVersion.FIREFOX_17);

        // For speed
        //webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setUseInsecureSSL(true);
        webClient.setIncorrectnessListener(new IncorrectnessListener() {
            @Override
            public void notify(String arg0, Object arg1) {
            }
        });
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(
                Level.OFF);
        webClient.setJavaScriptTimeout(3600);
        webClient.getOptions().setTimeout(9000);
        webClient.getOptions().setPopupBlockerEnabled(false);
        webClient.setRefreshHandler(new ImmediateRefreshHandler());
        // Ignore some file types.
        webClient.setWebConnection(new WebConnectionWrapper(webClient) {
            @Override
            public WebResponse getResponse(WebRequest request) throws
                    IOException {
                String fileName = request.getUrl().getFile();
                if (!fileName.endsWith(".css") && !fileName.endsWith(".jpg")
                        && !fileName.endsWith(".gif") && !fileName.endsWith(
                                ".jsp") && !fileName.endsWith(".js")
                        && !fileName.endsWith(".png") && !fileName.endsWith(
                                ".png?ssl=1")) {
                    return super.getResponse(request);
                } else {
                    return new StringWebResponse("", request.getUrl());
                }
            }
        });

        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
    }

    public static void main(String[] args) throws IOException, FeedException {
        int count = 1;
        int startFrom = 0;
        if(args.length >= 1) {
            count = Integer.parseInt(args[0]);
        }
        if(args.length == 2) {
            startFrom = Integer.parseInt(args[1]);
        }
        
        VimeoDownloader downloader = new VimeoDownloader();
        List<String> downloadUrls = downloader.getNextDownloadableVideoUrl(startFrom, count);
        for(String s : downloadUrls) {
            System.out.println(s);
        }
    }

    public String retrieveVideoDownloadUrl(String videoPageUrl) throws
            IOException {
        //System.err.println("Trying: " + videoPageUrl);
        HtmlPage page = webClient.getPage(videoPageUrl);

        // Get and click download button (this causes a div to appear with download
        // options. 
        HtmlElement download = page.getFirstByXPath("id('tools')/a[4]");
        if (download == null || !download.getAttribute("title").equals(
                "Download")) {
            // Download is not supported.
            return null;
        }
        page = download.click();
        webClient.waitForBackgroundJavaScript(5000);

        HtmlElement download2 = page.getFirstByXPath("id('download')");
        download2 = page.getFirstByXPath("id('download')/div/div/ul/li[3]/a");
        if(download2 == null) {
            return null;
        }
        String link = download2.getAttribute("href");
        return link;
    }
    
    public List<String> getNextDownloadableVideoUrl(int startingFrom, int count) throws IOException, FeedException {
        List<String> urls = new ArrayList<>();
        int index = startingFrom;
        for(int i=0; i<count; i++) {
            String url = getNextDownloadableVideoUrl(index);
            urls.add(url);
            // Not the cleanest way to communicate.
            index = lastDownloadeableVideoIndex;
        }
        return urls;
    }

    public String getNextDownloadableVideoUrl(int startingFrom) throws
            IOException, FeedException {
        String videoUrl = null;
        int maxIndex = (MAX_ATTEMPTS + startingFrom);
        int index = startingFrom;
        while(index < maxIndex && index < PAGE_LIMIT-1 && videoUrl == null) {
            String videoPage = getStaffPickUrl(index);
            videoUrl = retrieveVideoDownloadUrl(videoPage);
            index++;
        }
        lastDownloadeableVideoIndex = index;
        return videoUrl;
    }

    public String getStaffPickUrl(int numSinceMostRecent) throws IOException,
                                                                 FeedException {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed;
        feed = input.build(new XmlReader(RSS_URL));
        List<SyndEntry> entries = feed.getEntries();
        String topUrl = entries.get(numSinceMostRecent).getLink();
        int videoNumberPart = topUrl.lastIndexOf("/");
        String videoNumber = topUrl.substring(videoNumberPart, topUrl.length());
        topUrl = VIMEO_URL + videoNumber;
        return topUrl;
    }
}

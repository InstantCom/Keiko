package net.instantcom.keikosniffer.scraper;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class TorrentKingScraper {

    private static final Log log = LogFactory.getLog(TorrentKingScraper.class);

    public TorrentKingScraper() {
    }

    @SuppressWarnings("unchecked")
    public List<String> scrape() {
        List<String> list = new ArrayList<String>();
        WebClient webClient = createWebClient();
        int trackerCount = 0;
        try {
            int pageNum = 1;
            for (;; pageNum++) {
                String url = "http://www.torrentking.org/?page=" + pageNum;
                log.info("scraping " + url);
                HtmlPage page = (HtmlPage) webClient.getPage(url);
                if (null == page) {
                    break;
                }
                List<HtmlAnchor> links = (List<HtmlAnchor>) page.getByXPath("//td[@class='lc1']/a");
                if (null == links || links.isEmpty()) {
                    log.warn("can't find tracker links");
                    break;
                } else {
                    for (HtmlAnchor link : links) {
                        ++trackerCount;
                        String tracker = link.asText().trim().toLowerCase();
                        list.add(tracker);
                        if (!tracker.startsWith("www.")) {
                            list.add("www." + tracker);
                        }
                        if (!tracker.startsWith("tracker.")) {
                            list.add("tracker." + tracker);
                        }
                    }
                }
            }
        } catch (FailingHttpStatusCodeException ignored) {
        } catch (Exception e) {
            log.warn("scrape", e);
        } finally {
            webClient.closeAllWindows();
        }
        log.info(trackerCount + " trackers scraped");
        return list;
    }

    private WebClient createWebClient() {
        WebClient webClient = new WebClient();
        webClient.setCssEnabled(false);
        webClient.setJavaScriptEnabled(false);
        webClient.setThrowExceptionOnScriptError(false);
        webClient.setThrowExceptionOnFailingStatusCode(true);
        return webClient;
    }

}

package com.painlessshopping.mohamed.findit;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.painlessshopping.mohamed.findit.model.Item;
import com.painlessshopping.mohamed.findit.model.SearchQuery;
import com.painlessshopping.mohamed.findit.viewmodel.SearchQueueHandler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Fetches search results from the Canada Computers website.
 *
 * Created by Mohamed on 2016-11-28.
 */

public class CanadaComputersSearch extends SearchQuery {

    public Elements resultsEven, finalDoc;
    private ArrayList<Item> processed;
    private final Handler uiHandler = new Handler();
    private int status = 0;
    protected class JSHtmlInterface {
        @android.webkit.JavascriptInterface
        public void showHTML(String html) {
            final String htmlContent = html;
            uiHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            Document doc = Jsoup.parse(htmlContent);
                        }
                    }
            );
        }
    }

    /**
     * Constructor method
     * @param context The context taken from the webview (So that the asynctask can show progress)
     * @param query Provides the search term
     */
    public CanadaComputersSearch(Context context, String query) {
        final Context c = context;

        try {
            final WebView browser = new WebView(c);
            browser.setVisibility(View.INVISIBLE);
            browser.setLayerType(View.LAYER_TYPE_NONE, null);
            browser.getSettings().setJavaScriptEnabled(true);
            browser.getSettings().setBlockNetworkImage(true);
            browser.getSettings().setDomStorageEnabled(true);
            browser.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            browser.getSettings().setLoadsImagesAutomatically(false);
            browser.getSettings().setGeolocationEnabled(false);
            browser.getSettings().setSupportZoom(false);
            browser.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36");
            browser.addJavascriptInterface(new JSHtmlInterface(), "JSBridge");

            browser.setWebViewClient(
                    new WebViewClient() {

                        @Override
                        public void onPageStarted(WebView view, String url, Bitmap favicon) {
                            super.onPageStarted(view, url, favicon);
                        }

                        @Override
                        public void onPageFinished(WebView view, String url) {
                            browser.loadUrl("javascript:window.JSBridge.showHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                        }
                    }
            );
                //Loads website with WebView to fetch results
                browser.loadUrl("http://www.canadacomputers.com/simple_search.php?keywords=" + query);
                browser.loadUrl(browser.getUrl());
                final String link = browser.getUrl();

                //Processes pages of results
                new fetcher(c).execute(link);
                new fetcher(c).execute(link + "&page=2");
                new fetcher(c).execute(link + "&page=3");
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * This subclass is a worker thread meaning it does work in the background while the user interface is doing something else
     * This is done to prevent "lag".
     * To call this class you must write fetcher(Context c).execute(The link you want to connect to)
     */
    class fetcher extends AsyncTask<String, Void, Elements> {
        Context mContext;
        ProgressDialog pdialog;

        public fetcher(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pdialog = new ProgressDialog(mContext);
            pdialog.setTitle(R.string.finding_results);
            pdialog.setCancelable(false);
            pdialog.show();
        }

        //This return elements because the postExecute() method needs an Elements object to parse its results
        @Override
        protected Elements doInBackground(String... strings) {

            //You can pass in multiple strings, so this line just says to use the first string
            String link = strings[0];

            //For Debug Purposes, Do NOT Remove - **Important**
            System.out.println("Connecting to: " + link);

            try {
                doc = Jsoup.connect(link)
                        .ignoreContentType(true)
                        .userAgent("Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36")
                        .timeout(10000)
                        .get();
                //Defines which element of the website to observe
                finalDoc = doc.select("body tbody");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return finalDoc;
        }


        @Override
        protected void onPostExecute(Elements result) {
            processed = crunchResults(parse(result));
            System.out.println("Done Crunching CanadaComputers");
            TechSearch.adapter.addAll(processed);
            System.out.println("Adapter Notified by CanadaComputers");
            pdialog.dismiss();

            //Adds Canada Computers to the Tech Search
            TechSearch.adapter.notifyDataSetChanged();
            SearchQueueHandler.makeRequest(mContext, processed, SearchQueueHandler.TECH_SEARCH);
        }
    }

    /**
     * This class stores the relevant results retrieved from the Asynctask in one Elements object for manipulation
     * @param r The elements retrieved from the Asynctask "fetcher"
     */
    public Elements parse(Elements r){
        if(r != null){
            results = r.select(" tr.productListing-odd");
            resultsEven = r.select(" tr.productListing-even");

            //Add the "even" product listings to results array
            for(int j = 0; j <resultsEven.size();j++){
                results.add(resultsEven.get(j));
            }
            System.out.println(results.size() + " Results have been returned from CanadaComputers.");
//        fetchPrice(results);
//        fetchDescription(results);
            return results;
        } else {
            return null;
        }
    }

    public ArrayList<Item> crunchResults(Elements e){
        ArrayList<Item> results = new ArrayList<Item>();
        try {
            for (int i = 0; i < e.size(); i++) {

                Element ele = e.get(i).select("td").get(1);
                String description = ele.select("div.item_description > a").first().text();
                Elements ids = ele.select(" " + "div.partnum");
                String unflink = ids.get(1).attr("id");
                String link = "http://m.canadacomputers.com/mobile/itemid/" + unflink;
                Element prices = e.get(i).select("td").get(2);

                //For some reason I have to substring this twice seperately because it doesn't work otherwise
                String pricestring = prices.toString().substring(prices.toString().indexOf("$") + 1);
                int endIndex = 0;

                while (Character.isDigit(pricestring.charAt(endIndex)) || pricestring.charAt(endIndex) == '.') {
                    endIndex++;
                }

                pricestring = pricestring.substring(0, endIndex);
                System.out.println(pricestring);
                //Parses the double as an actual price
                price = Double.parseDouble(pricestring);
                String store = "Canada Computers";
                results.add(new Item(description, store, price, link));
                System.out.println(results.get(i).toString());
            }
        } catch (Exception a){
            a.printStackTrace();
        }
        return results;
    }

    public int getStatus(){
        return status;
    }
 }

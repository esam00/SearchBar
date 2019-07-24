package com.android.essam.searchbar;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.android.essam.searchbar.Retrofit.ISuggestAPI;
import com.android.essam.searchbar.Retrofit.RetrofitClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mancj.materialsearchbar.MaterialSearchBar;
import com.mancj.materialsearchbar.adapter.SuggestionsAdapter;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class HomeActivity extends AppCompatActivity {

    //views
    private MaterialSearchBar materialSearchBar;
    private WebView webView;
    private CardView cardView;

    //vars
    private ISuggestAPI myAPI;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private List<String> suggestions = new ArrayList<>();

    //constants
    private static String GOOGLE_SEARCH_BASE_URL = "https://www.google.com/search?q=";
    private static String REGEX_FULL_URL = "https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
    private static String REGEX_SHORT_URL = "[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        //init
        materialSearchBar = findViewById(R.id.search_bar);
        webView = findViewById(R.id.web_view);
        cardView = findViewById(R.id.get_started_cardView);
        myAPI = RetrofitClient.getInstance().create(ISuggestAPI.class);

        customizeWebView();

        //called when search button clicked
        materialSearchBar.setOnSearchActionListener(new MaterialSearchBar.OnSearchActionListener() {
            @Override
            public void onSearchStateChanged(boolean enabled) {

            }

            @Override
            public void onSearchConfirmed(CharSequence text) {
                startSearch(text);
            }

            @Override
            public void onButtonClicked(int buttonCode) {
                // if the clicked button is back button , disable searcBar and clear suggestions
                if (buttonCode == MaterialSearchBar.BUTTON_BACK) {
                    materialSearchBar.disableSearch();
                    materialSearchBar.clearSuggestions();
                }

            }
        });

        //when typing a text in searchBar
        materialSearchBar.addTextChangeListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cardView.setVisibility(View.INVISIBLE);
                //fetch suggestions from google API and display it on every char entered
                getSuggestion(s.toString(),"chrome");
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        //when suggestion clicked
        materialSearchBar.setSuggestionsClickListener(new SuggestionsAdapter.OnItemViewClickListener() {
            @Override
            public void OnItemClickListener(int position, View v) {
                String suggestion = suggestions.get(position);
                startSearch(suggestion);
            }

            @Override
            public void OnItemDeleteListener(int position, View v) {

            }
        });
    }

    /**
     * customize webView settings
     */
    private void customizeWebView() {
        //enable javaScript
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.setNetworkAvailable(true);

        //set new webClient to display pages in this app ,
        //otherwise system will ask to display it using other web browsers
        webView.setWebViewClient(new WebViewClient());
    }

    /**
     * This method is triggered to start search process,
     * and use webView.loadUrl(url) to load result and display it in our app
     * @param text which has been typed in searchBar , we should parse this string because it could be :
     *             single or multiple words ex : (facebook)
     *             shorten url ex :( facebook.com)
     *             complete url ex : (https://www.facebook.com)
     *  So we should handle each case from above
     */
    private void startSearch(CharSequence text) {
        //no need to display suggestion while searching is in progress
        materialSearchBar.clearSuggestions();
        //hide input keyboard
        hideSoftInput();

        String inputText = text.toString();
        String url = GOOGLE_SEARCH_BASE_URL+inputText;

        //if input text contains "." see what type of url is it
        if(inputText.contains(".")) {
            //if user typed a short url like : facebook.com or mytedata.net,
            // add the (https://)part to open the website not google search
            if (IsMatch(inputText, REGEX_SHORT_URL)) {
                url = "https://" + inputText;
            }
            // if text is a complete url open website
            if (IsMatch(inputText, REGEX_FULL_URL)) {
                url = inputText;
            }
        }

        webView.loadUrl(url);
    }

    /**
     * Start fetching autocomplete suggestions from Google Api using Retrofit and Rxjava
     * @param query the text that user want to search for
     * @param client chrome or firefox
     */
    private void getSuggestion(String query, String client) {
        compositeDisposable.add(
                myAPI.getSuggestFromGoogle(query,client)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        if (suggestions.size()>0)suggestions.clear();

                        //here we receive suggestions as JsonArray
                        JSONArray mainJson = new JSONArray(s);

                        //use Gson library to convert it to our java list
                        suggestions = new Gson().fromJson(mainJson.getString(1),
                                new TypeToken<List<String>>(){}.getType());
                        //update suggestions
                        materialSearchBar.updateLastSuggestions(suggestions);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Toast.makeText(HomeActivity.this, ""+throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
        );

    }

    /**
     * this method is generally used to match a given text with a specified pattern and returns boolean
     * So we can use it to check if this is a valid url by using Regular Expressions or (regex)
     * @param s text we want to check or validate it ex : a url (https://www.google.com )
     * @param pattern Regex
     * @return true they are matching
     */
    private static boolean IsMatch(String s, String pattern) {
        try {
            Pattern patt = Pattern.compile(pattern);
            Matcher matcher = patt.matcher(s);
            return matcher.matches();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void hideSoftInput(){
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(materialSearchBar.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    @Override
    protected void onDestroy() {
        compositeDisposable.clear();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

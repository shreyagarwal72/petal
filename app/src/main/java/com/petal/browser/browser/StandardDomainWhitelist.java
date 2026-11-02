package com.petal.browser.browser;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import com.petal.browser.database.HistoryBookmarkContract;
import com.petal.browser.database.HistoryBookmarkRepository;
import com.petal.browser.unit.PetalAndroidUtils;

public class StandardDomainWhitelist {

    private static final List<String> listStandard = new ArrayList<>();
    private final Context context;

    public StandardDomainWhitelist(Context context) {
        this.context = context;
        loadDomains(context);
    }

    private synchronized static void loadDomains(Context context) {
        HistoryBookmarkRepository action = new HistoryBookmarkRepository(context);
        action.open(false);
        listStandard.clear();
        listStandard.addAll(action.listDomains(HistoryBookmarkContract.TABLE_STANDARD));
        action.close();
    }

    public boolean isWhite(String url) {
        for (String domain : listStandard) {
            if (url != null && PetalAndroidUtils.domain(url).equals(domain)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void addDomain(String domain) {
        HistoryBookmarkRepository action = new HistoryBookmarkRepository(context);
        action.open(true);
        action.addDomain(domain, HistoryBookmarkContract.TABLE_STANDARD);
        action.close();
        listStandard.add(domain);
    }

    public synchronized void removeDomain(String domain) {
        HistoryBookmarkRepository action = new HistoryBookmarkRepository(context);
        action.open(true);
        action.deleteDomain(domain, HistoryBookmarkContract.TABLE_STANDARD);
        action.close();
        listStandard.remove(domain);
    }

    public synchronized void clearDomains() {
        HistoryBookmarkRepository action = new HistoryBookmarkRepository(context);
        action.open(true);
        action.clearTable(HistoryBookmarkContract.TABLE_STANDARD);
        action.close();
        listStandard.clear();
    }
}

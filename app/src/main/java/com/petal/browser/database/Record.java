package com.petal.browser.database;

import com.petal.browser.unit.HelperUnit;

public class Record {

    private long iconColor;
    private String title;
    private String url;
    private long time;

    public Record() {
        this.title = null;
        this.url = null;
        this.time = 0L;
        this.iconColor = 0L;
    }

    public Record(String title, String url, long time, long iconColor) {
        this.title = title;
        this.url = url;
        this.time = time;
        this.iconColor = iconColor;
    }

    public long getIconColor() {
        return iconColor;
    }

    public void setIconColor(long iconColor) {
        this.iconColor = iconColor;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getURL() {
        return url;
    }

    public String getDomain() {
        return HelperUnit.domain(url);
    }

    public void setURL(String url) {
        this.url = url;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}

package com.petal.browser.objects;

public class CustomRedirect {
    public final String source;
    public final String target;

    public CustomRedirect(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public String getSource() {
        return source;
    }
    public String getTarget() {
        return target;
    }
}

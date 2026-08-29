package com.petal.browser.browser;

import android.util.Base64;
import android.webkit.MimeTypeMap;

public class DataURIParser {

    private final String filename;
    private final byte[] imagedata;

    public DataURIParser(String url) {
        //Log.d("DataURIParse", url);
        String data = url.substring(url.indexOf(",") + 1);
        //Log.d("DataURIParse", data);
        String mimeType = url.substring(url.indexOf(":") + 1, url.indexOf(";"));
        //Log.d("DataURIParse", mimeType);
        String fileType = url.substring(url.indexOf(":") + 1, url.indexOf("/"));
        //Log.d("DataURIParse", fileType);
        String suffix = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        //Log.d("DataURIParse", suffix);
        filename = fileType + "." + suffix;
        //Log.d("DataURIParse", filename);
        imagedata = Base64.decode(data, Base64.DEFAULT);
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getImagedata() {
        return imagedata;
    }
}

package com.petal.browser.view;

public class MenuItem {
    private final String title;
    private final int iconResId; // Die Ressourcen-ID (z. B. android.R.drawable.ic_...)
    private boolean isSelected;

    public MenuItem(String title, int iconResId, boolean isSelected) {
        this.title = title;
        this.iconResId = iconResId;
        this.isSelected = isSelected;
    }

    public String getTitle() { return title; }
    public int getIconResId() { return iconResId; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}
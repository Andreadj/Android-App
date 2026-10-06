package com.mobiled.android.model;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.List;

@Keep
public class ExpandableListItem {
    public String title;
    public List<Object> childItems = new ArrayList<>();
    public boolean isExpanded = false;

    public ExpandableListItem(String title) {
        this.title = title;
        childItems = new ArrayList<>();
    }
}

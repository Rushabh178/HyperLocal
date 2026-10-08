package com.HyperLocalSystem.app.catalog.repository;

/** A grocery category of the virtual warehouse and how many distinct products it has nearby. */
public interface GroceryCategoryRow {

    String getCategory();

    long getItemCount();
}

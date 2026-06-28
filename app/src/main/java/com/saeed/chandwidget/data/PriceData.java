package com.saeed.chandwidget.data;

public class PriceData {
    public final String key;
    public final double price;
    public final double change;
    public final boolean isPositive;

    public PriceData(String key, double price, double change) {
        this.key = key;
        this.price = price;
        this.change = change;
        this.isPositive = change >= 0;
    }
}

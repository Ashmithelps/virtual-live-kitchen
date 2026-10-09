package com.virtualkitchen.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** A line on an order. Name, price and veg flag are copied from the menu at order time. */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "menu_item_id", updatable = false)
    private UUID menuItemId;

    @Column(name = "item_name", nullable = false, updatable = false, length = 120)
    private String itemName;

    @Column(name = "unit_price_paise", nullable = false, updatable = false)
    private long unitPricePaise;

    @Column(name = "is_veg", nullable = false, updatable = false)
    private boolean veg;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total_paise", nullable = false, updatable = false)
    private long lineTotalPaise;

    @Column(length = 200, updatable = false)
    private String notes;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    protected OrderItem() {
        // for JPA
    }

    OrderItem(Order order, int lineNumber, UUID menuItemId, String itemName, long unitPricePaise, boolean veg,
              int quantity, String notes) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.lineNumber = lineNumber;
        this.menuItemId = menuItemId;
        this.itemName = itemName;
        this.unitPricePaise = unitPricePaise;
        this.veg = veg;
        this.quantity = quantity;
        this.lineTotalPaise = Math.multiplyExact(unitPricePaise, quantity);
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public UUID getMenuItemId() { return menuItemId; }
    public String getItemName() { return itemName; }
    public long getUnitPricePaise() { return unitPricePaise; }
    public boolean isVeg() { return veg; }
    public int getQuantity() { return quantity; }
    public long getLineTotalPaise() { return lineTotalPaise; }
    public String getNotes() { return notes; }
    public int getLineNumber() { return lineNumber; }
}

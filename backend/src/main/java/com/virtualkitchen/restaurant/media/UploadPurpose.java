package com.virtualkitchen.restaurant.media;

/** What an uploaded image is for; decides the folder its key lives in. */
public enum UploadPurpose {
    MENU_ITEM_IMAGE("menu-items"),
    CHEF_PHOTO("chefs"),
    /** Shown on the restaurant page and burned into kitchen clips. */
    RESTAURANT_LOGO("logos"),
    /** FSSAI licence scan or PDF; only the owner and admins can open it. */
    FSSAI_LICENCE("licences", true);

    private final String folder;
    private final boolean allowsPdf;

    UploadPurpose(String folder) {
        this(folder, false);
    }

    UploadPurpose(String folder, boolean allowsPdf) {
        this.folder = folder;
        this.allowsPdf = allowsPdf;
    }

    public boolean allowsPdf() {
        return allowsPdf;
    }

    public String folder() {
        return folder;
    }
}

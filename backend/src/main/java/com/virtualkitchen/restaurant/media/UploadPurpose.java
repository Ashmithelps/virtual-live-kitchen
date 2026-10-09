package com.virtualkitchen.restaurant.media;

/** What an uploaded image is for; decides the folder its key lives in. */
public enum UploadPurpose {
    MENU_ITEM_IMAGE("menu-items"),
    CHEF_PHOTO("chefs");

    private final String folder;

    UploadPurpose(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}

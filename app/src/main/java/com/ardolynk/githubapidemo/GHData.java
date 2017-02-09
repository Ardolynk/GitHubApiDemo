package com.ardolynk.githubapidemo;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;

import lombok.Data;

/**
 * Created by Michael Tikhonenko on 2/7/17.
 */

@Data
public class GHData implements Serializable {
    private ArrayList<Item> items;

    @Data
    public class Item {
        @SerializedName("name")
        private String projectName;
        @SerializedName("stargazers_count")
        private int starCount;
        private Owner owner;
        @SerializedName("html_url") private String projectURL;

        @Data
        public class Owner {
            private String login;
            @SerializedName("avatar_url")
            private String avatarLink;
        }
    }
}

package com.chuckerteam.chucker

import com.google.gson.Gson
import com.google.gson.GsonBuilder

public object GsonInstance {

    private val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .setPrettyPrinting()
            .create()
    }

    public fun get(): Gson? = gson
}
